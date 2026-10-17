/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.totp.TOTPData;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.event.events.AuthenticAuthenticatedEvent;
import xyz.kyngs.librelogin.common.redis.RedisPubSubHandler;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.title.Title;

/**
 * Default implementation of {@link AuthorizationProvider} responsible for tracking player
 * authorization state, pending two-factor authentication challenges, email verification tokens,
 * password reset tokens, and periodic authentication reminder broadcasts.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticAuthorizationProvider<P, S> extends AuthenticHandler<P, S>
        implements AuthorizationProvider<P> {

    // The first prompt waits up to 10 s (40 x 250 ms) for the player to reach a server.
    private static final int INITIAL_PROMPT_ATTEMPTS = 40;
    private static final long INITIAL_PROMPT_RETRY_MILLIS = 250;

    private final Map<UUID, Boolean> unAuthorized;
    private final Set<UUID> authorized;
    // Players whose authorize() is in progress: the atomic gate that replaces the JVM-wide
    // interned-string monitor (which other code could share and which was held across DB I/O).
    private final Set<UUID> authorizing;
    private final Map<UUID, String> awaiting2FA;
    private final Cache<UUID, EmailVerifyData> emailConfirmCache;
    private final Cache<UUID, String> passwordResetCache;

    /**
     * Constructs a new authorization provider and initializes recurring tasks for action bar and
     * title/message reminders.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public AuthenticAuthorizationProvider(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
        unAuthorized = new ConcurrentHashMap<>();
        authorized = ConcurrentHashMap.newKeySet();
        authorizing = ConcurrentHashMap.newKeySet();
        awaiting2FA = new ConcurrentHashMap<>();

        var millis =
                plugin.getConfiguration()
                        .get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);

        if (millis > 0) {
            plugin.repeat(this::notifyUnauthorized, 0, millis);
        }

        plugin.repeat(this::broadcastActionbars, 0, 1000);

        emailConfirmCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
        passwordResetCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
    }

    /**
     * Returns the cache containing pending email verification tokens.
     *
     * @return the email confirmation cache
     */
    public Cache<UUID, EmailVerifyData> getEmailConfirmCache() {
        return emailConfirmCache;
    }

    /**
     * Returns the cache containing pending password reset tokens.
     *
     * @return the password reset cache
     */
    public Cache<UUID, String> getPasswordResetCache() {
        return passwordResetCache;
    }

    /**
     * Marks the player UUID as authorized (used for floodgate, premium auto-login, or session
     * resume).
     *
     * @param uuid the player unique identifier
     */
    public void markAuthorized(UUID uuid) {
        if (uuid != null) {
            unAuthorized.remove(uuid);
            authorized.add(uuid);
            awaiting2FA.remove(uuid);
            plugin.cancelTasksForPlayer(uuid);
            kickOtherProxySessions(uuid);
        }
    }

    /**
     * Kicks sessions of the same account on other proxies. Called only after this connection has
     * proven its identity, so an unauthenticated connection can never kick an authenticated one.
     */
    private void kickOtherProxySessions(UUID uuid) {
        if (!plugin.multiProxyEnabled() || plugin.getRedisService() == null) {
            return;
        }
        var pubSub = plugin.getRedisService().getPubSubHandler();
        if (pubSub != null) {
            pubSub.publishRemoteKick(
                    uuid, RedisPubSubHandler.MESSAGE_KEY_PREFIX + "kick.logged-in-elsewhere");
        }
    }

    /**
     * Cleans up all pending authorization tracking and caches when a player disconnects.
     *
     * @param player the player who exited
     */
    public void onExit(P player) {
        if (player == null) return;
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (uuid == null) return;
        P current = plugin.getPlayerForUUID(uuid);
        if (current != null && current != player) {
            // Player has already reconnected with a newer session! Abort stale disconnect cleanup.
            return;
        }
        stopTracking(uuid);
        awaiting2FA.remove(uuid);
        authorized.remove(uuid);
        // Retain emailConfirmCache and passwordResetCache across disconnects until their natural
        // 10-minute TTL
    }

    @Override
    public boolean isAuthorized(P player) {
        if (player == null) return false;
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (uuid == null) return false;
        if (plugin.fromFloodgate(uuid)) return true;
        return authorized.contains(uuid) && !unAuthorized.containsKey(uuid);
    }

    @Override
    public boolean isAwaiting2FA(P player) {
        if (player == null) return false;
        var uuid = platformHandle.getUUIDForPlayer(player);
        return uuid != null && awaiting2FA.containsKey(uuid);
    }

    @Override
    public void authorize(User user, P player, AuthenticatedEvent.AuthenticationReason reason) {
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (uuid == null) return;
        if (!authorizing.add(uuid)) {
            return; // another thread is already authorizing this player
        }
        try {
            if (isAuthorized(player)) {
                return;
            }

            // Persist first: if the write fails the player is not authorized, so a password
            // that was never stored cannot leave an authorized-but-unregistered account.
            user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
            user.setIp(plugin.anonymizeIP(platformHandle.getIP(player)));
            plugin.getDatabaseProvider().updateUser(user);

            stopTracking(uuid);
            authorized.add(uuid);
            awaiting2FA.remove(uuid);
            plugin.cancelTasksForPlayer(uuid);
            kickOtherProxySessions(uuid);

            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience != null) {
                audience.clearTitle();
                audience.sendActionBar(Component.empty());
            }
            plugin.getEventProvider()
                    .fire(
                            plugin.getEventTypes().authenticated,
                            new AuthenticAuthenticatedEvent<>(user, player, plugin, reason));
            plugin.authorize(player, user, audience);

            if (plugin.getPlayerForUUID(uuid) != player) {
                // Disconnected during /login: drop the authorized state added above.
                onExit(player);
                plugin.onExit(player);
            }
        } finally {
            authorizing.remove(uuid);
        }
    }

    @Override
    public boolean confirmTwoFactorAuth(P player, Integer code, User user) {
        var uuid = platformHandle.getUUIDForPlayer(player);
        var secret = uuid == null ? null : awaiting2FA.get(uuid);
        if (secret != null && plugin.getTOTPProvider().verify(code, secret)) {
            user.setSecret(secret);
            plugin.getDatabaseProvider().updateUser(user);
            awaiting2FA.remove(uuid);
            return true;
        }
        return false;
    }

    /**
     * Begins tracking an unauthorized player, scheduling authentication timeout enforcement and
     * displaying initial login/register prompts.
     *
     * @param user the database user entity
     * @param player the platform player instance
     */
    public void startTracking(User user, P player) {
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (uuid == null) return;
        var audience = platformHandle.getAudienceForPlayer(player);

        authorized.remove(uuid);
        unAuthorized.put(uuid, user.isRegistered());

        var limit = plugin.getConfiguration().get(ConfigurationKeys.SECONDS_TO_AUTHORIZE);

        if (limit > 0) {
            plugin.cancelOnExit(
                    plugin.delay(
                            () -> {
                                if (!unAuthorized.containsKey(uuid)) return;
                                platformHandle.kick(
                                        player, plugin.getMessages().getMessage("kick.time-limit"));
                            },
                            limit * 1000L),
                    player);
        }

        sendInitialPrompt(uuid, user.isRegistered(), player, audience, 0);
    }

    /**
     * Sends the first prompt once the player is connected to a server. On Velocity the post-login
     * event fires before the backend connection, and commands typed before that are dropped by the
     * proxy, so prompting earlier invites commands that are never executed.
     */
    private void sendInitialPrompt(
            UUID uuid, boolean registered, P player, Audience audience, int attempt) {
        if (!unAuthorized.containsKey(uuid)) return;
        if (platformHandle.getPlayersServerName(player) == null
                && attempt < INITIAL_PROMPT_ATTEMPTS) {
            plugin.cancelOnExit(
                    plugin.delay(
                            () ->
                                    sendInitialPrompt(
                                            uuid, registered, player, audience, attempt + 1),
                            INITIAL_PROMPT_RETRY_MILLIS),
                    player);
            return;
        }
        sendInfoMessage(registered, audience);
    }

    /** Periodically broadcasts action bar prompts to all currently unauthorized players. */
    private void broadcastActionbars() {
        if (!plugin.getConfiguration().get(ConfigurationKeys.USE_ACTION_BAR)) return;
        unAuthorized.forEach(
                (uuid, registered) -> {
                    var player = plugin.getPlayerForUUID(uuid);
                    if (player == null) return;
                    var audience = platformHandle.getAudienceForPlayer(player);
                    if (audience == null) return;
                    sendActionBar(registered, audience);
                });
    }

    /**
     * Sends the appropriate action bar notification depending on registration status.
     *
     * @param registered whether the user is already registered
     * @param audience the player's audience
     */
    private void sendActionBar(boolean registered, Audience audience) {
        if (audience != null && plugin.getConfiguration().get(ConfigurationKeys.USE_ACTION_BAR)) {
            audience.sendActionBar(
                    plugin.getMessages()
                            .getMessage(registered ? "action-bar.login" : "action-bar.register"));
        }
    }

    /**
     * Sends the prompt message and title/subtitle to the unauthorized player.
     *
     * @param registered whether the user is registered
     * @param audience the player's audience
     */
    private void sendInfoMessage(boolean registered, Audience audience) {
        if (audience == null) return;
        audience.sendMessage(
                plugin.getMessages().getMessage(registered ? "prompt.login" : "prompt.register"));
        if (!plugin.getConfiguration().get(ConfigurationKeys.USE_TITLES)) return;
        var toRefresh =
                plugin.getConfiguration()
                        .get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);
        audience.showTitle(
                Title.title(
                        plugin.getMessages()
                                .getMessage(registered ? "title.login" : "title.register"),
                        plugin.getMessages()
                                .getMessage(registered ? "subtitle.login" : "subtitle.register"),
                        Title.Times.times(
                                Duration.ofMillis(0),
                                Duration.ofMillis(toRefresh > 0 ? (long) (toRefresh * 1.1) : 10000),
                                Duration.ofMillis(0))));
    }

    /**
     * Removes the player from the unauthorized tracking map.
     *
     * @param player the player instance
     */
    public void stopTracking(P player) {
        if (player != null) {
            stopTracking(platformHandle.getUUIDForPlayer(player));
        }
    }

    /**
     * Removes the given UUID from the unauthorized tracking map.
     *
     * @param uuid the player's unique identifier
     */
    public void stopTracking(UUID uuid) {
        if (uuid != null) {
            unAuthorized.remove(uuid);
        }
    }

    /** Notifies all unauthorized players with periodic reminder messages and titles. */
    public void notifyUnauthorized() {
        unAuthorized.forEach(
                (uuid, registered) -> {
                    var player = plugin.getPlayerForUUID(uuid);
                    if (player == null) return;
                    var audience = platformHandle.getAudienceForPlayer(player);
                    if (audience == null) return;
                    sendInfoMessage(registered, audience);
                });
    }

    /**
     * Represents email verification metadata including associated token and player UUID.
     *
     * @param email the target email address
     * @param token the generated verification token
     * @param uuid the player unique identifier
     */
    public record EmailVerifyData(String email, String token, UUID uuid) {}

    /**
     * Initiates the two-factor authentication setup or verification process for a player,
     * registering the secret and teleporting them to a limbo server.
     *
     * @param user the user database profile
     * @param player the platform player instance
     * @param data the TOTP secret and QR/URI payload
     */
    public void beginTwoFactorAuth(User user, P player, TOTPData data) {
        var uuid = platformHandle.getUUIDForPlayer(player);
        awaiting2FA.put(uuid, data.secret());

        var limbo = plugin.getServerHandler().chooseLimboServer(user, player);

        if (limbo == null) {
            awaiting2FA.remove(uuid);
            platformHandle.kick(player, plugin.getMessages().getMessage("kick.no-limbo"));
            return;
        }

        platformHandle
                .movePlayer(player, limbo)
                .whenComplete(
                        (t, e) -> {
                            if (t != null || e != null) {
                                awaiting2FA.remove(uuid);
                                platformHandle.kick(
                                        player, plugin.getMessages().getMessage("kick.no-limbo"));
                            }
                        });
    }
}
