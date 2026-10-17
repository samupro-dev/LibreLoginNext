/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.connector;

import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;

/**
 * Holds registration information and factory logic for a {@link DatabaseConnector}.
 *
 * @param <E> the type of exception thrown during connector creation or query execution
 * @param <C> the type of database connector being registered
 * @param factory the factory creating the connector given a configuration key prefix
 * @param configClass the class defining configuration keys for this connector, or null if none
 * @param id the unique identifier for this connector type (e.g. "mysql", "sqlite")
 */
public record DatabaseConnectorRegistration<E extends Exception, C extends DatabaseConnector<E, ?>>(
        ThrowableFunction<String, C, E> factory, @Nullable Class<?> configClass, String id) {}
