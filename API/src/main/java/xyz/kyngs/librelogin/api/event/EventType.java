/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

import java.util.Objects;

/**
 * Type token identifying a specific event class for subscription and dispatch.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 * @param <E> the event class type
 */
public class EventType<P, S, E extends Event<P, S>> {

    private final Class<?> clazz;

    EventType(Class<?> clazz) {
        this.clazz = clazz;
    }

    /**
     * Gets the runtime event class represented by this type token.
     *
     * @return the event class
     */
    public Class<?> getClazz() {
        return clazz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventType<?, ?, ?> eventType = (EventType<?, ?, ?>) o;
        return Objects.equals(clazz, eventType.clazz);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz);
    }
}
