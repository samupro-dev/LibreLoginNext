/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.provider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import xyz.kyngs.librelogin.api.database.connector.SQLiteDatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;

/** SQLite-specific implementation of {@link LibreLoginSQLDatabaseProvider}. */
public class LibreLoginSQLiteDatabaseProvider extends LibreLoginSQLDatabaseProvider {

    /**
     * Constructs a new SQLite database provider.
     *
     * @param connector the SQLite database connector
     * @param plugin the plugin instance
     */
    public LibreLoginSQLiteDatabaseProvider(
            SQLiteDatabaseConnector connector, AuthenticLibreLogin<?, ?> plugin) {
        super(connector, plugin);
    }

    @Override
    protected List<String> getColumnNames(Connection connection) throws SQLException {
        var columns = new ArrayList<String>();
        try (var ps = connection.prepareStatement("PRAGMA table_info(librepremium_data)");
                var rs = ps.executeQuery()) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    @Override
    protected String getIgnoreSyntax() {
        return "OR IGNORE";
    }

    @Override
    protected String addUnique(String column) {
        return "CREATE UNIQUE INDEX %s_index ON librepremium_data(%s);".formatted(column, column);
    }

    @Override
    protected String addIndex(String indexName, String column) {
        return "CREATE INDEX IF NOT EXISTS %s ON librepremium_data(%s);"
                .formatted(indexName, column);
    }
}
