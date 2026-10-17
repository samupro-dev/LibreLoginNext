/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

/** Interface for events that can be cancelled to intercept or prevent default processing. */
public interface CancellableEvent {

    /**
     * Sets the cancellation state of the event.
     *
     * @param cancelled true to cancel the event, false otherwise
     */
    void setCancelled(boolean cancelled);

    /**
     * Checks whether the event has been marked as cancelled.
     *
     * @return true if the event is cancelled, false otherwise
     */
    boolean isCancelled();
}
