/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.listener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.BiHolder;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.premium.PremiumException;
import xyz.kyngs.librelogin.api.premium.PremiumUser;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.authorization.ProfileConflictResolutionStrategy;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.database.AuthenticDatabaseProvider;
import xyz.kyngs.librelogin.common.database.AuthenticUser;
import xyz.kyngs.librelogin.common.event.events.AuthenticAuthenticatedEvent;

/**
 * Common event listener base class managing player connection lifecycles, pre-login validation,
 * premium authentication verification, session restoration, server redirection routing, and
 * post-login state tracking.
 *
 * @param <Plugin> the plugin implementation type
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticListeners<Plugin extends AuthenticLibreLogin<P, S>, P, S> {

    /**
     * Fast-path, zero-allocation validator for Minecraft usernames.
     *
     * @param username the username to validate
     * @return true if non-null, 1-16 chars, and alphanumeric or underscore
     */
    public static boolean isValidMinecraftUsername(String username) {
        if (username == null || username.isEmpty() || username.length() > 16) {
            return false;
        }
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if (!((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /** Pre-logins allowed per IP address within {@link #PRE_LOGIN_WINDOW_SECONDS}. */
    private static final int PRE_LOGIN_LIMIT = 8;

    private static final int PRE_LOGIN_WINDOW_SECONDS = 10;

    protected final Plugin plugin;
    protected final PlatformHandle<P, S> platformHandle;

    // Fixed window per address: the counter is created on the first attempt and expires with it.
    private final Cache<InetAddress, AtomicInteger> preLoginAttempts =
            Caffeine.newBuilder()
                    .expireAfterWrite(PRE_LOGIN_WINDOW_SECONDS, TimeUnit.SECONDS)
                    .maximumSize(100_000)
                    .build();

    /**
     * Constructs a new base listener handler.
     *
     * @param plugin the plugin instance
     */
    public AuthenticListeners(Plugin plugin) {
        this.plugin = plugin;
        platformHandle = plugin.getPlatformHandle();
    }

    private boolean isSessionIpValid(User user, String clientIp) {
        var storedIp = user.getIp();
        if (storedIp == null || clientIp == null) return false;
        var clientAnonymized = plugin.anonymizeIP(clientIp);
        if (clientAnonymized == null) return false;
        return java.security.MessageDigest.isEqual(
                storedIp.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                clientAnonymized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Returns whether a password user may resume an IP session without logging in. Accounts with
     * 2FA always have to authenticate: a session must never skip the second factor. Sessions are
     * revoked (last authentication cleared) whenever the password or 2FA settings change.
     */
    private boolean hasValidSession(User user, String ip, Duration sessionTime) {
        return sessionTime != null
                && !sessionTime.isZero()
                && !sessionTime.isNegative()
                && user.getSecret() == null
                && user.getLastAuthentication() != null
                && isSessionIpValid(user, ip)
                && user.getLastAuthentication()
                        .toLocalDateTime()
                        .plus(sessionTime)
                        .isAfter(LocalDateTime.now());
    }

    /**
     * Handles post-login state evaluation when a player finishes initial handshake. Evaluates
     * premium auto-login, active session timeout continuity, or unauthorized tracking.
     *
     * @param player the platform player instance
     * @param user the resolved database user, or null if to be loaded from database
     */
    protected void onPostLogin(P player, User user) {
        var ip = platformHandle.getIP(player);
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (plugin.fromFloodgate(uuid)) return;

        // The cross-proxy kick of other sessions for this UUID is published only once this
        // connection is authorized (see AuthenticAuthorizationProvider), never before.
        plugin.registerLocalPlayer(uuid);

        if (user == null) {
            user = plugin.getDatabaseProvider().getByUUID(uuid);
        }
        if (user == null) {
            platformHandle.kick(player, plugin.getMessages().getMessage("kick.database-error"));
            return;
        }
        var sessionTime =
                Duration.ofSeconds(
                        plugin.getConfiguration().get(ConfigurationKeys.SESSION_TIMEOUT));

        if (user.autoLoginEnabled()) {
            plugin.getAuthorizationProvider().markAuthorized(uuid);
            if (!plugin.getMessages().isEmpty("info.premium-logged-in")) {
                plugin.delay(
                        () ->
                                plugin.getPlatformHandle()
                                        .getAudienceForPlayer(player)
                                        .sendMessage(
                                                plugin.getMessages()
                                                        .getMessage("info.premium-logged-in")),
                        500);
            }
            plugin.getEventProvider()
                    .fire(
                            plugin.getEventTypes().authenticated,
                            new AuthenticAuthenticatedEvent<>(
                                    user,
                                    player,
                                    plugin,
                                    AuthenticatedEvent.AuthenticationReason.PREMIUM));
        } else if (hasValidSession(user, ip, sessionTime)) {
            plugin.getAuthorizationProvider().markAuthorized(uuid);
            if (!plugin.getMessages().isEmpty("info.session-logged-in")) {
                plugin.delay(
                        () ->
                                plugin.getPlatformHandle()
                                        .getAudienceForPlayer(player)
                                        .sendMessage(
                                                plugin.getMessages()
                                                        .getMessage("info.session-logged-in")),
                        500);
            }
            plugin.getEventProvider()
                    .fire(
                            plugin.getEventTypes().authenticated,
                            new AuthenticAuthenticatedEvent<>(
                                    user,
                                    player,
                                    plugin,
                                    AuthenticatedEvent.AuthenticationReason.SESSION));
        } else {
            plugin.getAuthorizationProvider().startTracking(user, player);
        }

        user.setLastSeen(Timestamp.valueOf(LocalDateTime.now()));

        var finalUser = user;
        plugin.delay(() -> plugin.getDatabaseProvider().updateUser(finalUser), 0);

        if (plugin.getPlayerForUUID(uuid) != player) {
            // The player disconnected while this login was processed (the quit cleanup already
            // ran): undo the presence and tracking state registered above.
            onPlayerDisconnect(player);
        }
    }

    /**
     * Handles cleanup logic when a player disconnects from the proxy/server.
     *
     * @param player the platform player instance
     */
    protected void onPlayerDisconnect(P player) {
        plugin.onExit(player);
        plugin.getAuthorizationProvider().onExit(player);
    }

    /**
     * Performs pre-login checks including username validation, Mojang API lookup, database
     * connectivity verification, and online/offline encryption mode determination.
     *
     * @param username the connecting player's username
     * @param address the remote network address of the player
     * @return a {@link PreLoginResult} specifying login disposition and user entity
     */
    protected PreLoginResult onPreLogin(String username, InetAddress address) {
        if (address != null
                && !address.isLoopbackAddress()
                && preLoginAttempts.get(address, ignored -> new AtomicInteger()).incrementAndGet()
                        > PRE_LOGIN_LIMIT) {
            // Checked before any Mojang or database work so one address cannot flood them.
            return new PreLoginResult(
                    PreLoginState.DENIED,
                    plugin.getMessages().getMessage("kick.too-many-connections"),
                    null);
        }

        if (!isValidMinecraftUsername(username)) {
            return new PreLoginResult(
                    PreLoginState.DENIED,
                    plugin.getMessages().getMessage("kick.illegal-username"),
                    null);
        }

        PremiumUser mojangData;

        try {
            mojangData = plugin.getPremiumProvider().getUserForName(username);
        } catch (PremiumException e) {
            var message =
                    switch (e.getIssue()) {
                        case THROTTLED ->
                                plugin.getMessages().getMessage("kick.premium-error-throttled");
                        default -> {
                            plugin.getLogger()
                                    .error(
                                            "Encountered an error while communicating with Mojang"
                                                    + " API for user: "
                                                    + username,
                                            e);
                            yield plugin.getMessages().getMessage("kick.premium-error-undefined");
                        }
                    };

            return new PreLoginResult(PreLoginState.DENIED, message, null);
        }

        try {
            if (!plugin.getDatabaseProvider().connected()) {
                plugin.getLogger()
                        .error(
                                "Database is currently disconnected! Rejecting login for "
                                        + username
                                        + " to prevent security bypass.");
                return new PreLoginResult(
                        PreLoginState.DENIED,
                        plugin.getMessages().getMessage("kick.database-error"),
                        null);
            }

            if (mojangData == null) {
                // A user with this name does not exist in the Mojang database; offline mode user.
                User user;
                try {
                    user = checkAndValidateByName(username, null, true, address);
                } catch (InvalidCommandArgument e) {
                    return new PreLoginResult(PreLoginState.DENIED, e.getErrorMessage(), null);
                }

                @SuppressWarnings("ConstantConditions")
                var hasPremiumUUID = user.getPremiumUUID() != null;
                if (hasPremiumUUID) {
                    // Online encryption required to prevent hijacking accounts with premium UUID
                    return new PreLoginResult(PreLoginState.FORCE_ONLINE, null, user);
                }
            } else {
                // A user with this name exists in Mojang database; determine encryption mode
                var premiumID = mojangData.uuid();
                var user = plugin.getDatabaseProvider().getByPremiumUUID(premiumID);

                if (user == null) {
                    User userByName;
                    try {
                        userByName = checkAndValidateByName(username, mojangData, true, address);
                    } catch (InvalidCommandArgument e) {
                        return new PreLoginResult(PreLoginState.DENIED, e.getErrorMessage(), null);
                    }

                    @SuppressWarnings("ConstantConditions")
                    var autoLogin = userByName.autoLoginEnabled();
                    if (autoLogin) {
                        return new PreLoginResult(PreLoginState.FORCE_ONLINE, null, userByName);
                    }
                } else {
                    User byName;
                    try {
                        byName = checkAndValidateByName(username, mojangData, false, address);
                    } catch (InvalidCommandArgument e) {
                        return new PreLoginResult(PreLoginState.DENIED, e.getErrorMessage(), null);
                    }

                    if (byName != null && !user.equals(byName)) {
                        // Profile conflict: username belongs to a different record
                        return handleProfileConflict(user, byName);
                    }

                    if (!mojangData.reliable()) {
                        plugin.getLogger()
                                .warn(
                                        "Player '{}' profile data from Mojang API is unverified;"
                                                + " using current username case.",
                                        username);
                        mojangData = new PremiumUser(mojangData.uuid(), username, false);
                    }

                    if (!user.getLastNickname().contentEquals(mojangData.name())) {
                        // Update updated nickname in database
                        user.setLastNickname(mojangData.name());
                        plugin.getDatabaseProvider().updateUser(user);
                    }

                    return new PreLoginResult(PreLoginState.FORCE_ONLINE, null, user);
                }
            }

            return new PreLoginResult(PreLoginState.FORCE_OFFLINE, null, null);
        } catch (RuntimeException e) {
            plugin.getLogger()
                    .error(
                            "Database error during pre-login for user "
                                    + username
                                    + "! Rejecting login to prevent security bypass: "
                                    + e.getMessage());
            return new PreLoginResult(
                    PreLoginState.DENIED,
                    plugin.getMessages().getMessage("kick.database-error"),
                    null);
        }
    }

    /**
     * Resolves profile conflict between an existing account and an incoming authenticated profile
     * according to the configured conflict resolution strategy.
     *
     * @param conflicting the existing conflicting premium user
     * @param conflicted the conflicting username record
     * @return the resulting pre-login outcome
     */
    private PreLoginResult handleProfileConflict(User conflicting, User conflicted) {
        return switch (ProfileConflictResolutionStrategy.valueOf(
                plugin.getConfiguration()
                        .get(ConfigurationKeys.PROFILE_CONFLICT_RESOLUTION_STRATEGY))) {
            case BLOCK ->
                    new PreLoginResult(
                            PreLoginState.DENIED,
                            plugin.getMessages()
                                    .getMessage(
                                            "kick.name-mismatch",
                                            "%nickname%",
                                            conflicting.getLastNickname()),
                            null);
            case USE_OFFLINE -> new PreLoginResult(PreLoginState.FORCE_OFFLINE, null, null);
            case OVERWRITE -> {
                if (plugin.getDatabaseProvider() instanceof AuthenticDatabaseProvider<?> sql) {
                    // Keep a copy in librepremium_data_archive instead of an irreversible delete.
                    sql.archiveUser(conflicted);
                } else {
                    plugin.getDatabaseProvider().deleteUser(conflicted);
                }
                conflicting.setLastNickname(conflicted.getLastNickname());
                plugin.getDatabaseProvider().updateUser(conflicting);
                yield new PreLoginResult(PreLoginState.FORCE_ONLINE, null, conflicting);
            }
        };
    }

    /**
     * Checks and validates a user by their username, validating casing consistency, username
     * length, and IP registration limits.
     *
     * @param username the username of the user
     * @param premiumUser the premium user details from Mojang, if available
     * @param generate true if a new user record should be generated if missing
     * @param ip the remote IP address of the user
     * @return the validated user record, or null if not found and {@code generate} is false
     * @throws InvalidCommandArgument if username validation or IP limit checks fail
     */
    protected User checkAndValidateByName(
            String username, @Nullable PremiumUser premiumUser, boolean generate, InetAddress ip)
            throws InvalidCommandArgument {
        var user = plugin.getDatabaseProvider().getByName(username);

        if (user != null) {
            if (!user.getLastNickname().contentEquals(username)) {
                throw new InvalidCommandArgument(
                        plugin.getMessages()
                                .getMessage(
                                        "kick.invalid-case-username",
                                        "%username%",
                                        user.getLastNickname()));
            }
        } else if (generate) {
            var minLength =
                    plugin.getConfiguration().get(ConfigurationKeys.MINIMUM_USERNAME_LENGTH);
            if (username.length() < minLength) {
                throw new InvalidCommandArgument(
                        plugin.getMessages()
                                .getMessage(
                                        "kick.short-username",
                                        "%length%",
                                        String.valueOf(minLength)));
            }

            var ipLimit = plugin.getConfiguration().get(ConfigurationKeys.IP_LIMIT);
            var rawIpStr = ip != null ? ip.getHostAddress() : "127.0.0.1";
            var storedIp = plugin.anonymizeIP(rawIpStr);
            if (ipLimit > 0) {
                var ipCount =
                        plugin.getDatabaseProvider().getByIP(storedIp).stream()
                                .filter(User::isRegistered)
                                .count();

                if (ipCount >= ipLimit) {
                    throw new InvalidCommandArgument(
                            plugin.getMessages()
                                    .getMessage(
                                            "kick.ip-limit", "%limit%", String.valueOf(ipLimit)));
                }
            }

            var newID =
                    plugin.generateNewUUID(
                            username, premiumUser == null ? null : premiumUser.uuid());

            var conflictingUser = plugin.getDatabaseProvider().getByUUID(newID);

            if (conflictingUser != null) {
                throw new InvalidCommandArgument(
                        plugin.getMessages()
                                .getMessage(
                                        "kick.occupied-username",
                                        "%username%",
                                        conflictingUser.getLastNickname()));
            }

            if (premiumUser != null
                    && premiumUser.reliable()
                    && plugin.getConfiguration().get(ConfigurationKeys.AUTO_REGISTER)) {
                if (!premiumUser.name().contentEquals(username)) {
                    throw new InvalidCommandArgument(
                            plugin.getMessages()
                                    .getMessage(
                                            "kick.invalid-case-username",
                                            "%username%",
                                            premiumUser.name()));
                }
                user =
                        new AuthenticUser(
                                newID,
                                premiumUser.uuid(),
                                null,
                                username,
                                Timestamp.valueOf(LocalDateTime.now()),
                                Timestamp.valueOf(LocalDateTime.now()),
                                null,
                                storedIp,
                                null,
                                null,
                                null);
            } else {
                if (premiumUser != null && !premiumUser.reliable()) {
                    plugin.getLogger()
                            .warn(
                                    "Mojang profile data for '{}' could not be reliably verified;"
                                            + " falling back to offline registration.",
                                    username);
                }
                user =
                        new AuthenticUser(
                                newID,
                                null,
                                null,
                                username,
                                Timestamp.valueOf(LocalDateTime.now()),
                                Timestamp.valueOf(LocalDateTime.now()),
                                null,
                                storedIp,
                                null,
                                null,
                                null);
            }

            plugin.getDatabaseProvider().insertUser(user);
        } else {
            return null;
        }

        return user;
    }

    /**
     * Determines whether the player should be routed to a lobby or limbo server based on
     * authentication state.
     *
     * @param player the platform player instance
     * @param ip the optional IP address string
     * @param user the user database record, or null
     * @return a {@link BiHolder} containing authorization boolean and destination server
     */
    protected BiHolder<Boolean, S> chooseServer(
            P player, @Nullable String ip, @Nullable User user) {
        return chooseServer(
                platformHandle.getUUIDForPlayer(player),
                ip == null ? platformHandle.getIP(player) : ip,
                user);
    }

    /**
     * Determines whether the player should be routed to a lobby or limbo server based on UUID, IP,
     * and authentication state.
     *
     * @param uuid the player's unique identifier
     * @param ip the player's IP address
     * @param user the user database record, or null
     * @return a {@link BiHolder} where key is true if player is authorized, and value is the
     *     destination server
     */
    protected BiHolder<Boolean, S> chooseServer(
            UUID uuid, @NotNull String ip, @Nullable User user) {
        var fromFloodgate = plugin.fromFloodgate(uuid);

        var sessionTime =
                Duration.ofSeconds(
                        plugin.getConfiguration().get(ConfigurationKeys.SESSION_TIMEOUT));

        if (fromFloodgate) {
            user = null;
        } else if (user == null) {
            if (plugin.mainThread()) {
                plugin.getLogger()
                        .warn(
                                "Blocking database lookup prevented on main thread for UUID: {}."
                                        + " Defaulting to Limbo.",
                                uuid);
                return new BiHolder<>(
                        false, plugin.getServerHandler().chooseLimboServer(null, null));
            }
            user = plugin.getDatabaseProvider().getByUUID(uuid);
        }

        if (fromFloodgate
                || (user != null
                        && (user.autoLoginEnabled() || hasValidSession(user, ip, sessionTime)))) {
            return new BiHolder<>(
                    true, plugin.getServerHandler().chooseLobbyServer(user, null, true, false));
        } else {
            return new BiHolder<>(false, plugin.getServerHandler().chooseLimboServer(user, null));
        }
    }
}
