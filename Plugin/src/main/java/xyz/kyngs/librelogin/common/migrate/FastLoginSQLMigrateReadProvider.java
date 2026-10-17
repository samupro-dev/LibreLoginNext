/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.api.premium.PremiumException;
import xyz.kyngs.librelogin.api.premium.PremiumProvider;
import xyz.kyngs.librelogin.api.premium.PremiumUser;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/**
 * Migration provider for FastLogin databases that migrates premium status and UUID mappings into
 * the LibreLogin database.
 */
public class FastLoginSQLMigrateReadProvider extends SQLMigrateReadProvider {

    /** Bounded: the migration runs during startup and must not wait forever on Mojang. */
    private static final int MAX_THROTTLE_RETRIES = 5;

    private final DatabaseConnector<?, ?> main;
    private final PremiumProvider provider;

    /**
     * Constructs a new FastLogin migration provider.
     *
     * @param tableName the FastLogin database table name
     * @param logger the logger instance
     * @param connector the FastLogin SQL database connector
     * @param main the main LibreLogin database connector
     * @param provider the premium provider for Mojang UUID resolution
     */
    public FastLoginSQLMigrateReadProvider(
            String tableName,
            Logger logger,
            SQLDatabaseConnector connector,
            DatabaseConnector<?, ?> main,
            PremiumProvider provider) {
        super(tableName, logger, connector);
        this.main = main;
        this.provider = provider;
    }

    @Override
    public Collection<User> getAllUsers() {
        Multimap<UUID, String> premiumUsers =
                connector.runQuery(
                        connection -> {
                            Multimap<UUID, String> map = HashMultimap.create();

                            try (var ps =
                                            connection.prepareStatement(
                                                    "SELECT * FROM " + getEscapedTableName());
                                    var rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    try {
                                        if (rs.getInt("Premium") != 1) {
                                            continue;
                                        }

                                        var premiumUUID =
                                                GeneralUtil.fromUnDashedUUID(rs.getString("UUID"));
                                        var name = rs.getString("Name");

                                        map.put(premiumUUID, name);
                                    } catch (Exception e) {
                                        logger.error(
                                                "Error while migrating user from FastLogin db,"
                                                        + " omitting");
                                    }
                                }
                            }

                            return map;
                        });

        for (Map.Entry<UUID, Collection<String>> entry : premiumUsers.asMap().entrySet()) {
            var names = entry.getValue();
            var premiumUUID = entry.getKey();

            if (premiumUUID == null) {
                continue;
            }

            String name = null;

            if (names.size() == 1) {
                name = names.iterator().next();
            } else if (names.size() > 1) {
                logger.warn(
                        "Users %s share the same premium UUID %s, contacting mojang to find the owner"
                                .formatted(Arrays.toString(names.toArray()), premiumUUID));
                PremiumUser user = null;

                for (int attempt = 1; attempt <= MAX_THROTTLE_RETRIES; attempt++) {
                    try {
                        user = provider.getUserForUUID(premiumUUID);
                        break;
                    } catch (PremiumException e) {
                        if (e.getIssue() == PremiumException.Issue.THROTTLED) {
                            logger.warn(
                                    "Request to mojang throttled, waiting for 5 seconds (attempt "
                                            + attempt
                                            + "/"
                                            + MAX_THROTTLE_RETRIES
                                            + ")");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException interruptedException) {
                                throw new RuntimeException(interruptedException);
                            }
                        } else {
                            logger.error("Cannot contact mojang to find the owner, omitting", e);
                            break;
                        }
                    }
                }

                if (user == null) {
                    logger.warn(
                            "No owner found for the premium UUID %s, omitting"
                                    .formatted(premiumUUID));
                    continue;
                }

                for (String s : names) {
                    if (s.equalsIgnoreCase(user.name())) {
                        name = s;
                        break;
                    }
                }

                if (name == null) {
                    logger.error(
                            "Registered names with the premium UUID do not match the mojang name %s, omitting"
                                    .formatted(user.name()));
                    continue;
                } else {
                    logger.info(
                            "Found owner of the premium UUID %s, name %s"
                                    .formatted(premiumUUID, name));
                }
            } else {
                continue;
            }

            if (main instanceof SQLDatabaseConnector sqlMain) {
                String finalName = name;
                try {
                    sqlMain.runQuery(
                            connection2 -> {
                                try (var ps2 =
                                        connection2.prepareStatement(
                                                "UPDATE librepremium_data SET premium_uuid=? WHERE"
                                                        + " last_nickname=?")) {
                                    ps2.setString(1, premiumUUID.toString());
                                    ps2.setString(2, finalName);
                                    ps2.executeUpdate();
                                }
                            });
                } catch (RuntimeException e) {
                    // e.g. the premium UUID is already bound to another account
                    logger.error(
                            "Could not mark %s as premium (%s): %s"
                                    .formatted(finalName, premiumUUID, e.getMessage()));
                }
            }
        }

        return List.of();
    }
}
