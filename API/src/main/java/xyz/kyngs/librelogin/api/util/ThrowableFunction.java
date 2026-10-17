/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.util;

/**
 * Functional interface for a single-argument function that can throw a checked exception.
 *
 * @param <T> the type of the input argument
 * @param <V> the type of the result
 * @param <E> the type of exception that may be thrown
 * @see java.util.function.Function
 */
@FunctionalInterface
public interface ThrowableFunction<T, V, E extends Throwable> {

    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     * @throws E if an error occurs during execution
     */
    V apply(T t) throws E;
}
