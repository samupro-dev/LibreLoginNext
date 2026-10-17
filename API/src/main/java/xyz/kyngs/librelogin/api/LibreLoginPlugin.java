/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api;

import java.io.File;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.configuration.Messages;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.ReadDatabaseProvider;
import xyz.kyngs.librelogin.api.database.ReadDatabaseProviderRegistration;
import xyz.kyngs.librelogin.api.database.ReadWriteDatabaseProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.WriteDatabaseProvider;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.api.event.EventProvider;
import xyz.kyngs.librelogin.api.event.EventTypes;
import xyz.kyngs.librelogin.api.image.ImageProjector;
import xyz.kyngs.librelogin.api.integration.LimboIntegration;
import xyz.kyngs.librelogin.api.mail.EmailHandler;
import xyz.kyngs.librelogin.api.premium.PremiumProvider;
import xyz.kyngs.librelogin.api.server.ServerHandler;
import xyz.kyngs.librelogin.api.totp.TOTPProvider;
import xyz.kyngs.librelogin.api.util.SemanticVersion;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;

/**
 * The core plugin interface exposing management services, configuration, database providers, event
 * dispatchers, and authentication handlers.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface LibreLoginPlugin<P, S> {

    /**
     * Gets the current premium provider.
     *
     * @return the premium provider
     */
    PremiumProvider getPremiumProvider();

    /**
     * Gets the plugin's logger.
     *
     * @return the logger
     */
    Logger getLogger();

    /**
     * Gets a plugin resource as an input stream.
     *
     * @param name the name of the resource
     * @return the input stream, or null if the resource does not exist
     */
    @Nullable
    InputStream getResourceAsStream(String name);

    /**
     * Gets the plugin's authorization provider.
     *
     * @return the authorization provider
     */
    AuthorizationProvider<P> getAuthorizationProvider();

    /**
     * Gets the plugin's event provider.
     *
     * @return the event provider
     */
    EventProvider<P, S> getEventProvider();

    /**
     * Gets the plugin's message provider.
     *
     * @return the message provider
     */
    Messages getMessages();

    /**
     * Gets the plugin's crypto provider for the given algorithm identifier.
     *
     * @param id the algorithm identifier
     * @return the crypto provider, or null if none registered for the identifier
     */
    @Nullable
    CryptoProvider getCryptoProvider(String id);

    /**
     * Gets the default crypto provider.
     *
     * @return the default crypto provider
     */
    CryptoProvider getDefaultCryptoProvider();

    /**
     * Gets the plugin's active read/write database provider.
     *
     * @return the database provider
     */
    ReadWriteDatabaseProvider getDatabaseProvider();

    /**
     * Gets the plugin's TOTP provider.
     *
     * @return the TOTP provider
     */
    TOTPProvider getTOTPProvider();

    /**
     * Gets the plugin's image projector.
     *
     * @return the image projector
     */
    ImageProjector<P> getImageProjector();

    /**
     * Migrates data from one database provider to another.
     *
     * @param from the source database provider to migrate from
     * @param to the target database provider to migrate to
     */
    void migrate(ReadDatabaseProvider from, WriteDatabaseProvider to);

    /**
     * Gets an immutable map of registered read database providers keyed by identifier.
     *
     * @return the immutable map of read providers
     */
    Map<String, ReadDatabaseProviderRegistration<?, ?, ?>> getReadProviders();

    /**
     * Registers a custom read database provider.
     *
     * <p>If a {@link ReadWriteDatabaseProvider} is passed, it can also be configured as the primary
     * database provider for the plugin.
     *
     * @param registration the registration definition of the provider
     */
    void registerReadProvider(ReadDatabaseProviderRegistration<?, ?, ?> registration);

    /**
     * Registers a custom cryptographic hashing algorithm provider.
     *
     * @param provider the crypto provider to register
     */
    void registerCryptoProvider(CryptoProvider provider);

    /**
     * Registers a new database connector factory.
     *
     * @param clazz the connector interface/class being registered
     * @param factory the factory used to instantiate the connector with a configuration prefix
     * @param id the identifier of the connector
     * @param <C> the connector type
     * @param <E> the exception type thrown by the connector
     */
    <E extends Exception, C extends DatabaseConnector<E, ?>> void registerDatabaseConnector(
            Class<?> clazz, ThrowableFunction<String, C, E> factory, String id);

    /**
     * Gets the plugin data directory.
     *
     * @return the data folder
     */
    File getDataFolder();

    /** Ensures that the plugin data directory exists, creating it if necessary. */
    void checkDataFolder();

    /**
     * Gets the raw plugin version string.
     *
     * @return the version string
     */
    String getVersion();

    /**
     * Gets the parsed semantic version of the plugin.
     *
     * @return the semantic version instance
     */
    SemanticVersion getParsedVersion();

    /**
     * Checks whether a player with the specified UUID is connected to the network.
     *
     * @param uuid the UUID of the player
     * @return true if the player is present on the network, false otherwise
     */
    boolean isPresent(UUID uuid);

    /**
     * Checks whether multi-proxy clustering support is enabled.
     *
     * @return true if multi-proxy is enabled, false otherwise
     */
    boolean multiProxyEnabled();

    /**
     * Validates whether a candidate password satisfies the configured complexity requirements.
     *
     * @param password the password to validate
     * @return true if the password is valid, false otherwise
     */
    boolean validPassword(String password);

    /**
     * Gets a local player instance by their unique identifier.
     *
     * <p>Note: This only checks the local proxy instance and cannot substitute {@link
     * #isPresent(UUID)} in multi-proxy environments.
     *
     * @param uuid the UUID of the player
     * @return the player object, or null if the player is not connected to this proxy
     */
    @Nullable
    P getPlayerForUUID(UUID uuid);

    /**
     * Gets the platform-specific handle for interacting with underlying server constructs.
     *
     * @return the platform handle
     */
    PlatformHandle<P, S> getPlatformHandle();

    /**
     * Gets the server handler responsible for server selection, lobby routing, and limbo
     * management.
     *
     * @return the server handler
     */
    ServerHandler<P, S> getServerHandler();

    /**
     * Gets the email service handler if enabled.
     *
     * @return the email handler, or null if email integration is disabled
     */
    @Nullable
    EmailHandler getEmailHandler();

    /**
     * Gets the Limbo server integration instance if available.
     *
     * @return the limbo integration provider, or null if unsupported
     */
    @Nullable
    LimboIntegration<S> getLimboIntegration();

    /**
     * Gets the registry of event types available for subscription.
     *
     * @return the event types registry
     */
    default EventTypes<P, S> getEventTypes() {
        return getEventProvider().getTypes();
    }

    /**
     * Creates a new {@link User} domain model instance initialized with the given fields.
     *
     * @param uuid the persistent UUID of the user
     * @param premiumUUID the Mojang UUID if premium, or null
     * @param hashedPassword the hashed password credential, or null
     * @param lastNickname the last known nickname of the user
     * @param joinDate the initial registration timestamp
     * @param lastSeen the last seen timestamp
     * @param secret the TOTP secret key, or null
     * @param ip the last recorded IP address, or null
     * @param lastAuthentication the timestamp of the last successful login, or null
     * @param lastServer the last connected backend server identifier, or null
     * @param email the linked email address, or null
     * @return a new User model instance
     */
    User createUser(
            UUID uuid,
            @Nullable UUID premiumUUID,
            @Nullable HashedPassword hashedPassword,
            String lastNickname,
            Timestamp joinDate,
            Timestamp lastSeen,
            @Nullable String secret,
            @Nullable String ip,
            @Nullable Timestamp lastAuthentication,
            @Nullable String lastServer,
            @Nullable String email);
}
