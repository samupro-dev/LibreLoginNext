/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.util;

/**
 * Functional interface for an operation that accepts a single input argument and can throw a
 * checked exception.
 *
 * @param <T> the type of the input argument
 * @param <E> the type of exception that may be thrown
 * @see java.util.function.Consumer
 */
@FunctionalInterface
public interface ThrowableConsumer<T, E extends Throwable> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     * @throws E if an error occurs during execution
     */
    void accept(T t) throws E;
}
