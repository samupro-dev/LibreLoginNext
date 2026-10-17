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
import xyz.kyngs.librelogin.api.database.connector.MySQLDatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.database.connector.AuthenticMySQLDatabaseConnector;

/** MySQL-specific implementation of {@link LibreLoginSQLDatabaseProvider}. */
public class LibreLoginMySQLDatabaseProvider extends LibreLoginSQLDatabaseProvider {

    /**
     * Constructs a new MySQL database provider.
     *
     * @param connector the MySQL database connector
     * @param plugin the plugin instance
     */
    public LibreLoginMySQLDatabaseProvider(
            MySQLDatabaseConnector connector, AuthenticLibreLogin<?, ?> plugin) {
        super(connector, plugin);
    }

    @Override
    protected List<String> getColumnNames(Connection connection) throws SQLException {
        var dbName =
                ((AuthenticMySQLDatabaseConnector) connector)
                        .get(AuthenticMySQLDatabaseConnector.Configuration.NAME);
        var sql =
                "SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE"
                        + " TABLE_NAME='librepremium_data' AND TABLE_SCHEMA=?";
        var columns = new ArrayList<String>();
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, dbName);
            try (var resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("column_name"));
                }
            }
        }
        return columns;
    }

    @Override
    protected String getIgnoreSyntax() {
        return "IGNORE";
    }

    @Override
    protected String addUnique(String column) {
        return "CREATE UNIQUE INDEX %s_index ON librepremium_data(%s)".formatted(column, column);
    }

    @Override
    protected String addIndex(String indexName, String column) {
        return "CREATE INDEX %s ON librepremium_data(%s)".formatted(indexName, column);
    }

    // null until detected: whether last_nickname uses a case-insensitive collation.
    private volatile Boolean caseInsensitiveNickname;

    @Override
    protected String nicknameLookupQuery() {
        var ci = caseInsensitiveNickname;
        if (ci == null) {
            ci = detectCaseInsensitiveNickname();
            caseInsensitiveNickname = ci;
        }
        // LOWER(col) cannot use an index in MySQL/MariaDB. With a case-insensitive (*_ci)
        // collation a plain comparison is equivalent and uses the unique index on last_nickname.
        return ci
                ? "SELECT * FROM librepremium_data WHERE last_nickname=?"
                : super.nicknameLookupQuery();
    }

    @Override
    protected String addNicknameLookupIndex() {
        // MariaDB has no expression indexes; the unique index is used through the collation above.
        return null;
    }

    private boolean detectCaseInsensitiveNickname() {
        try {
            return connector.runQuery(
                    connection -> {
                        try (var ps =
                                        connection.prepareStatement(
                                                "SELECT COLLATION_NAME FROM"
                                                    + " information_schema.COLUMNS WHERE"
                                                    + " TABLE_SCHEMA = DATABASE() AND TABLE_NAME ="
                                                    + " 'librepremium_data' AND COLUMN_NAME ="
                                                    + " 'last_nickname'");
                                var rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                return false;
                            }
                            var collation = rs.getString(1);
                            return collation != null
                                    && collation.toLowerCase(java.util.Locale.ROOT).endsWith("_ci");
                        }
                    });
        } catch (RuntimeException e) {
            return false;
        }
    }
}
