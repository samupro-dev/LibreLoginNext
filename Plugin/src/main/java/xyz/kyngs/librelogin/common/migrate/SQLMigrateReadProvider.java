/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;

/** Base abstract class for SQL-based migration read providers. */
public abstract class SQLMigrateReadProvider extends MigrateReadProvider {

    protected final String tableName;
    protected final Logger logger;
    protected final SQLDatabaseConnector connector;

    /**
     * Constructs a new SQL migration read provider.
     *
     * @param tableName the legacy table name to read users from
     * @param logger the logger instance
     * @param connector the SQL database connector
     */
    public SQLMigrateReadProvider(String tableName, Logger logger, SQLDatabaseConnector connector) {
        this.tableName = tableName;
        this.logger = logger;
        this.connector = connector;
    }

    /**
     * Returns the table name safely quoted for standard SQL queries.
     *
     * @return the escaped SQL table identifier
     */
    protected String getEscapedTableName() {
        if (tableName == null) return "users";
        if (tableName.startsWith("`") || tableName.startsWith("\"") || tableName.startsWith("[")) {
            return tableName;
        }
        if (connector
                instanceof
                xyz.kyngs.librelogin.api.database.connector.PostgreSQLDatabaseConnector) {
            return "\"" + tableName.replace("\"", "\"\"") + "\"";
        }
        return "`" + tableName.replace("`", "``") + "`";
    }
}
