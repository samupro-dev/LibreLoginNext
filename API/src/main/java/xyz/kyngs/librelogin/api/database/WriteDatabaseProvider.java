/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.database;

import java.util.Collection;

/**
 * Write-only interface for creating, modifying, and deleting user account records in a persistent
 * store.
 */
public interface WriteDatabaseProvider {

    /**
     * Inserts a new user account into the database.
     *
     * @param user the user model to insert
     */
    void insertUser(User user);

    /**
     * Bulk inserts a collection of user accounts into the database.
     *
     * @param users the collection of users to insert
     */
    void insertUsers(Collection<User> users);

    /**
     * Persists updates to an existing user account record in the database.
     *
     * @param user the user model with updated fields
     */
    void updateUser(User user);

    /**
     * Deletes a user account record from the database.
     *
     * @param user the user model to remove
     */
    void deleteUser(User user);
}
