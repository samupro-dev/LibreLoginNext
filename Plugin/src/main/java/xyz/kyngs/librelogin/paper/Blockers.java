/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.ALLOWED_COMMANDS_WHILE_UNAUTHORIZED;

import java.util.Locale;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.server.ServerHandler;
import xyz.kyngs.librelogin.common.config.HoconPluginConfiguration;

/**
 * Event listener responsible for restricting interactions, movement, commands, and combat for
 * unauthorized players who have not yet completed authentication or 2FA.
 */
public class Blockers implements Listener {

    private final PaperLibreLogin plugin;
    private final AuthorizationProvider<Player> authorizationProvider;
    private final HoconPluginConfiguration configuration;
    private final ServerHandler<Player, World> serverHandler;

    /**
     * Constructs a new {@link Blockers} listener.
     *
     * @param plugin the Paper LibreLogin plugin instance
     */
    public Blockers(PaperLibreLogin plugin) {
        this.plugin = plugin;
        this.authorizationProvider = plugin.getAuthorizationProvider();
        this.configuration = plugin.getConfiguration();
        this.serverHandler = plugin.getServerHandler();
    }

    private <E extends PlayerEvent & Cancellable> void cancelIfNeeded(E event) {
        cancelIfNeeded(event.getPlayer(), event);
    }

    private void cancelIfNeeded(Player player, Cancellable cancellable) {
        if (inLimbo(player)) {
            cancellable.setCancelled(true);
        }
    }

    private boolean inLimbo(Player player) {
        return !authorizationProvider.isAuthorized(player)
                || authorizationProvider.isAwaiting2FA(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        var toWorld = event.getTo() != null ? event.getTo().getWorld() : null;
        boolean isDestinationLimbo =
                toWorld != null && serverHandler.getLimboServers().contains(toWorld);

        if (inLimbo(event.getPlayer())) {
            if (!isDestinationLimbo) {
                event.setCancelled(true);
            }
        } else {
            if (isDestinationLimbo && !event.getPlayer().hasPermission("librelogin.limbo.access")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (authorizationProvider.isAuthorized(event.getPlayer())
                && !authorizationProvider.isAwaiting2FA(event.getPlayer())) {
            return;
        }

        var raw = event.getMessage().trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1).trim();
        }
        var command = raw.split(" ")[0].toLowerCase(Locale.ROOT);
        var strippedCommand =
                command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;

        for (String allowed : configuration.get(ALLOWED_COMMANDS_WHILE_UNAUTHORIZED)) {
            var lowerAllowed = allowed.toLowerCase(Locale.ROOT);
            if (command.equals(lowerAllowed) || strippedCommand.equals(lowerAllowed)) {
                return;
            }
        }

        event.setCancelled(true);
        new PaperAudience(event.getPlayer())
                .sendMessage(plugin.getMessages().getMessage("error.not-authorized"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!PaperUtil.hasChangedPosition(event)) {
            return;
        }
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfNeeded(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfNeeded(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (inLimbo(event.getPlayer())) {
            PaperUtil.setInvisible(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfNeeded(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            cancelIfNeeded(player, event);
        }
    }
}
