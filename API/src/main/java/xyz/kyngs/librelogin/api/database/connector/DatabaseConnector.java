/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.database.connector;

import xyz.kyngs.librelogin.api.util.ThrowableConsumer;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;

/**
 * Service provider interface for managing lifecycle and execution against database connections.
 *
 * @param <E> the common exception type thrown by database operations
 * @param <I> the connection/session handle type (e.g. {@link java.sql.Connection})
 */
public interface DatabaseConnector<E extends Exception, I> {

    /**
     * Initializes and opens the database connection pool or session.
     *
     * @throws E if an error occurs while establishing the connection
     */
    void connect() throws E;

    /**
     * Checks whether the connector is active and connected.
     *
     * @return true if connected, false otherwise
     */
    boolean connected();

    /**
     * Closes connections and releases associated resources.
     *
     * @throws E if an error occurs while disconnecting
     */
    void disconnect() throws E;

    /**
     * Obtains a raw connection interface. Callers are responsible for closing it after use.
     *
     * @return the raw connection/interface object
     * @throws E if an error occurs obtaining the connection
     * @throws IllegalStateException if the connector is not currently connected
     */
    I obtainInterface() throws E, IllegalStateException;

    /**
     * Executes a query or transactional operation and handles automatic closing of the interface.
     *
     * @param function the function to execute with the obtained interface
     * @param <V> the return type of the function
     * @return the computation result
     * @throws IllegalStateException if the database is not connected
     */
    <V> V runQuery(ThrowableFunction<I, V, E> function) throws IllegalStateException;

    /**
     * Executes a query or statement without a return value, automatically closing the interface
     * afterwards.
     *
     * @param consumer the consumer to execute with the obtained interface
     * @throws IllegalStateException if the database is not connected
     */
    default void runQuery(ThrowableConsumer<I, E> consumer) throws IllegalStateException {
        runQuery(
                i -> {
                    consumer.accept(i);
                    return null;
                });
    }
}
