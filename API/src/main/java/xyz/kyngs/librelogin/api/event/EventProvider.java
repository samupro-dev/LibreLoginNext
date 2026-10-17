/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

import java.util.function.Consumer;

/**
 * Event bus interface managing event registration, dispatch, and listeners.
 *
 * <p><b>Threading:</b> handlers may be called on any thread: the server main thread (Paper join),
 * proxy event threads (Velocity), LibreLogin worker threads or Redis client threads. Handlers must
 * be thread-safe, and code that touches main-thread-only platform state (for example Bukkit
 * entities or worlds) must schedule itself onto the main thread.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface EventProvider<P, S> {

    /**
     * Gets the registry of standard event types.
     *
     * @return the event types container
     */
    default EventTypes<P, S> getTypes() {
        return new EventTypes<>();
    }

    /**
     * Subscribes a listener consumer to an event type.
     *
     * @param type the event type token (see {@link #getTypes()})
     * @param handler the callback handler to execute when the event fires
     * @param <E> the event class type
     * @return the handler instance passed as argument, useful for unsubscribing
     */
    <E extends Event<P, S>> Consumer<E> subscribe(EventType<P, S, E> type, Consumer<E> handler);

    /**
     * Unsubscribes a previously registered event listener.
     *
     * @param handler the exact handler instance passed to {@link #subscribe(EventType, Consumer)}
     */
    void unsubscribe(Consumer<? extends Event<P, S>> handler);

    /**
     * Dispatches and fires an event to all subscribed listeners.
     *
     * @param type the event type token
     * @param event the event instance being fired
     * @param <E> the event class type
     */
    <E extends Event<P, S>> void fire(EventType<P, S, E> type, E event);
}
