/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.database;

import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;

/**
 * Encapsulates the registration metadata and factory for a {@link ReadDatabaseProvider}.
 *
 * @param factory the factory used to instantiate the provider from a connector
 * @param id the unique identifier of the provider (e.g. "librelogin-mysql", "authme-sqlite")
 * @param databaseConnector the connector class required by the factory, or null if no connector is
 *     needed
 * @param <R> the provider type
 * @param <C> the connector type
 * @param <E> the exception type thrown by the provider during creation
 */
public record ReadDatabaseProviderRegistration<
        R extends ReadDatabaseProvider, C extends DatabaseConnector<E, ?>, E extends Exception>(
        ThrowableFunction<C, R, E> factory, String id, @Nullable Class<C> databaseConnector) {

    /**
     * Creates a new instance of the database provider using the given database connector.
     *
     * @param connector the database connector to supply to the factory
     * @return the instantiated database provider
     * @throws E if an error occurs during provider instantiation
     */
    @SuppressWarnings("unchecked")
    public R create(DatabaseConnector<?, ?> connector) throws E {
        return factory.apply((C) connector);
    }
}
