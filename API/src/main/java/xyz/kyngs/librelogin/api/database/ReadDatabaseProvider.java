/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.database;

import java.util.Collection;
import java.util.UUID;

/** Read-only interface for querying user account records from a persistent store. */
public interface ReadDatabaseProvider {

    /**
     * Looks up a user account by their username.
     *
     * @param name the username to search for
     * @return the matching User instance, or null if not found
     */
    User getByName(String name);

    /**
     * Looks up a user account by their unique identifier.
     *
     * @param uuid the UUID of the user
     * @return the matching User instance, or null if not found
     */
    User getByUUID(UUID uuid);

    /**
     * Looks up a user account by their linked premium Mojang UUID.
     *
     * @param uuid the premium UUID of the user
     * @return the matching User instance, or null if not found
     */
    User getByPremiumUUID(UUID uuid);

    /**
     * Retrieves all user account records stored in the database.
     *
     * @return a collection of all users
     */
    Collection<User> getAllUsers();

    /**
     * Retrieves all user accounts associated with a specific IP address.
     *
     * @param ip the IP address to search for
     * @return a collection of matching users
     */
    Collection<User> getByIP(String ip);

    /**
     * Checks whether the underlying database connection is active and responsive.
     *
     * @return true if connected, false otherwise
     */
    default boolean connected() {
        return true;
    }
}
