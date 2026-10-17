/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.common.database.AuthenticUser;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/** Migration read provider for LoginTo authentication databases. */
public class LoginToSQLMigrateReadProvider extends SQLMigrateReadProvider {

    /**
     * Constructs a new LoginTo migration provider.
     *
     * @param tableName the LoginTo database table name
     * @param logger the logger instance
     * @param connector the SQL database connector
     */
    public LoginToSQLMigrateReadProvider(
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

    private static boolean isPremium(ResultSet rs) {
        try {
            return rs.getBoolean("isPremium");
        } catch (SQLException e) {
            return false;
        }
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
                        var nameless = 0;
                        var unsupported = new ArrayList<String>();

                        while (rs.next()) {
                            try {
                                var nickname =
                                        getOptionalString(rs, "name", "username", "player_name");
                                var uuidStr = getOptionalString(rs, "uuid", "player_uuid");

                                UUID uuid = null;
                                if (uuidStr != null) {
                                    try {
                                        uuid =
                                                UUID.fromString(
                                                        uuidStr.replace("-", "").length() == 32
                                                                ? GeneralUtil.fromUnDashedUUID(
                                                                                uuidStr)
                                                                        .toString()
                                                                : uuidStr);
                                    } catch (Exception ignored) {
                                    }
                                }

                                if (uuid == null && nickname != null) {
                                    uuid = GeneralUtil.getCrackedUUIDFromName(nickname);
                                }

                                if (uuid == null) {
                                    continue;
                                }

                                if (nickname == null || nickname.isEmpty()) {
                                    // LoginTo 4.x stores no player names. A cracked account cannot
                                    // be
                                    // mapped to a name, and leaving it out would let anyone
                                    // register
                                    // that name and inherit its offline-UUID data.
                                    nameless++;
                                    continue;
                                }

                                var passwordRaw = getOptionalString(rs, "password", "hash");
                                HashedPassword password = null;

                                if (passwordRaw != null && !passwordRaw.isEmpty()) {
                                    if (passwordRaw.startsWith("$2a$")
                                            || passwordRaw.startsWith("$2y$")
                                            || passwordRaw.startsWith("$2b$")) {
                                        password =
                                                new HashedPassword(passwordRaw, null, "BCrypt-2A");
                                    } else if (passwordRaw.startsWith("$argon2")) {
                                        password =
                                                new HashedPassword(passwordRaw, null, "Argon-2ID");
                                    } else {
                                        unsupported.add(nickname);
                                        continue;
                                    }
                                }

                                var onlineUuidStr =
                                        getOptionalString(rs, "online_uuid", "premium_uuid");
                                UUID premiumUuid = null;
                                if (onlineUuidStr != null) {
                                    try {
                                        premiumUuid = UUID.fromString(onlineUuidStr);
                                    } catch (Exception ignored) {
                                    }
                                }
                                if (premiumUuid == null && isPremium(rs)) {
                                    // LoginTo keys premium players by their online UUID.
                                    premiumUuid = uuid;
                                }

                                var lastSeen =
                                        getOptionalTimestamp(
                                                rs, "last_seen", "last_login", "lastlogin");
                                var joinDate =
                                        getOptionalTimestamp(
                                                rs, "reg_date", "registration_date", "regdate");
                                var lastIp = getOptionalString(rs, "ip", "last_ip", "lastip");
                                var totpSecret =
                                        getOptionalString(
                                                rs, "totp_secret", "totp", "secret", "otp_secret");
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
                                                null,
                                                null,
                                                email));

                            } catch (Exception e) {
                                logger.error(
                                        "Failed to read user from LoginTo database; omitting user.",
                                        e);
                            }
                        }

                        if (nameless > 0 || !unsupported.isEmpty()) {
                            throw new IllegalStateException(
                                    "LoginTo migration aborted so no account becomes claimable: "
                                            + nameless
                                            + " row(s) have no player name (LoginTo 4.x does not"
                                            + " store names), "
                                            + unsupported.size()
                                            + " row(s) use a password hash LibreLogin cannot verify"
                                            + (unsupported.isEmpty()
                                                    ? ""
                                                    : " ("
                                                            + String.join(
                                                                    ", ",
                                                                    unsupported.subList(
                                                                            0,
                                                                            Math.min(
                                                                                    20,
                                                                                    unsupported
                                                                                            .size())))
                                                            + ")")
                                            + ". Only tables that include a name column can be"
                                            + " migrated.");
                        }

                        return users;
                    }
                });
    }
}
