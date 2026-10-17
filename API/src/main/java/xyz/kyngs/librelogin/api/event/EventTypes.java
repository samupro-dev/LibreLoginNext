/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.event.events.LimboServerChooseEvent;
import xyz.kyngs.librelogin.api.event.events.LobbyServerChooseEvent;
import xyz.kyngs.librelogin.api.event.events.PasswordChangeEvent;
import xyz.kyngs.librelogin.api.event.events.PremiumLoginSwitchEvent;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent;

/**
 * Registry holding standard {@link EventType} tokens for all core events.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class EventTypes<P, S> {

    /** Event type fired upon successful player authentication. */
    public EventType<P, S, AuthenticatedEvent<P, S>> authenticated =
            new EventType<>(AuthenticatedEvent.class);

    /** Event type fired when an incorrect password or verification code is entered. */
    public EventType<P, S, WrongPasswordEvent<P, S>> wrongPassword =
            new EventType<>(WrongPasswordEvent.class);

    /** Event type fired when selecting a limbo server for an unauthenticated player. */
    public EventType<P, S, LimboServerChooseEvent<P, S>> limboServerChoose =
            new EventType<>(LimboServerChooseEvent.class);

    /** Event type fired when selecting a destination lobby server for a player. */
    public EventType<P, S, LobbyServerChooseEvent<P, S>> lobbyServerChoose =
            new EventType<>(LobbyServerChooseEvent.class);

    /** Event type fired when a player updates their password credential. */
    public EventType<P, S, PasswordChangeEvent<P, S>> passwordChange =
            new EventType<>(PasswordChangeEvent.class);

    /** Event type fired when a player toggles premium auto-login state. */
    public EventType<P, S, PremiumLoginSwitchEvent<P, S>> premiumLoginSwitch =
            new EventType<>(PremiumLoginSwitchEvent.class);

    /** Package-private constructor preventing external instantiation. */
    EventTypes() {}
}
