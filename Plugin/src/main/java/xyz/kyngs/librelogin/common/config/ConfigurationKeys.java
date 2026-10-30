/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import java.util.Locale;
import xyz.kyngs.librelogin.common.authorization.ProfileConflictResolutionStrategy;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

/**
 * Declares all static configuration key definitions and default values used throughout LibreLogin
 * Next.
 */
public final class ConfigurationKeys {

    private ConfigurationKeys() {}

    public static final ConfigurationKey<List<String>> ALLOWED_COMMANDS_WHILE_UNAUTHORIZED =
            new ConfigurationKey<>(
                    "allowed-commands-while-unauthorized",
                    List.of("login", "register", "l", "reg", "email", "2fa"),
                    """
                    List of command roots allowed for unauthorized players awaiting authentication.
                    """,
                    ConfigurateHelper::getStringList);

    public static final ConfigurationKey<List<String>> LIMBO =
            new ConfigurationKey<>(
                    "limbo",
                    List.of("limbo0", "limbo1"),
                    """
                    Authentication / Limbo servers (or Paper spawn worlds) where unauthorized players are sent.
                    On Velocity, these server names MUST exist in your proxy server configuration.
                    On Paper standalone, players will be isolated at the world spawn.
                    """,
                    ConfigurateHelper::getStringList);

    public static final Multimap<String, String> LOBBY_DEFAULT = HashMultimap.create();

    static {
        LOBBY_DEFAULT.put("root", "lobby0");
        LOBBY_DEFAULT.put("root", "lobby1");
    }

    public static final ConfigurationKey<Multimap<String, String>> LOBBY =
            new ConfigurationKey<>(
                    "lobby",
                    LOBBY_DEFAULT,
                    """
                    Target backend lobby servers (or Paper worlds) where players are sent upon successful authentication.
                    On Velocity, these server names MUST exist in your proxy server configuration.
                    Supports forced-hosts routing (use § instead of dots in hostnames, e.g. "play§example§com").
                    Servers under "root" are used as the default fallback for standard connections.
                    """,
                    ConfigurateHelper::getServerMap);

    public static final ConfigurationKey<Boolean> IGNORE_MAX_PLAYERS_FROM_BACKEND_PING =
            new ConfigurationKey<>(
                    "ignore-max-players-from-backend-ping",
                    false,
                    """
                    When selecting available lobbies/limbos, servers reporting full player counts are ruled out.
                    Enable this option to ignore the backend server ping max player threshold.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> PING_SERVERS =
            new ConfigurationKey<>(
                    "ping-servers",
                    false,
                    """
                    (Proxy Only) Whether to actively ping backend servers to verify online status and player counts.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> FALLBACK =
            new ConfigurationKey<>(
                    "fallback",
                    false,
                    """
                    (Proxy Only) Should the proxy automatically redirect players to a lobby if their current backend server shuts down?
                    If set to false, players will be disconnected with a kick message.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> REMEMBER_LAST_SERVER =
            new ConfigurationKey<>(
                    "remember-last-server",
                    false,
                    """
                    (Proxy Only) Should the proxy remember and reconnect players to the last server/world they were on?
                    Not recommended for large or multi-lobby networks.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> ALLOW_PROXY_CONNECTIONS =
            new ConfigurationKey<>(
                    "allow-proxy-connections",
                    true,
                    """
                    (Paper Standalone Only) Verifies whether the client IP matches the Mojang session handshake IP.
                    Keep set to true unless the backend server is running behind a custom reverse proxy or VPN.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<String> DEFAULT_CRYPTO_PROVIDER =
            new ConfigurationKey<>(
                    "default-crypto-provider",
                    "Argon-2ID",
                    """
                    Default cryptographic algorithm used for hashing new passwords.
                    Available algorithms:
                      Argon-2ID - Default, modern hybrid memory-hard algorithm (Argon2id v1.3). Highly secure against GPU/ASIC attacks.
                      BCrypt-2A - Industry standard blowfish-based adaptive hashing scheme ($2a$).
                      SCrypt    - Memory-hard cryptographic key derivation function.
                      PBKDF2    - NIST/OWASP approved password-based key derivation function (HMAC-SHA256).
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> PROFILE_CONFLICT_RESOLUTION_STRATEGY =
            new ConfigurationKey<>(
                    "profile-conflict-resolution-strategy",
                    "BLOCK",
                    """
                    Policy for resolving profile collisions between offline (cracked) and online (Mojang premium) accounts:
                    BLOCK, USE_OFFLINE, OVERWRITE.
                    """,
                    (helper, key) -> {
                        var value = helper.getString(key);
                        // null falls back to the default; unknown values fail validation
                        return value == null
                                ? null
                                : ProfileConflictResolutionStrategy.valueOf(
                                                value.trim().toUpperCase(Locale.ROOT))
                                        .name();
                    });

    public static final ConfigurationKey<String> NEW_UUID_CREATOR =
            new ConfigurationKey<>(
                    "new-uuid-creator",
                    "CRACKED",
                    """
                    Method used to generate player UUIDs for newly registered accounts:
                    CRACKED, MOJANG, RANDOM.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Boolean> AUTO_REGISTER =
            new ConfigurationKey<>(
                    "auto-register",
                    false,
                    """
                    Automatically register players joining with verified premium nicknames without prompting for password setup.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Integer> SECONDS_TO_AUTHORIZE =
            new ConfigurationKey<>(
                    "seconds-to-authorize",
                    60,
                    """
                    Maximum duration in seconds allowed for a player to complete /login or /register before being disconnected.
                    Set to -1 to disable.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Integer> MAX_LOGIN_ATTEMPTS =
            new ConfigurationKey<>(
                    "max-login-attempts",
                    3,
                    """
                    Maximum number of incorrect password attempts before the player is disconnected.
                    Set to -1 to disable.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Integer> MILLISECONDS_TO_EXPIRE_LOGIN_ATTEMPTS =
            new ConfigurationKey<>(
                    "milliseconds-to-refresh-login-attempts",
                    30000,
                    """
                    Cooldown window in milliseconds before failed password attempt counters reset.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Integer> MINIMUM_PASSWORD_LENGTH =
            new ConfigurationKey<>(
                    "minimum-password-length",
                    6,
                    """
                    Minimum allowed character length for passwords. Set to -1 to disable.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Boolean> FORBIDDEN_PASSWORDS_DOWNLOAD =
            new ConfigurationKey<>(
                    "forbidden-passwords-download",
                    true,
                    """
                    Download the community list of forbidden passwords into forbidden-passwords-remote.txt on start and reload.
                    Your own forbidden-passwords.txt is never modified; both lists are combined.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<String> PASSWORD_REGEX =
            new ConfigurationKey<>(
                    "password-regex",
                    "",
                    """
                    Optional regular expression pattern that passwords must match (leave empty to disable).
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Boolean> REQUIRE_PASSWORD_CONFIRMATION =
            new ConfigurationKey<>(
                    "require-password-confirmation",
                    true,
                    """
                    Whether player registration requires typing the password twice for confirmation.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> ALLOW_PLAYER_UNREGISTER =
            new ConfigurationKey<>(
                    "allow-player-unregister",
                    false,
                    """
                    Whether players are allowed to unregister their own account using /unregister <password>.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Integer> MINIMUM_USERNAME_LENGTH =
            new ConfigurationKey<>(
                    "minimum-username-length",
                    3,
                    """
                    Minimum character length required for usernames. Set to 0 or lower to disable.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Integer> IP_LIMIT =
            new ConfigurationKey<>(
                    "ip-limit",
                    -1,
                    """
                    Maximum number of registered accounts allowed per public IP address. Set to -1 to disable.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Boolean> USE_TITLES =
            new ConfigurationKey<>(
                    "use-titles",
                    true,
                    """
                    Display on-screen Title and Subtitle reminders to players awaiting authentication.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> USE_ACTION_BAR =
            new ConfigurationKey<>(
                    "use-action-bar",
                    false,
                    """
                    Display Action Bar reminders (above hotbar) to players awaiting authentication.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Integer> MILLISECONDS_TO_REFRESH_NOTIFICATION =
            new ConfigurationKey<>(
                    "milliseconds-to-refresh-notification",
                    10000,
                    """
                    Interval in milliseconds between repetitive chat and title authentication reminders.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Long> SESSION_TIMEOUT =
            new ConfigurationKey<>(
                    "session-timeout",
                    43200L,
                    """
                    Duration in seconds before an authenticated session expires (0 = disabled, requires password on every join).
                    """,
                    ConfigurateHelper::getLong);

    public static final ConfigurationKey<?> LOGIN_SECURITY =
            ConfigurationKey.getComment(
                    "login-security",
                    """
                    Global rate limiting per IP address to defend against distributed credential stuffing.
                    """);

    public static final ConfigurationKey<Integer> MAX_IP_LOGIN_ATTEMPTS =
            new ConfigurationKey<>(
                    "login-security.max-ip-login-attempts",
                    10,
                    """
                    Maximum failed authentication attempts allowed from a single IP across all accounts within the time window.
                    Set to -1 to disable IP-level rate limiting.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Long> IP_ATTEMPTS_EXPIRE_MILLIS =
            new ConfigurationKey<>(
                    "login-security.ip-attempts-expire-millis",
                    60000L,
                    """
                    Expiration window in milliseconds for tracked failed IP attempts.
                    """,
                    ConfigurateHelper::getLong);

    public static final ConfigurationKey<?> PRIVACY =
            ConfigurationKey.getComment(
                    "privacy",
                    """
                    Player IP address storage, GDPR anonymization, and automated data retention policies.
                    """);

    public static final ConfigurationKey<String> IP_STORAGE_MODE =
            new ConfigurationKey<>(
                    "privacy.ip-storage-mode",
                    "PLAIN",
                    """
                    IP address storage mode in database records: PLAIN or HASHED.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> IP_HASH_SALT =
            new ConfigurationKey<>(
                    "privacy.ip-hash-salt",
                    "change-this-to-a-secure-random-secret",
                    """
                    Secret salt key used for calculating HMAC-SHA256 IP hashes.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Integer> AUTO_PURGE_INACTIVE_DAYS =
            new ConfigurationKey<>(
                    "privacy.auto-purge-inactive-days",
                    0,
                    """
                    Automatic retention purge threshold in days.
                    Stale accounts inactive for longer than this period are automatically removed on server startup.
                    Set to 0 to disable automatic retention purging.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<?> DATABASE =
            ConfigurationKey.getComment(
                    "database",
                    """
                    Primary persistence storage configuration for user credentials and profiles.
                    """);

    public static final ConfigurationKey<String> DATABASE_TYPE =
            new ConfigurationKey<>(
                    "database.type",
                    "librelogin-sqlite",
                    """
                    Selected database storage driver: librelogin-sqlite, librelogin-mysql, librelogin-postgresql.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<?> MIGRATION =
            ConfigurationKey.getComment(
                    "migration",
                    """
                    One-time migration system to import legacy user credentials from other authentication plugins.
                    """);

    public static final ConfigurationKey<Boolean> MIGRATION_ON_NEXT_STARTUP =
            new ConfigurationKey<>(
                    "migration.on-next-startup",
                    false,
                    """
                    Execute database migration on the next startup.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<String> MIGRATION_TYPE =
            new ConfigurationKey<>(
                    "migration.type",
                    "authme-sqlite",
                    """
                    Source migration format:
                    authme-mysql, authme-sqlite, authme-postgresql,
                    fastlogin-mysql, fastlogin-sqlite,
                    loginto-mysql, loginto-sqlite,
                    librelogin-migration-mysql, librelogin-migration-sqlite, librelogin-migration-postgresql.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> MIGRATION_MYSQL_OLD_DATABASE_TABLE =
            new ConfigurationKey<>(
                    "migration.old-database.mysql.table",
                    "user-data",
                    """
                    The table name of the old MySQL database to migrate from.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> MIGRATION_POSTGRESQL_OLD_DATABASE_TABLE =
            new ConfigurationKey<>(
                    "migration.old-database.postgresql.table",
                    "user-data",
                    """
                    The table name of the old PostgreSQL database to migrate from.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<?> TOTP =
            ConfigurationKey.getComment(
                    "totp",
                    """
                    RFC 6238 Time-Based One-Time Password (TOTP) two-factor authentication subsystem.
                    """);

    public static final ConfigurationKey<Boolean> TOTP_ENABLED =
            new ConfigurationKey<>(
                    "totp.enabled",
                    true,
                    """
                    Enable TOTP 2FA (/2fa, /2fa confirm).
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<String> TOTP_LABEL =
            new ConfigurationKey<>(
                    "totp.label",
                    "LibreLogin Next Network",
                    """
                    Display issuer label shown in authenticator apps (e.g. Google Authenticator, 2FAS, Aegis, Microsoft Authenticator). Standard RFC 6238 compatible with all TOTP apps.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Integer> TOTP_DELAY =
            new ConfigurationKey<>(
                    "totp.delay",
                    1000,
                    """
                    Delay in milliseconds before projecting the in-game QR code map to the player's inventory.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<?> MAIL =
            ConfigurationKey.getComment(
                    "mail",
                    """
                    Asynchronous SMTP mail service for self-service player password recovery (/email).
                    """);

    public static final ConfigurationKey<Boolean> MAIL_ENABLED =
            new ConfigurationKey<>(
                    "mail.enabled",
                    false,
                    """
                    Enable email recovery features.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<String> MAIL_HOST =
            new ConfigurationKey<>(
                    "mail.host",
                    "smtp.gmail.com",
                    """
                    The host of the SMTP server.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Integer> MAIL_PORT =
            new ConfigurationKey<>(
                    "mail.port",
                    587,
                    """
                    The port of the SMTP server.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<String> MAIL_USERNAME =
            new ConfigurationKey<>(
                    "mail.username",
                    "username",
                    """
                    The username used to log in to the SMTP server.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> MAIL_PASSWORD =
            new ConfigurationKey<>(
                    "mail.password",
                    "password",
                    """
                    The password used to log in to the SMTP server.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> MAIL_SENDER =
            new ConfigurationKey<>(
                    "mail.sender",
                    "LibreLogin Next Network",
                    """
                    Display sender name shown in email client inboxes.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> MAIL_EMAIL =
            new ConfigurationKey<>(
                    "mail.email",
                    "noreply@example.com",
                    """
                    The 'From' sender email address.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Boolean> REDIS_ENABLED =
            new ConfigurationKey<>(
                    "redis.enabled",
                    false,
                    """
                    Enable Redis distributed synchronization.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<String> REDIS_HOST =
            new ConfigurationKey<>(
                    "redis.host",
                    "127.0.0.1",
                    """
                    Hostname or IP address of the Redis server.
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Integer> REDIS_PORT =
            new ConfigurationKey<>(
                    "redis.port",
                    6379,
                    """
                    Port of the Redis server.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<String> REDIS_USERNAME =
            new ConfigurationKey<>(
                    "redis.username",
                    "",
                    """
                    Redis username (leave empty if your Redis uses standard password authentication without ACL users).
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> REDIS_PASSWORD =
            new ConfigurationKey<>(
                    "redis.password",
                    "",
                    """
                    Redis authentication password (leave empty for unauthenticated).
                    """,
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<Integer> REDIS_DATABASE =
            new ConfigurationKey<>(
                    "redis.database",
                    0,
                    """
                    Redis logical database index (0-15).
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Boolean> REDIS_SSL =
            new ConfigurationKey<>(
                    "redis.ssl",
                    false,
                    """
                    Whether to enable SSL/TLS encryption for Redis connections.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Integer> REDIS_TIMEOUT_MS =
            new ConfigurationKey<>(
                    "redis.timeout-ms",
                    2000,
                    """
                    Redis command timeout in milliseconds.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Integer> REDIS_HEARTBEAT_INTERVAL_SECONDS =
            new ConfigurationKey<>(
                    "redis.heartbeat-interval-seconds",
                    15,
                    """
                    Interval in seconds between node presence heartbeats.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Integer> REDIS_KEY_TTL_SECONDS =
            new ConfigurationKey<>(
                    "redis.key-ttl-seconds",
                    45,
                    """
                    Key time-to-live in seconds for active player presence records in Redis.
                    """,
                    ConfigurateHelper::getInt);

    public static final ConfigurationKey<Boolean> CHECK_FOR_UPDATES =
            new ConfigurationKey<>(
                    "check-for-updates",
                    true,
                    """
                    Check GitHub for newer releases (pre-releases included) on startup.
                    """,
                    ConfigurateHelper::getBoolean);

    public static final ConfigurationKey<Boolean> DEBUG =
            new ConfigurationKey<>(
                    "debug",
                    false,
                    """
                    Enable verbose diagnostic logging in console for troubleshooting.
                    """,
                    ConfigurateHelper::getBoolean);
}
