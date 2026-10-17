/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database;

import xyz.kyngs.librelogin.api.database.ReadWriteDatabaseProvider;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;

/**
 * Base abstract implementation of {@link ReadWriteDatabaseProvider} that manages an underlying
 * {@link DatabaseConnector}.
 *
 * @param <C> the type of database connector utilized by this provider
 */
public abstract class AuthenticDatabaseProvider<C extends DatabaseConnector<?, ?>>
        implements ReadWriteDatabaseProvider {

    protected final C connector;
    protected final AuthenticLibreLogin<?, ?> plugin;

    /**
     * Constructs a new database provider instance.
     *
     * @param connector the database connector to interact with the underlying database
     * @param plugin the plugin instance
     */
    protected AuthenticDatabaseProvider(C connector, AuthenticLibreLogin<?, ?> plugin) {
        this.connector = connector;
        this.plugin = plugin;
    }

    /**
     * Returns the database connector used by this provider.
     *
     * @return the database connector
     */
    public C getConnector() {
        return connector;
    }

    @Override
    public boolean connected() {
        return connector != null && connector.connected();
    }

    /**
     * Validates and initializes the database schema if necessary. Subclasses should override this
     * method to create required tables and execute migrations.
     */
    public void validateSchema() {}

    /**
     * Purges accounts inactive for longer than the specified number of days.
     *
     * @param days the retention threshold in days
     * @return the number of purged user accounts
     */
    public int purgeInactiveUsers(int days) {
        return 0;
    }

    /**
     * Deletes rows that pre-login created for names which never registered, never logged in and are
     * not premium (for example bot joins), once they are older than the given age.
     *
     * @param days minimum age of the row in days
     * @return the number of deleted rows
     */
    /**
     * Removes a user, keeping a copy for recovery where the backend supports it (SQL backends copy
     * the row into {@code librepremium_data_archive}).
     *
     * @param user the user to archive and remove
     */
    public void archiveUser(xyz.kyngs.librelogin.api.database.User user) {
        deleteUser(user);
    }

    public int purgeAbandonedUsers(int days) {
        return 0;
    }

    /**
     * Returns the estimated number of currently cached user entities in memory.
     *
     * @return count of cached users
     */
    public long getCachedUsersCount() {
        return 0;
    }
}
