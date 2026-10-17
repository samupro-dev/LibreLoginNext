/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.exception;

/** Thrown when an operation is aborted because a corresponding cancellable event was cancelled. */
public class EventCancelledException extends RuntimeException {

    /** Constructs a new EventCancelledException without a detail message. */
    public EventCancelledException() {
        super();
    }

    /**
     * Constructs a new EventCancelledException with the specified detail message.
     *
     * @param message the detail message
     */
    public EventCancelledException(String message) {
        super(message);
    }
}
