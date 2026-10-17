/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.common.crypto.LegacyHashNormalizer;
import xyz.kyngs.librelogin.common.database.AuthenticUser;

/** Migration read provider for existing LibreLogin / LibreLoginProd databases. */
public class LibreLoginSQLMigrateReadProvider extends SQLMigrateReadProvider {

    /**
     * Constructs a new LibreLogin migration provider.
     *
     * @param tableName the LibreLogin database table name
     * @param logger the logger instance
     * @param connector the SQL database connector
     */
    public LibreLoginSQLMigrateReadProvider(
            String tableName, Logger logger, SQLDatabaseConnector connector) {
        super(tableName, logger, connector);
    }

    private static String getOptionalString(ResultSet rs, String... columns) {
        for (String col : columns) {
            try {
                var val = rs.getString(col);
                if (val != null && !val.isEmpty()) {
                    return val;
                }
            } catch (SQLException ignored) {
            }
        }
        return null;
    }

    private static Timestamp getOptionalTimestamp(ResultSet rs, String... columns) {
        for (String col : columns) {
            try {
                var val = rs.getTimestamp(col);
                if (val != null) {
                    return val;
                }
            } catch (SQLException ignored) {
            }
            try {
                var millis = rs.getLong(col);
                if (millis > 0) {
                    if (millis < 100_000_000_000L) {
                        millis *= 1000L;
                    }
                    return new Timestamp(millis);
                }
            } catch (SQLException ignored) {
            }
        }
        return null;
    }

    @Override
    public Collection<User> getAllUsers() {
        return connector.runQuery(
                connection -> {
                    try (var ps =
                                    connection.prepareStatement(
                                            "SELECT * FROM " + getEscapedTableName());
                            var rs = ps.executeQuery()) {
                        var users = new HashSet<User>();

                        while (rs.next()) {
                            try {
                                var uuidStr = getOptionalString(rs, "uuid");
                                if (uuidStr == null || uuidStr.isEmpty()) {
                                    continue;
                                }

                                var uuid = UUID.fromString(uuidStr);
                                var premiumUuidStr = getOptionalString(rs, "premium_uuid");
                                var premiumUuid =
                                        premiumUuidStr != null && !premiumUuidStr.isEmpty()
                                                ? UUID.fromString(premiumUuidStr)
                                                : null;

                                var passwordHash =
                                        getOptionalString(rs, "hashed_password", "password");
                                var passwordType = getOptionalString(rs, "algo", "password_type");
                                var salt = getOptionalString(rs, "salt");
                                HashedPassword password = null;

                                if (passwordHash != null && !passwordHash.isEmpty()) {
                                    if (passwordType == null || passwordType.isEmpty()) {
                                        passwordType =
                                                passwordHash.startsWith("$argon2")
                                                        ? "Argon-2ID"
                                                        : "BCrypt-2A";
                                    }
                                    password =
                                            LegacyHashNormalizer.normalize(
                                                    new HashedPassword(
                                                            passwordHash, salt, passwordType));
                                }

                                var nickname =
                                        getOptionalString(
                                                rs, "last_nickname", "nickname", "username");
                                var joinDate =
                                        getOptionalTimestamp(rs, "joined", "join_date", "regdate");
                                var lastSeen =
                                        getOptionalTimestamp(
                                                rs, "last_seen", "last_login", "lastlogin");
                                var totpSecret =
                                        getOptionalString(rs, "secret", "totp_secret", "totp");
                                var lastIp = getOptionalString(rs, "ip", "last_ip", "lastip");
                                var lastAuth =
                                        getOptionalTimestamp(
                                                rs, "last_authentication", "last_auth");
                                var lastServer = getOptionalString(rs, "last_server");
                                var email = getOptionalString(rs, "email");

                                users.add(
                                        new AuthenticUser(
                                                uuid,
                                                premiumUuid,
                                                password,
                                                nickname,
                                                joinDate,
                                                lastSeen,
                                                totpSecret,
                                                lastIp,
                                                lastAuth,
                                                lastServer,
                                                email));

                            } catch (Exception e) {
                                logger.error(
                                        "Failed to read user from LibreLogin database; omitting"
                                                + " user.",
                                        e);
                            }
                        }

                        return users;
                    }
                });
    }
}
