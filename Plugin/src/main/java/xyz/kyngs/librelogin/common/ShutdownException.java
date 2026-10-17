/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common;

/**
 * Thrown when the server is deliberately stopped during plugin initialization (fatal configuration
 * or database error). It stops {@code enable()} on every platform.
 */
public class ShutdownException extends RuntimeException {

    public ShutdownException() {
        super();
    }

    public ShutdownException(String message) {
        super(message);
    }

    public ShutdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
