/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.event;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Base implementation of a player-associated event. Resolves and caches audience, UUID, and
 * database user representations for the event subject.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticPlayerBasedEvent<P, S> implements PlayerBasedEvent<P, S> {

    private final User user;
    private final Audience audience;
    private final UUID uuid;
    private final P player;
    private final LibreLoginPlugin<P, S> plugin;
    private final PlatformHandle<P, S> platformHandle;

    /**
     * Constructs a new player-based event.
     *
     * @param user the database user associated with the player, if present
     * @param player the platform player instance, if online/connected
     * @param plugin the LibreLogin plugin instance
     */
    public AuthenticPlayerBasedEvent(
            @Nullable User user, @Nullable P player, LibreLoginPlugin<P, S> plugin) {
        this.plugin = plugin;
        this.platformHandle = plugin.getPlatformHandle();
        this.user = user;
        this.audience =
                player == null ? Audience.empty() : platformHandle.getAudienceForPlayer(player);
        this.uuid =
                player == null
                        ? (user == null ? null : user.getUuid())
                        : platformHandle.getUUIDForPlayer(player);
        this.player = player;
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public Audience getAudience() {
        return audience;
    }

    @Override
    public P getPlayer() {
        return player;
    }

    @Override
    public LibreLoginPlugin<P, S> getPlugin() {
        return plugin;
    }

    @Override
    public PlatformHandle<P, S> getPlatformHandle() {
        return platformHandle;
    }
}
