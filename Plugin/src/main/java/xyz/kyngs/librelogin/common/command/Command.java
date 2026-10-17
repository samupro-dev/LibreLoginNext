/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.MessageKeys;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.configuration.Messages;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.ReadWriteDatabaseProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.common.util.PlatformAudience;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;

/**
 * Abstract base command for all LibreLogin commands, providing common utilities, database access,
 * message resolution, and authorization verification.
 *
 * @param <P> the platform-specific player type
 */
public class Command<P> extends BaseCommand {

    protected final AuthenticLibreLogin<P, ?> plugin;

    /**
     * Constructs a new command instance associated with the given plugin instance.
     *
     * @param plugin the plugin instance
     */
    public Command(AuthenticLibreLogin<P, ?> plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtains the primary read-write database provider.
     *
     * @return the database provider
     */
    protected ReadWriteDatabaseProvider getDatabaseProvider() {
        return plugin.getDatabaseProvider();
    }

    /**
     * Obtains the plugin logger.
     *
     * @return the logger
     */
    protected Logger getLogger() {
        return plugin.getLogger();
    }

    /**
     * Obtains the localized messages handler.
     *
     * @return the messages instance
     */
    protected Messages getMessages() {
        return plugin.getMessages();
    }

    /**
     * Retrieves a localized message by key, applying variable replacements.
     *
     * @param key the message key
     * @param replacements placeholder pairs (key, value)
     * @return the formatted text component
     */
    protected TextComponent getMessage(String key, String... replacements) {
        return getMessages().getMessage(key, replacements);
    }

    /**
     * Obtains the authorization provider.
     *
     * @return the authorization provider
     */
    protected AuthenticAuthorizationProvider<P, ?> getAuthorizationProvider() {
        return plugin.getAuthorizationProvider();
    }

    /**
     * Asserts that the given player is currently authorized.
     *
     * @param player the player to check
     * @throws InvalidCommandArgument if the player is not authorized
     */
    protected void checkAuthorized(P player) {
        if (!getAuthorizationProvider().isAuthorized(player)) {
            throw new InvalidCommandArgument(getMessage("error.not-authorized"));
        }
    }

    /**
     * Resolves the {@link Audience} associated with the given player.
     *
     * @param player the player instance
     * @return the audience representing the player
     */
    protected Audience getAudience(P player) {
        return plugin.getPlatformHandle().getAudienceForPlayer(player);
    }

    /**
     * Resolves the platform player instance from the issuing {@link Audience}, asserting
     * non-console origin.
     *
     * @param sender the command issuing audience
     * @return the platform-specific player instance
     * @throws co.aikar.commands.InvalidCommandArgument if sender is console
     */
    protected P getPlayer(Audience sender) {
        var playerClass = plugin.getPlatformHandle().getPlayerClass();
        Object target = sender instanceof PlatformAudience platform ? platform.target() : sender;
        if (playerClass != null && playerClass.isInstance(target)) {
            return playerClass.cast(target);
        }
        var issuer = co.aikar.commands.CommandManager.getCurrentCommandIssuer();
        if (issuer != null) {
            var player = plugin.getPlayerFromIssuer(issuer);
            if (player != null) {
                return player;
            }
        }
        throw new co.aikar.commands.InvalidCommandArgument(
                MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
    }

    /**
     * Resolves the crypto provider for the given password's algorithm.
     *
     * @param password the hashed password
     * @return the crypto provider
     */
    protected CryptoProvider getCrypto(HashedPassword password) {
        if (password == null) {
            throw new InvalidCommandArgument(getMessage("error.no-password"));
        }
        var crypto = plugin.getCryptoProvider(password.algo());
        if (crypto == null) {
            throw new InvalidCommandArgument(getMessage("error.password-corrupted"));
        }
        return crypto;
    }

    /**
     * Executes a task asynchronously on the common worker pool.
     *
     * @param runnable the task to execute
     * @return a completion stage representing completion
     */
    public CompletionStage<Void> runAsync(Runnable runnable) {
        return GeneralUtil.runAsync(runnable);
    }

    /**
     * Resolves the {@link User} database entity for a given player, validating console and
     * Floodgate status.
     *
     * @param player the player
     * @return the database user
     * @throws co.aikar.commands.InvalidCommandArgument if sender is console
     * @throws InvalidCommandArgument if sender is a Floodgate player
     */
    protected User getUser(P player) {
        if (player == null) {
            throw new co.aikar.commands.InvalidCommandArgument(
                    MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
        }

        var uuid = plugin.getPlatformHandle().getUUIDForPlayer(player);

        if (plugin.fromFloodgate(uuid)) {
            throw new InvalidCommandArgument(getMessage("error.from-floodgate"));
        }

        return plugin.getDatabaseProvider().getByUUID(uuid);
    }

    /**
     * Validates and updates a user's password using the default cryptographic provider.
     *
     * @param sender the audience to receive notifications
     * @param user the database user
     * @param password the new plaintext password
     * @param messageKey the message key to send to sender prior to hashing
     * @throws InvalidCommandArgument if the password is forbidden or cannot be hashed
     */
    protected void setPassword(Audience sender, User user, String password, String messageKey) {
        if (!plugin.validPassword(password)) {
            throw new InvalidCommandArgument(getMessage("error.forbidden-password"));
        }

        if (!getMessages().isEmpty(messageKey)) {
            sender.sendMessage(getMessage(messageKey));
        }

        var defaultProvider = plugin.getDefaultCryptoProvider();
        var hash = defaultProvider.createHash(password);

        if (hash == null) {
            throw new InvalidCommandArgument(getMessage("error.password-too-long"));
        }

        user.setHashedPassword(hash);
        revokeSessions(user);
    }

    /**
     * Revokes remembered IP sessions after a credential change, so a stale session cannot outlive
     * the password or 2FA setting it stood in for.
     *
     * @param user the user whose sessions are revoked (persist with {@code updateUser})
     */
    protected static void revokeSessions(User user) {
        user.setLastAuthentication(null);
        user.setIp(null);
    }
}
