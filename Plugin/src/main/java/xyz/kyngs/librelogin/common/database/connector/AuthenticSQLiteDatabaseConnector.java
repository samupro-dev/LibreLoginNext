/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.connector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import xyz.kyngs.librelogin.api.database.connector.SQLiteDatabaseConnector;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

/** SQLite database connector backed by a {@link HikariDataSource} connection pool. */
public class AuthenticSQLiteDatabaseConnector
        extends AuthenticDatabaseConnector<SQLException, Connection>
        implements SQLiteDatabaseConnector {

    private final HikariConfig hikariConfig;
    private HikariDataSource dataSource;

    /**
     * Constructs a new SQLite connector configured with the database file path relative to the
     * plugin's data folder.
     *
     * @param plugin the plugin instance
     * @param prefix the configuration key prefix
     */
    public AuthenticSQLiteDatabaseConnector(AuthenticLibreLogin<?, ?> plugin, String prefix) {
        super(plugin, prefix);

        this.hikariConfig = new HikariConfig();

        hikariConfig.setPoolName("LibreLogin Next SQLite Pool");
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        // SQLite has a single writer: one pooled connection. Suited to small servers; use
        // MySQL/MariaDB or PostgreSQL for large ones.
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMaxLifetime(60000);
        // One local connection needs no keepalive; HikariCP's 2 minute default would exceed the
        // 60 s lifetime and be disabled with a warning.
        hikariConfig.setKeepaliveTime(0);
        hikariConfig.setConnectionTimeout(5000);

        hikariConfig.addDataSourceProperty("busy_timeout", "5000");
        hikariConfig.addDataSourceProperty("journal_mode", "WAL");
        hikariConfig.addDataSourceProperty("synchronous", "NORMAL");

        hikariConfig.setJdbcUrl(
                "jdbc:sqlite:"
                        + plugin.getDataFolder().getAbsolutePath()
                        + "/"
                        + get(Configuration.PATH));
    }

    @Override
    public void connect() throws SQLException {
        dataSource = new HikariDataSource(hikariConfig);
        try (var connection = dataSource.getConnection()) {
            // Verify connection
        } catch (SQLException e) {
            dataSource.close();
            throw e;
        }
        connected = true;
    }

    @Override
    public void disconnect() throws SQLException {
        connected = false;
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public Connection obtainInterface() throws SQLException, IllegalStateException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new IllegalStateException("Database pool is closed!");
        }
        return dataSource.getConnection();
    }

    @Override
    public <V> V runQuery(ThrowableFunction<Connection, V, SQLException> function)
            throws IllegalStateException {
        try {
            try (var connection = obtainInterface()) {
                var result = function.apply(connection);
                recordQuerySuccess();
                return result;
            }
        } catch (SQLTransientConnectionException e) {
            recordQueryFailure(e);
            throw new RuntimeException("Transient connection error to database", e);
        } catch (SQLException e) {
            recordQueryFailure(e);
            throw new RuntimeException(e);
        }
    }

    /** Configuration keys specific to SQLite database connections. */
    public static final class Configuration {

        public static final ConfigurationKey<String> PATH =
                new ConfigurationKey<>(
                        "path",
                        "user-data.db",
                        "Path to SQLite database file. Relative to plugin datafolder.",
                        ConfigurateHelper::getString);

        private Configuration() {}
    }
}
