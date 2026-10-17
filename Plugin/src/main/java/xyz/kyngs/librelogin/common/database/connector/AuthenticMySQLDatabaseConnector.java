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
import xyz.kyngs.librelogin.api.database.connector.MySQLDatabaseConnector;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

/** MySQL/MariaDB database connector backed by a {@link HikariDataSource} connection pool. */
public class AuthenticMySQLDatabaseConnector
        extends AuthenticDatabaseConnector<SQLException, Connection>
        implements MySQLDatabaseConnector {

    private final HikariConfig hikariConfig;
    private HikariDataSource dataSource;

    /**
     * Constructs a new MySQL connector with pool configurations loaded from the plugin
     * configuration.
     *
     * @param plugin the plugin instance
     * @param prefix the configuration key prefix
     */
    public AuthenticMySQLDatabaseConnector(AuthenticLibreLogin<?, ?> plugin, String prefix) {
        super(plugin, prefix);

        this.hikariConfig = new HikariConfig();

        hikariConfig.setPoolName("LibreLogin Next MySQL Pool");
        hikariConfig.setDriverClassName("xyz.kyngs.librelogin.lib.mariadb.jdbc.Driver");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.setUsername(get(Configuration.USER));
        hikariConfig.setPassword(get(Configuration.PASSWORD));
        hikariConfig.setJdbcUrl(
                get(Configuration.JDBC_URL)
                        .replace("%host%", get(Configuration.HOST))
                        .replace("%port%", String.valueOf(get(Configuration.PORT)))
                        .replace("%database%", get(Configuration.NAME)));
        hikariConfig.setMaximumPoolSize(get(Configuration.POOL_SIZE));
        hikariConfig.setMaxLifetime(get(Configuration.MAX_LIFE_TIME));
        hikariConfig.setKeepaliveTime(keepaliveFor(get(Configuration.MAX_LIFE_TIME)));
        hikariConfig.setConnectionTimeout(get(Configuration.CONNECTION_TIMEOUT));
        hikariConfig.setLeakDetectionThreshold(get(Configuration.LEAK_DETECTION_THRESHOLD));
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

    /** Configuration keys specific to MySQL database connections. */
    public static final class Configuration {

        public static final ConfigurationKey<String> HOST =
                new ConfigurationKey<>(
                        "host",
                        "localhost",
                        "The host of the database.",
                        ConfigurateHelper::getString);

        public static final ConfigurationKey<String> NAME =
                new ConfigurationKey<>(
                        "database",
                        "librelogin",
                        "The name of the database.",
                        ConfigurateHelper::getString);

        public static final ConfigurationKey<String> PASSWORD =
                new ConfigurationKey<>(
                        "password",
                        "",
                        "The password of the database.",
                        ConfigurateHelper::getString);

        public static final ConfigurationKey<Integer> PORT =
                new ConfigurationKey<>(
                        "port", 3306, "The port of the database.", ConfigurateHelper::getInt);

        public static final ConfigurationKey<String> USER =
                new ConfigurationKey<>(
                        "user", "root", "The user of the database.", ConfigurateHelper::getString);

        public static final ConfigurationKey<Integer> MAX_LIFE_TIME =
                new ConfigurationKey<>(
                        "max-life-time",
                        600000,
                        "The maximum lifetime of a database connection in milliseconds. Don't touch"
                                + " this if you don't know what you're doing.",
                        ConfigurateHelper::getInt);

        public static final ConfigurationKey<String> JDBC_URL =
                new ConfigurationKey<>(
                        "jdbc-url",
                        "jdbc:mariadb://%host%:%port%/%database%",
                        "The JDBC URL of the database. Don't touch this if you don't know what"
                                + " you're doing. (Using jdbc:mariadb also works for pure mysql)",
                        ConfigurateHelper::getString);

        public static final ConfigurationKey<Integer> POOL_SIZE =
                new ConfigurationKey<>(
                        "pool-size",
                        10,
                        "The maximum pool size of database connections.",
                        ConfigurateHelper::getInt);

        public static final ConfigurationKey<Integer> CONNECTION_TIMEOUT =
                new ConfigurationKey<>(
                        "connection-timeout",
                        30000,
                        "The connection timeout of the database in milliseconds.",
                        ConfigurateHelper::getInt);

        public static final ConfigurationKey<Integer> LEAK_DETECTION_THRESHOLD =
                new ConfigurationKey<>(
                        "leak-detection-threshold",
                        10000,
                        "The leak detection threshold of database connections in milliseconds.",
                        ConfigurateHelper::getInt);

        private Configuration() {}
    }
}
