/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config.migrate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;
import xyz.kyngs.librelogin.common.database.connector.AuthenticMySQLDatabaseConnector;

class RemoveDefaultJdbcUrlMigratorTest {

    @Test
    void removesOnlyTheUntouchedDefault() {
        var root = CommentedConfigurationNode.root();
        var current = root.node("database", "properties", "mysql", "jdbc-url");
        current.raw(AuthenticMySQLDatabaseConnector.LEGACY_DEFAULT_JDBC_URL);
        var custom = "jdbc:mariadb://%host%:%port%/%database%?useSSL=true";
        root.node("migration", "old-database", "mysql", "jdbc-url").raw(custom);

        new RemoveDefaultJdbcUrlMigrator().migrate(new ConfigurateHelper(root), null);

        assertTrue(root.node("database", "properties", "mysql", "jdbc-url").virtual());
        assertEquals(
                custom, root.node("migration", "old-database", "mysql", "jdbc-url").getString());
    }
}
