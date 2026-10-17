/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;

/**
 * Main Paper plugin bootstrap entry point for LibreLogin.
 *
 * <p>Handles world generator hooks and plugin lifecycle initialization. If initialization fails,
 * the plugin stays enabled in lockdown mode and denies every join: an offline-mode server must
 * never keep accepting players without authentication.
 */
// Lockdown uses the String kick/disallow APIs: the only ones present on every version (1.13+).
@SuppressWarnings("deprecation")
public class PaperBootstrap extends JavaPlugin implements LibreLoginProvider<Player, World> {

    private static final String LOCKDOWN_REASON =
            "Authentication is unavailable on this server. Please contact an administrator.";

    private PaperLibreLogin libreLogin;
    private volatile boolean lockdown;

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(
            @NotNull String worldName, @Nullable String id) {
        return id == null ? null : id.equals("void") ? new VoidWorldGenerator() : null;
    }

    @Override
    public void onLoad() {
        getLogger()
                .info(
                        "Detected server distribution - "
                                + getServer().getName()
                                + " "
                                + getServer().getVersion()
                                + (PaperAdventure.isNative()
                                        ? ""
                                        : " (legacy text delivery, no native Adventure)"));

        try {
            libreLogin = new PaperLibreLogin(this);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to load LibreLogin Next.", t);
            libreLogin = null;
        }
    }

    @Override
    public void onEnable() {
        if (libreLogin == null) {
            enterLockdown(null);
            return;
        }

        getLogger().info("Bootstrapping LibreLogin Next...");
        try {
            libreLogin.enable();
        } catch (Throwable t) {
            enterLockdown(t);
        }
    }

    @Override
    public void onDisable() {
        if (libreLogin == null) {
            return;
        }
        try {
            libreLogin.disable();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while disabling LibreLogin Next.", t);
        }
    }

    @Override
    public PaperLibreLogin getLibreLogin() {
        return libreLogin;
    }

    /** Disables this plugin via the Bukkit plugin manager. */
    protected void disable() {
        getServer().getPluginManager().disablePlugin(this);
    }

    /**
     * Returns whether initialization failed and every join is being denied.
     *
     * @return {@code true} in lockdown mode
     */
    public boolean isLockdown() {
        return lockdown;
    }

    private void enterLockdown(@Nullable Throwable cause) {
        lockdown = true;
        getLogger()
                .log(
                        Level.SEVERE,
                        "LibreLogin Next failed to start. LOCKDOWN: every join is denied until the"
                                + " problem is fixed and the server is restarted.",
                        cause);
        getServer().getPluginManager().registerEvents(new LockdownListener(), this);
        for (Player player : getServer().getOnlinePlayers()) {
            player.kickPlayer(LOCKDOWN_REASON);
        }
    }

    private static final class LockdownListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPreLogin(AsyncPlayerPreLoginEvent event) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, LOCKDOWN_REASON);
        }
    }
}
