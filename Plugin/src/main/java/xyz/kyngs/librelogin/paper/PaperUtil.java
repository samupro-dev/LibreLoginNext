/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import xyz.kyngs.librelogin.lib.adventure.text.Component;

/**
 * Utility helper methods for Paper and Bukkit operations including thread synchronization,
 * reflection-based configuration checks, profile generation, and safe player operations.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class PaperUtil {

    private PaperUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Executes the given task on the main server thread, blocking until completion if called
     * asynchronously.
     *
     * @param runnable the task to execute
     * @param plugin the plugin instance for scheduling
     */
    public static void runSyncAndWait(Runnable runnable, PaperLibreLogin plugin) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            var future = new CompletableFuture<Void>();
            Bukkit.getScheduler()
                    .runTask(
                            plugin.getBootstrap(),
                            () -> {
                                try {
                                    runnable.run();
                                    future.complete(null);
                                } catch (Throwable t) {
                                    future.completeExceptionally(t);
                                }
                            });
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for sync task", e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    /**
     * Retrieves the version string of a plugin across modern PluginMeta and legacy
     * PluginDescriptionFile APIs.
     *
     * @param plugin the plugin to check
     * @return the plugin version string
     */
    public static String getPluginVersion(Plugin plugin) {
        try {
            return plugin.getPluginMeta().getVersion();
        } catch (Throwable t) {
            return plugin.getDescription().getVersion();
        }
    }

    /**
     * Retrieves the list of authors for a plugin across modern and legacy APIs.
     *
     * @param plugin the plugin to check
     * @return list of author names
     */
    public static List<String> getPluginAuthors(Plugin plugin) {
        try {
            return plugin.getPluginMeta().getAuthors();
        } catch (Throwable t) {
            return plugin.getDescription().getAuthors();
        }
    }

    /**
     * Determines whether the current Paper/Spigot server is running behind a proxy (Velocity or
     * BungeeCord).
     *
     * @return {@code true} if proxy forwarding mode is enabled; {@code false} otherwise
     */
    public static boolean isProxyEnabled() {
        // Tier 1: Paper 1.21.8+ / 26.x ServerConfiguration#isProxyEnabled()
        try {
            Method getServerConfigMethod = Bukkit.class.getMethod("getServerConfig");
            Object serverConfig = getServerConfigMethod.invoke(null);
            if (serverConfig != null) {
                try {
                    Method isProxyEnabledMethod =
                            serverConfig.getClass().getMethod("isProxyEnabled");
                    return (boolean) isProxyEnabledMethod.invoke(serverConfig);
                } catch (NoSuchMethodException ignored) {
                    // Tier 1b: Paper 1.21.5 ServerConfiguration#isProxyOnlineMode()
                    try {
                        Method isProxyOnlineModeMethod =
                                serverConfig.getClass().getMethod("isProxyOnlineMode");
                        if ((boolean) isProxyOnlineModeMethod.invoke(serverConfig)) {
                            return true;
                        }
                    } catch (Throwable ignored2) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Tier 2: Paper 1.19 - 1.21.4 GlobalConfiguration
        try {
            Class<?> globalConfigClass =
                    Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object globalConfig = globalConfigClass.getMethod("get").invoke(null);
            if (globalConfig != null) {
                Field proxiesField = globalConfig.getClass().getField("proxies");
                Object proxies = proxiesField.get(globalConfig);
                if (proxies != null) {
                    try {
                        Field velocityField = proxies.getClass().getField("velocity");
                        Object velocity = velocityField.get(proxies);
                        if (velocity != null) {
                            Field enabledField = velocity.getClass().getField("enabled");
                            if (enabledField.getBoolean(velocity)) {
                                return true;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    // proxies.bungee-cord.online-mode defaults to true and only applies when
                    // BungeeCord is enabled in spigot.yml, which Tier 4 checks.
                }
            }
        } catch (Throwable ignored) {
        }

        // Tier 3: PaperConfig velocitySupport (Paper 1.13 - 1.18.2)
        try {
            Class<?> paperConfigClass = Class.forName("com.destroystokyo.paper.PaperConfig");
            Field velocitySupportField = paperConfigClass.getField("velocitySupport");
            if (velocitySupportField.getBoolean(null)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Tier 4: SpigotConfig bungee check (Paper 1.13 - 26.2 / Spigot)
        try {
            Class<?> spigotConfigClass = Class.forName("org.spigotmc.SpigotConfig");
            Field bungeeField = spigotConfigClass.getField("bungee");
            if (bungeeField.getBoolean(null)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Tier 5: Bukkit.spigot().getConfig() fallback
        try {
            var spigotConfig = Bukkit.spigot().getConfig();
            if (spigotConfig.getBoolean("settings.bungeecord", false)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    /**
     * Sets a boolean game rule on a world across various Minecraft version naming schemes.
     *
     * @param world the target world
     * @param modernName the modern game rule name
     * @param legacyName the legacy game rule name
     * @param value the boolean value to apply
     */
    public static void setBooleanGameRule(
            World world, String modernName, String legacyName, boolean value) {
        GameRule<?> rule = null;
        try {
            rule = GameRule.getByName(modernName);
        } catch (Throwable ignored) {
        }
        if (rule == null) {
            try {
                rule = GameRule.getByName(legacyName);
            } catch (Throwable ignored) {
            }
        }
        if (rule != null && rule.getType() == Boolean.class) {
            @SuppressWarnings("unchecked")
            GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
            world.setGameRule(booleanRule, value);
            return;
        }
        try {
            world.setGameRuleValue(legacyName, String.valueOf(value));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Safely creates a {@link PlayerProfile} instance using modern exact profile creation when
     * available.
     *
     * @param uuid the player's unique identifier
     * @param name the player's username
     * @return the created player profile
     */
    public static PlayerProfile createProfileSafe(UUID uuid, String name) {
        try {
            return Bukkit.createProfileExact(uuid, name);
        } catch (Throwable t) {
            return Bukkit.createProfile(uuid, name);
        }
    }

    /**
     * Toggles invisibility for a player using native entity invisibility or fallback potion
     * effects.
     *
     * @param player the target player
     * @param invisible whether the player should be made invisible
     */
    public static void setInvisible(Player player, boolean invisible) {
        try {
            player.setInvisible(invisible);
        } catch (Throwable t) {
            if (invisible) {
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.INVISIBILITY,
                                Integer.MAX_VALUE,
                                1,
                                false,
                                false,
                                false));
            } else {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
    }

    /**
     * Checks whether a move event represents a change in block/coordinate position rather than just
     * orientation.
     *
     * @param event the move event to evaluate
     * @return {@code true} if position or world changed; {@code false} if only yaw/pitch changed
     */
    public static boolean hasChangedPosition(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return false;
        }
        return from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ()
                || !Objects.equals(from.getWorld(), to.getWorld());
    }

    /**
     * Determines whether a player respawn event was triggered programmatically by a plugin.
     *
     * @param event the player respawn event
     * @return {@code true} if the respawn was plugin-induced or reason is unavailable
     */
    public static boolean isPluginRespawn(PlayerRespawnEvent event) {
        try {
            Method m = event.getClass().getMethod("getRespawnReason");
            Object reason = m.invoke(event);
            return reason != null && "PLUGIN".equals(reason.toString());
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Safely kicks a player using Adventure components or legacy string fallback.
     *
     * @param player the player to kick
     * @param reason the kick reason component
     */
    public static void kickPlayerSafe(Player player, Component reason) {
        PaperAdventure.kick(player, reason);
    }
}
