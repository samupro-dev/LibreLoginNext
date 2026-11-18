/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config.migrate.config;

import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;
import xyz.kyngs.librelogin.common.config.migrate.ConfigurationMigrator;
import xyz.kyngs.librelogin.common.database.connector.AuthenticMySQLDatabaseConnector;

/**
 * Revision 1 (0.26.4): MySQL {@code jdbc-url} was replaced by host/port/database plus {@code
 * properties}. An untouched default is removed; a customized one is kept, and the connector still
 * uses it with a deprecation warning.
 */
public class RemoveDefaultJdbcUrlMigrator implements ConfigurationMigrator {

    private static final String[] PATHS = {
        "database.properties.mysql.jdbc-url", "migration.old-database.mysql.jdbc-url"
    };

    @Override
    public void migrate(ConfigurateHelper helper, Logger logger) {
        for (var path : PATHS) {
            var node = helper.resolve(path);
            if (node != null
                    && !node.virtual()
                    && node.parent() != null
                    && AuthenticMySQLDatabaseConnector.LEGACY_DEFAULT_JDBC_URL.equals(
                            node.getString())) {
                node.parent().removeChild(node.key());
            }
        }
    }
}
