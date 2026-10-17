/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.connector;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

/**
 * Base abstract database connector providing failure tracking, recovery handling, and configuration
 * access.
 *
 * @param <E> the type of exception thrown by this connector
 * @param <I> the interface type used to interact with the database (e.g. {@link
 *     java.sql.Connection})
 */
public abstract class AuthenticDatabaseConnector<E extends Exception, I>
        implements DatabaseConnector<E, I> {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    // HikariCP's default keepalive interval, and the minimum it accepts.
    private static final long DEFAULT_KEEPALIVE_MILLIS = 120_000;
    private static final long MIN_KEEPALIVE_MILLIS = 30_000;
    private static final long PROBE_INTERVAL_MILLIS = 5_000;
    private static final int PROBE_TIMEOUT_SECONDS = 2;

    protected final AuthenticLibreLogin<?, ?> plugin;
    private final String prefix;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    protected volatile boolean connected = false;
    private final AtomicBoolean probing = new AtomicBoolean(false);
    private volatile long nextProbeAt;

    /**
     * Constructs a new database connector instance.
     *
     * @param plugin the plugin instance
     * @param prefix the configuration key prefix for this connector
     */
    public AuthenticDatabaseConnector(AuthenticLibreLogin<?, ?> plugin, String prefix) {
        this.plugin = plugin;
        this.prefix = prefix;
    }

    @Override
    public boolean connected() {
        if (!connected) {
            return false;
        }
        if (consecutiveFailures.get() < MAX_CONSECUTIVE_FAILURES) {
            return true;
        }
        // Half-open: let one caller per interval probe the pool, so the breaker closes again as
        // soon as the database is back instead of rejecting logins until a restart.
        long now = System.currentTimeMillis();
        if (now < nextProbeAt || !probing.compareAndSet(false, true)) {
            return false;
        }
        try {
            if (probe()) {
                recordQuerySuccess();
                plugin.getLogger().info("Database connectivity restored; accepting logins again.");
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().debug("Database recovery probe failed: " + e.getMessage());
        } finally {
            nextProbeAt = System.currentTimeMillis() + PROBE_INTERVAL_MILLIS;
            probing.set(false);
        }
        return false;
    }

    /**
     * Checks whether the database answers. JDBC connectors validate a pooled connection.
     *
     * @return {@code true} if the database is reachable
     * @throws Exception if obtaining the connection fails
     */
    protected boolean probe() throws Exception {
        if (obtainInterface() instanceof Connection connection) {
            try (connection) {
                return connection.isValid(PROBE_TIMEOUT_SECONDS);
            }
        }
        return false;
    }

    /**
     * Records a successful query execution, resetting the consecutive failure counter and marking
     * the connector as connected.
     */
    protected void recordQuerySuccess() {
        consecutiveFailures.set(0);
        connected = true;
    }

    /**
     * Records a query execution failure. If consecutive failures exceed the allowed threshold,
     * marks the connector as disconnected and initiates a graceful proxy shutdown to prevent
     * unauthorized access.
     *
     * @param t the throwable cause of the query failure
     */
    protected void recordQueryFailure(Throwable t) {
        if (isStatementError(t)) {
            // Constraint or syntax errors come from a working connection; they say nothing
            // about connectivity and must not trip the breaker.
            consecutiveFailures.set(0);
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        plugin.getLogger()
                .error(
                        "Database query failure (attempt "
                                + failures
                                + "/"
                                + MAX_CONSECUTIVE_FAILURES
                                + "): "
                                + t.getMessage());
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            nextProbeAt = System.currentTimeMillis() + PROBE_INTERVAL_MILLIS;
            plugin.getLogger()
                    .error(
                            "Database connection degraded after "
                                    + failures
                                    + " consecutive failures! Rejecting new logins; probing for"
                                    + " recovery every "
                                    + (PROBE_INTERVAL_MILLIS / 1000)
                                    + " seconds.");
        }
    }

    private static boolean isStatementError(Throwable t) {
        if (t instanceof SQLException sql) {
            var state = sql.getSQLState();
            // SQLSTATE class 23 = integrity constraint violation, 42 = syntax/access rule.
            return state != null && (state.startsWith("23") || state.startsWith("42"));
        }
        return false;
    }

    /**
     * Retrieves a configuration setting resolved with this connector's configuration prefix.
     *
     * @param <T> the type of configuration value
     * @param key the configuration key definition
     * @return the resolved configuration value, or default value if not specified
     */
    public <T> T get(ConfigurationKey<T> key) {
        var value = key.getter().apply(plugin.getConfiguration().getHelper(), prefix + key.key());
        return value == null ? key.defaultValue() : value;
    }

    /**
     * Returns a keepalive interval that is valid for the given connection lifetime. HikariCP
     * disables, with a warning, a keepalive that is not shorter than the lifetime.
     *
     * @param maxLifetimeMillis the configured maximum connection lifetime
     * @return the keepalive interval in milliseconds, or {@code 0} to disable it
     */
    protected static long keepaliveFor(long maxLifetimeMillis) {
        if (maxLifetimeMillis <= 0 || maxLifetimeMillis > DEFAULT_KEEPALIVE_MILLIS) {
            return DEFAULT_KEEPALIVE_MILLIS;
        }
        long half = maxLifetimeMillis / 2;
        return half >= MIN_KEEPALIVE_MILLIS ? half : 0;
    }
}
