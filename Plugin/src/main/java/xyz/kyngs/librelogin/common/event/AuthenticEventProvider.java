/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.event;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import xyz.kyngs.librelogin.api.event.Event;
import xyz.kyngs.librelogin.api.event.EventProvider;
import xyz.kyngs.librelogin.api.event.EventType;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;

/**
 * Standard implementation of the {@link EventProvider} event bus. Manages event subscriptions and
 * dispatches events synchronously to registered consumer listeners.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticEventProvider<P, S> extends AuthenticHandler<P, S>
        implements EventProvider<P, S> {

    private final Map<EventType<P, S, ?>, Set<Consumer<Event<P, S>>>> listeners;

    /**
     * Constructs a new event provider bound to the specified plugin instance.
     *
     * @param plugin the plugin instance
     */
    public AuthenticEventProvider(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event<P, S>> Consumer<E> subscribe(
            EventType<P, S, E> type, Consumer<E> handler) {
        listeners
                .computeIfAbsent(type, x -> new CopyOnWriteArraySet<>())
                .add((Consumer<Event<P, S>>) handler);
        return handler;
    }

    @Override
    public void unsubscribe(Consumer<? extends Event<P, S>> handler) {
        listeners.values().forEach(x -> x.remove(handler));
    }

    @Override
    public <E extends Event<P, S>> void fire(EventType<P, S, E> type, E event) {
        var set = listeners.get(type);

        if (set == null || set.isEmpty()) return;

        for (Consumer<Event<P, S>> consumer : set) {
            try {
                consumer.accept(event);
            } catch (Throwable t) {
                plugin.getLogger()
                        .error(
                                "Error occurred while dispatching event "
                                        + type
                                        + " to listener "
                                        + consumer,
                                t);
            }
        }
    }

    /**
     * Fires an event without compile-time generic type enforcement.
     *
     * @param type the type of event being fired
     * @param event the event instance
     */
    @SuppressWarnings("unchecked")
    public void unsafeFire(EventType<?, ?, ?> type, Event<?, ?> event) {
        var set = listeners.get(type);

        if (set == null || set.isEmpty()) return;

        for (Consumer<Event<P, S>> consumer : set) {
            try {
                consumer.accept((Event<P, S>) event);
            } catch (Throwable t) {
                plugin.getLogger()
                        .error(
                                "Error occurred while dispatching event "
                                        + type
                                        + " to listener "
                                        + consumer,
                                t);
            }
        }
    }
}
