/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.configuration;

/** Thrown when a configuration file cannot be parsed or contains invalid syntax/structures. */
public class CorruptedConfigurationException extends Exception {

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause the underlying cause
     */
    public CorruptedConfigurationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CorruptedConfigurationException(String message) {
        super(message);
    }
}
