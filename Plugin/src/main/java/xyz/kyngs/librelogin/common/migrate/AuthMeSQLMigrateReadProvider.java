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
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.common.crypto.LegacyHashNormalizer;
import xyz.kyngs.librelogin.common.database.AuthenticUser;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/** Migration read provider for AuthMeReloaded authentication databases. */
public class AuthMeSQLMigrateReadProvider extends SQLMigrateReadProvider {

    /** AuthMe stores this placeholder when a player has no email (PlayerAuth.DB_EMAIL_DEFAULT). */
    private static final String AUTHME_NO_EMAIL = "your@email.com";

    /**
     * Constructs a new AuthMeReloaded migration provider.
     *
     * @param tableName the AuthMe database table name
     * @param logger the logger instance
     * @param connector the SQL database connector
     */
    public AuthMeSQLMigrateReadProvider(
            String tableName, Logger logger, SQLDatabaseConnector connector) {
        super(tableName, logger, connector);
    }

    private static String getOptionalString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }

    private static Timestamp getOptionalTimestamp(ResultSet rs, String column) {
        try {
            var ts = rs.getTimestamp(column);
            if (ts != null) {
                return ts;
            }
        } catch (SQLException ignored) {
        }
        try {
            var val = rs.getLong(column);
            if (val > 0) {
                if (val < 100_000_000_000L) {
                    val *= 1000L;
                }
                return new Timestamp(val);
            }
        } catch (SQLException ignored) {
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
                        var unsupported = new ArrayList<String>();

                        while (rs.next()) {
                            try {
                                var nickname = getOptionalString(rs, "realname");
                                if (nickname == null || nickname.isEmpty()) {
                                    nickname = getOptionalString(rs, "username");
                                }

                                if (nickname == null || nickname.isEmpty()) {
                                    continue;
                                }

                                var passwordRaw = getOptionalString(rs, "password");
                                var lastSeen = getOptionalTimestamp(rs, "lastlogin");
                                var firstSeen = getOptionalTimestamp(rs, "regdate");
                                var ip = getOptionalString(rs, "ip");
                                var email = getOptionalString(rs, "email");
                                var totpSecret = getOptionalString(rs, "totp");

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
                                        // AuthMe's default SHA256 ($SHA$salt$hash), verified by the
                                        // verify-only SHA-256 provider and re-hashed on next login.
                                        password =
                                                LegacyHashNormalizer.fromAuthMeSha256(passwordRaw);
                                    }

                                    if (password == null) {
                                        // Importing this row without its password would let anyone
                                        // /register the name and take over the account.
                                        unsupported.add(nickname);
                                        continue;
                                    }
                                }

                                if (email != null
                                        && (email.isBlank()
                                                || AUTHME_NO_EMAIL.equalsIgnoreCase(
                                                        email.trim()))) {
                                    // AuthMe's "no email" placeholder must not receive reset
                                    // tokens.
                                    email = null;
                                }

                                users.add(
                                        new AuthenticUser(
                                                GeneralUtil.getCrackedUUIDFromName(nickname),
                                                null,
                                                password,
                                                nickname,
                                                firstSeen,
                                                lastSeen,
                                                totpSecret,
                                                ip,
                                                null,
                                                null,
                                                email));

                            } catch (Exception e) {
                                logger.error(
                                        "Failed to read user from AuthMe database; omitting user.",
                                        e);
                            }
                        }

                        if (!unsupported.isEmpty()) {
                            throw new IllegalStateException(
                                    unsupported.size()
                                            + " AuthMe account(s) use a password hash format"
                                            + " LibreLogin cannot verify (supported: $SHA$, BCrypt,"
                                            + " Argon2). The migration was aborted so these"
                                            + " accounts cannot be claimed by anyone. Affected: "
                                            + String.join(
                                                    ", ",
                                                    unsupported.subList(
                                                            0, Math.min(20, unsupported.size())))
                                            + (unsupported.size() > 20 ? ", ..." : ""));
                        }

                        return users;
                    }
                });
    }
}
