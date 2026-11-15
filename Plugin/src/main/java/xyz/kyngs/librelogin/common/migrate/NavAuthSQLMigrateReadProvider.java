/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.common.crypto.NavAuthPBKDF2CryptoProvider;
import xyz.kyngs.librelogin.common.database.AuthenticUser;

/**
 * Migration read provider for NavAuth 0.3 databases (MySQL/MariaDB, PostgreSQL, SQLite).
 *
 * <p>NavAuth keeps its data in fixed ORMLite tables: {@code navauth_users}, {@code
 * navauth_credentials} and the optional {@code navauth_users_sessions}. The NavAuth user UUID is
 * the UUID given to the player in game, so it is kept as the LibreLogin UUID. H2 databases must
 * first be converted with NavAuth's own database type migration.
 *
 * <p>Non-premium accounts without a password (NavAuth allows 2FA-only accounts) and accounts with a
 * hash LibreLogin cannot verify are imported locked (see {@link #LOCKED_ALGORITHM}): leaving them
 * out would let anyone register the name and inherit its UUID. Rows without a valid UUID abort the
 * migration, because they cannot be locked.
 */
public class NavAuthSQLMigrateReadProvider extends SQLMigrateReadProvider {

    private static final String USERS_TABLE = "navauth_users";
    private static final String CREDENTIALS_TABLE = "navauth_credentials";
    private static final String SESSIONS_TABLE = "navauth_users_sessions";

    private record Credentials(String hash, String algo, String secret) {}

    private record Activity(long joined, long left, long ip) {}

    /**
     * Constructs a new NavAuth migration provider.
     *
     * @param logger the logger instance
     * @param connector the SQL database connector of the NavAuth database
     */
    public NavAuthSQLMigrateReadProvider(Logger logger, SQLDatabaseConnector connector) {
        super(USERS_TABLE, logger, connector);
    }

    @Override
    public Collection<User> getAllUsers() {
        return connector.runQuery(connection -> {
            var credentials = readCredentials(connection);
            var activity = readActivity(connection);
            var users = new ArrayList<User>();
            var locked = new ArrayList<String>();
            var unreadable = 0;

            try (var ps = connection.prepareStatement("SELECT * FROM " + USERS_TABLE);
                    var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var name = rs.getString("username");
                    UUID uuid = null;
                    try {
                        var uuidRaw = rs.getString("uuid");
                        uuid = UUID.fromString(uuidRaw);
                        var mojangRaw = rs.getString("mojang_uuid");
                        var premiumUuid = mojangRaw == null ? null : UUID.fromString(mojangRaw);

                        var credential = credentials.get(uuidRaw.toLowerCase(Locale.ROOT));
                        HashedPassword password = null;
                        String secret = null;
                        if (credential != null) {
                            secret =
                                    credential.secret() == null || credential.secret().isBlank()
                                            ? null
                                            : credential.secret();
                            if (credential.hash() != null) {
                                password = toHashedPassword(credential.algo(), credential.hash());
                                if (password == null) {
                                    password = locked(credential.hash());
                                    locked.add(name);
                                }
                            }
                        }
                        if (premiumUuid == null && password == null) {
                            // 2FA-only or credential-less account: LibreLogin needs a
                            // password.
                            password = locked(null);
                            locked.add(name);
                        }

                        var session = activity.get(uuidRaw.toLowerCase(Locale.ROOT));
                        users.add(
                                new AuthenticUser(
                                        uuid,
                                        premiumUuid,
                                        password,
                                        name,
                                        session == null ? null : new Timestamp(session.joined()),
                                        session == null ? null : new Timestamp(session.left()),
                                        secret,
                                        session == null ? null : ipv4(session.ip()),
                                        null,
                                        null,
                                        null));
                    } catch (RuntimeException e) {
                        if (name == null || name.isEmpty() || uuid == null) {
                            logger.error(
                                    "Failed to read a NavAuth user without a known" + " identity.",
                                    e);
                            unreadable++;
                            continue;
                        }
                        logger.error(
                                "Failed to read NavAuth user " + name + "; importing it locked.",
                                e);
                        users.add(lockedUser(uuid, name));
                        locked.add(name);
                    }
                }
            }

            // Without a valid UUID an unreadable row cannot be locked, and leaving it out
            // could make its account claimable.
            if (unreadable > 0) {
                throw new IllegalStateException(
                        "NavAuth migration aborted so no account becomes claimable: "
                                + unreadable
                                + " row(s) of navauth_users have an invalid UUID or no"
                                + " name. Fix these rows and run the migration again.");
            }

            warnLocked("NavAuth", locked);
            return users;
        });
    }

    /**
     * Converts a NavAuth credential into a LibreLogin password.
     *
     * @param algo the NavAuth {@code HashingAlgorithm} name
     * @param hash the stored NavAuth password hash
     * @return the converted password, or {@code null} if the algorithm or hash is not supported
     */
    static @Nullable HashedPassword toHashedPassword(@Nullable String algo, String hash) {
        if (algo == null || hash.isEmpty()) {
            return null;
        }
        return switch (algo) {
            case "BCRYPT" ->
                    hash.startsWith("$2") ? new HashedPassword(hash, null, "BCrypt-2A") : null;
            case "ARGON2" ->
                    hash.startsWith("$argon2id$")
                            ? new HashedPassword(hash, null, "Argon-2ID")
                            : null;
            case "SHA256", "SHA512" ->
                    hash.startsWith("pbkdf2-sha")
                            ? new HashedPassword(hash, null, NavAuthPBKDF2CryptoProvider.IDENTIFIER)
                            : null;
            case "LIBRELOGIN_SHA256", "LIBRELOGIN_SHA512" -> fromLibreLoginSha(hash);
            default -> null;
        };
    }

    // NavAuth stores hashes migrated from LibreLogin as "<SHA256|SHA512>$<salt hex>$<hash hex>".
    private static @Nullable HashedPassword fromLibreLoginSha(String hash) {
        var parts = hash.split("\\$");
        if (parts.length != 3 || parts[1].isEmpty() || parts[2].isEmpty()) {
            return null;
        }
        var digest =
                switch (parts[0]) {
                    case "SHA256" -> "SHA-256";
                    case "SHA512" -> "SHA-512";
                    default -> null;
                };
        return digest == null ? null : new HashedPassword(parts[2], parts[1], digest);
    }

    private static Map<String, Credentials> readCredentials(Connection connection)
            throws SQLException {
        var credentials = new HashMap<String, Credentials>();
        try (var ps = connection.prepareStatement("SELECT * FROM " + CREDENTIALS_TABLE);
                var rs = ps.executeQuery()) {
            while (rs.next()) {
                credentials.put(
                        rs.getString("uuid").toLowerCase(Locale.ROOT),
                        new Credentials(
                                rs.getString("passwordHash"),
                                rs.getString("algo"),
                                rs.getString("twoFactorSecret")));
            }
        }
        return credentials;
    }

    // First join, last leave and the IP of the latest session of every user.
    private static Map<String, Activity> readActivity(Connection connection) throws SQLException {
        var activity = new HashMap<String, Activity>();
        try (var tables = connection.getMetaData().getTables(null, null, SESSIONS_TABLE, null)) {
            if (!tables.next()) {
                return activity;
            }
        }
        try (var ps =
                        connection.prepareStatement(
                                "SELECT uuid, joined_at, left_at, ip FROM " + SESSIONS_TABLE);
                var rs = ps.executeQuery()) {
            while (rs.next()) {
                var session =
                        new Activity(
                                rs.getLong("joined_at"), rs.getLong("left_at"), rs.getLong("ip"));
                activity.merge(
                        rs.getString("uuid").toLowerCase(Locale.ROOT),
                        session,
                        (a, b) -> {
                            var latest = b.left() > a.left() ? b : a;
                            return new Activity(
                                    Math.min(a.joined(), b.joined()), latest.left(), latest.ip());
                        });
            }
        }
        return activity;
    }

    private static @Nullable String ipv4(long ip) {
        if (ip <= 0 || ip > 0xFFFF_FFFFL) {
            return null;
        }
        return (ip >>> 24)
                + "."
                + ((ip >>> 16) & 0xFF)
                + "."
                + ((ip >>> 8) & 0xFF)
                + "."
                + (ip & 0xFF);
    }
}
