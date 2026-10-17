/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import static xyz.kyngs.librelogin.paper.protocol.ProtocolUtil.getServerVersion;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.util.reflection.Reflection;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.common.listener.AuthenticListeners;
import xyz.kyngs.librelogin.common.util.ComponentBridge;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.paper.protocol.ClientPublicKey;
import xyz.kyngs.librelogin.paper.protocol.EncryptionUtil;
import xyz.kyngs.librelogin.paper.protocol.ProtocolUtil;

/**
 * Core event and packet listener for the Paper platform.
 *
 * <p>Handles pre-login caching, authentication handshakes, packet-level encryption, spawn location
 * resolution, and player lifecycle events.
 */
public class PaperListeners extends AuthenticListeners<PaperLibreLogin, Player, World>
        implements Listener {

    private static volatile EncryptionMethods encryptionMethods;
    private static final Object ENCRYPT_LOCK = new Object();

    /**
     * NMS methods used to enable login encryption.
     *
     * @param encrypt the connection method installing the cipher
     * @param cipherFactory {@code null} when {@code encrypt} takes the {@link SecretKey} directly,
     *     otherwise the static {@code (int mode, Key key) -> Cipher} factory
     */
    private record EncryptionMethods(Method encrypt, @Nullable Method cipherFactory) {}

    private final KeyPair keyPair = EncryptionUtil.generateKeyPair();
    private final Random random = new SecureRandom();
    private final Cache<InetSocketAddress, EncryptionData> encryptionDataCache =
            Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.MINUTES).maximumSize(10_000).build();
    private final FloodgateHelper floodgateHelper;
    private final Cache<UUID, String> ipCache;
    private final Cache<UUID, User> readOnlyUserCache;
    private final Cache<UUID, Location> spawnLocationCache;
    private final Cache<UUID, Location> joinedWhileDead;
    // Exact (case-insensitive) names of online players, readable from Netty and worker threads.
    // Bukkit#getPlayer(String) is a prefix match and is not safe off the main thread.
    private final Set<String> onlineNames = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a new {@link PaperListeners} handler.
     *
     * @param plugin the PaperLibreLogin plugin instance
     */
    public PaperListeners(PaperLibreLogin plugin) {
        super(plugin);

        floodgateHelper = this.plugin.floodgateEnabled() ? new FloodgateHelper() : null;
        ipCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
        readOnlyUserCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
        spawnLocationCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
        joinedWhileDead =
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
    }

    /**
     * Registers Bukkit event listeners and appropriate spawn location listeners based on server
     * version capability.
     */
    public void registerListeners() {
        for (var online : Bukkit.getOnlinePlayers()) {
            onlineNames.add(online.getName().toLowerCase(Locale.ROOT));
        }
        Bukkit.getPluginManager().registerEvents(this, plugin.getBootstrap());

        boolean asyncSpawnAvailable = false;
        try {
            Class.forName("io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent");
            asyncSpawnAvailable = true;
        } catch (Throwable ignored) {
        }

        if (asyncSpawnAvailable) {
            Bukkit.getPluginManager()
                    .registerEvents(new ModernSpawnLocationListener(this), plugin.getBootstrap());
        } else {
            Bukkit.getPluginManager()
                    .registerEvents(new LegacySpawnLocationListener(this), plugin.getBootstrap());
        }
    }

    /**
     * Returns the spawn location cache for pending authentications.
     *
     * @return the cache mapping player UUID to target {@link Location}
     */
    public Cache<UUID, Location> getSpawnLocationCache() {
        return spawnLocationCache;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlineNames.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        onPlayerDisconnect(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        var location = joinedWhileDead.getIfPresent(event.getPlayer().getUniqueId());
        if (location == null) {
            return;
        }

        if (!PaperUtil.isPluginRespawn(event)) {
            return;
        }

        event.setRespawnLocation(location);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        onlineNames.add(player.getName().toLowerCase(Locale.ROOT));
        var puuid = player.getUniqueId();

        var data = readOnlyUserCache.getIfPresent(puuid);
        if (data == null && !plugin.fromFloodgate(player.getUniqueId())) {
            PaperUtil.kickPlayerSafe(
                    player, plugin.getMessages().getMessage("kick.internal-error"));
            return;
        }

        readOnlyUserCache.invalidate(puuid);
        if (player.isDead()) {
            spawnLocationCache.invalidate(puuid);
            player.spigot().respawn();
        } else {
            joinedWhileDead.invalidate(puuid);
        }

        onPostLogin(player, data);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        ipCache.put(event.getUniqueId(), event.getAddress().getHostAddress());
        if (plugin.fromFloodgate(event.getUniqueId())) {
            return;
        }

        try {
            if (!plugin.getDatabaseProvider().connected()) {
                PaperAdventure.disallow(
                        event,
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessages().getMessage("kick.database-error"));
                return;
            }

            var user = plugin.getDatabaseProvider().getByName(event.getName());
            if (user == null) {
                user = checkAndValidateByName(event.getName(), null, true, event.getAddress());
            }

            if (user != null) {
                var newProfile = PaperUtil.createProfileSafe(user.getUuid(), event.getName());
                event.setPlayerProfile(newProfile);
                readOnlyUserCache.put(user.getUuid(), user);
                ipCache.put(user.getUuid(), event.getAddress().getHostAddress());
            }
        } catch (InvalidCommandArgument e) {
            PaperAdventure.disallow(
                    event, AsyncPlayerPreLoginEvent.Result.KICK_OTHER, e.getErrorMessage());
        } catch (Exception e) {
            plugin.getLogger()
                    .error(
                            "Database error in AsyncPlayerPreLoginEvent for "
                                    + event.getName()
                                    + "! Rejecting login.",
                            e);
            PaperAdventure.disallow(
                    event,
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessages().getMessage("kick.database-error"));
        }
    }

    /**
     * Resolves the target spawn location for a connecting player.
     *
     * @param puuid the UUID of the player
     * @param eventSpawnLocation the original spawn location from the event
     * @param isNewPlayer whether the player is joining for the first time
     * @return the resolved {@link Location}, or {@code null} if default location should be used
     */
    public Location onChooseSpawnLocation(
            UUID puuid, Location eventSpawnLocation, boolean isNewPlayer) {
        var ip = ipCache.getIfPresent(puuid);
        if (ip == null) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin.getBootstrap(),
                            () -> {
                                var p = plugin.getPlayerForUUID(puuid);
                                if (p != null) {
                                    PaperUtil.kickPlayerSafe(
                                            p,
                                            plugin.getMessages().getMessage("kick.database-error"));
                                }
                            });
            return null;
        }

        ipCache.invalidate(puuid);

        var world = chooseServer(puuid, ip, readOnlyUserCache.getIfPresent(puuid));
        if (world.value() == null) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin.getBootstrap(),
                            () -> {
                                var p = plugin.getPlayerForUUID(puuid);
                                if (p != null) {
                                    PaperUtil.kickPlayerSafe(
                                            p,
                                            plugin.getMessages()
                                                    .getMessage(
                                                            "kick.no-"
                                                                    + (world.key()
                                                                            ? "lobby"
                                                                            : "limbo")));
                                }
                            });
            return null;
        }

        Function<World, Boolean> isLimbo =
                (w) -> plugin.getConfiguration().get(ConfigurationKeys.LIMBO).contains(w.getName());

        World chosenWorld = world.value();
        boolean isAuthenticated = world.key();

        if (!isNewPlayer
                && !isLimbo.apply(eventSpawnLocation.getWorld())
                && isLimbo.apply(chosenWorld)) {
            spawnLocationCache.put(puuid, eventSpawnLocation);
        }

        if (isAuthenticated && !isLimbo.apply(chosenWorld)) {
            joinedWhileDead.invalidate(puuid);
            return null;
        }

        var loc = chosenWorld.getSpawnLocation();
        joinedWhileDead.put(puuid, loc);
        return loc;
    }

    /**
     * Handles incoming login packets asynchronously.
     *
     * @param event the packet receive event
     */
    public void asyncPacketReceive(PacketReceiveEvent event) {
        var user = event.getUser();
        var type = event.getPacketType();

        if (plugin.getLogger().isDebugEnabled()) {
            plugin.getLogger()
                    .debug(
                            "Packet received {} from {} ({})",
                            type,
                            user.getName(),
                            user.getAddress());
        }

        if (type == PacketType.Login.Client.LOGIN_START) {
            var packet = new WrapperLoginClientLoginStart(event);
            var sessionKey = (InetSocketAddress) user.getAddress();

            encryptionDataCache.invalidate(sessionKey);

            if (plugin.floodgateEnabled()) {
                var success = floodgateHelper.processFloodgateTasks(event, packet);
                if (!success) {
                    return;
                }
            }
            var username = packet.getUsername();

            Optional<ClientPublicKey> clientKey;

            if (getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_19_3)) {
                clientKey = Optional.empty();
            } else {
                var signature = packet.getSignatureData();

                clientKey =
                        signature.map(
                                data -> {
                                    var expires = data.getTimestamp();
                                    var key = data.getPublicKey();
                                    var signatureData = data.getSignature();

                                    return new ClientPublicKey(expires, key, signatureData);
                                });
            }

            if (onlineNames.contains(username.toLowerCase(Locale.ROOT))) {
                kickPlayer(
                        plugin.getMessages().getMessage(MessageKeys.KICK_ALREADY_CONNECTED.key()),
                        user);
                return;
            }

            if (floodgateHelper != null
                    && floodgateHelper.isFloodgateConnection(event.getChannel())) {
                receiveFakeStartPacket(
                        username, clientKey.orElse(null), event.getChannel(), UUID.randomUUID());
                return;
            }
            var preLoginResult = onPreLogin(username, user.getAddress().getAddress());
            switch (preLoginResult.state()) {
                case DENIED -> {
                    assert preLoginResult.message() != null;
                    kickPlayer(preLoginResult.message(), user);
                }
                case FORCE_ONLINE -> {
                    byte[] token;
                    try {
                        token = EncryptionUtil.generateVerifyToken(random);

                        var newPacket =
                                new WrapperLoginServerEncryptionRequest(
                                        "", keyPair.getPublic(), token);

                        encryptionDataCache.put(
                                sessionKey,
                                new EncryptionData(
                                        username,
                                        token,
                                        clientKey.orElse(null),
                                        preLoginResult.user().getUuid()));

                        PacketEvents.getAPI()
                                .getProtocolManager()
                                .sendPacket(event.getChannel(), newPacket);
                    } catch (Exception e) {
                        plugin.getLogger()
                                .error(
                                        "Failed to send encryption begin packet for player "
                                                + username
                                                + "! Kicking player.",
                                        e);
                        kick("kick.internal-error", "Internal error", user);
                    }
                }
                default -> {
                    receiveFakeStartPacket(
                            username,
                            clientKey.orElse(null),
                            event.getChannel(),
                            UUID.randomUUID());
                }
            }
        } else {
            var packet = new WrapperLoginClientEncryptionResponse(event);
            var sharedSecret = packet.getEncryptedSharedSecret();

            var sessionAddress = (InetSocketAddress) user.getAddress();
            var data = encryptionDataCache.getIfPresent(sessionAddress);

            if (data == null) {
                kick("kick.handshake-failed", "Illegal encryption state", user);
                return;
            }
            encryptionDataCache.invalidate(sessionAddress);

            var expectedToken = data.token().clone();

            if (!verifyNonce(packet, data.publicKey(), expectedToken)) {
                kick("kick.handshake-failed", "Invalid nonce", user);
                return;
            }

            var privateKey = keyPair.getPrivate();

            SecretKey loginKey;

            try {
                loginKey = EncryptionUtil.decryptSharedKey(privateKey, sharedSecret);
            } catch (GeneralSecurityException securityEx) {
                kick("kick.handshake-failed", "Cannot decrypt shared secret", user);
                return;
            }

            try {
                if (!enableEncryption(loginKey, user, event.getChannel())) {
                    return;
                }
            } catch (Exception e) {
                kick("kick.handshake-failed", "Cannot decrypt shared secret", user);
                return;
            }

            var serverId = EncryptionUtil.getServerIdHashString("", loginKey, keyPair.getPublic());
            var username = data.username();
            var address = user.getAddress();

            try {
                if (hasJoined(username, serverId, address.getAddress())) {
                    receiveFakeStartPacket(
                            username, data.publicKey(), event.getChannel(), data.uuid());
                } else {
                    kick("kick.invalid-session", "Invalid session", user);
                }
            } catch (IOException e) {
                if (e instanceof SocketTimeoutException) {
                    plugin.getLogger()
                            .warn("Session verification timed out (5 seconds) for " + username);
                }
                kick("kick.session-verify-failed", "Cannot verify session", user);
            }
        }
    }

    /**
     * Intercepts and delegates incoming packet receive events to the asynchronous worker pool.
     *
     * @param event the packet receive event
     */
    public void onPacketReceive(PacketReceiveEvent event) {
        event.setCancelled(true);

        var copy = event.clone();
        var user = event.getUser();

        try {
            AuthenticLibreLogin.getLoginExecutor()
                    .execute(
                            () -> {
                                try {
                                    asyncPacketReceive(copy);
                                } catch (Throwable t) {
                                    plugin.getLogger()
                                            .error(
                                                    "Failed to process login packet for "
                                                            + user.getName(),
                                                    t);
                                    kickPlayer(
                                            plugin.getMessages().getMessage("kick.server-busy"),
                                            user);
                                } finally {
                                    copy.cleanUp();
                                }
                            });
        } catch (RejectedExecutionException e) {
            // Login pool saturated (join flood): free the copy and refuse instead of hanging.
            copy.cleanUp();
            kickPlayer(plugin.getMessages().getMessage("kick.server-busy"), user);
        }
    }

    private void receiveFakeStartPacket(
            String username, ClientPublicKey clientKey, Object channel, UUID uuid) {
        WrapperLoginClientLoginStart startPacket;
        if (getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_20)) {
            startPacket =
                    new WrapperLoginClientLoginStart(
                            getServerVersion().toClientVersion(),
                            username,
                            clientKey == null ? null : clientKey.toSignatureData(),
                            uuid);
        } else if (getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
            startPacket =
                    new WrapperLoginClientLoginStart(
                            getServerVersion().toClientVersion(),
                            username,
                            clientKey == null ? null : clientKey.toSignatureData());
        } else {
            startPacket =
                    new WrapperLoginClientLoginStart(
                            getServerVersion().toClientVersion(), username);
        }
        PacketEvents.getAPI().getProtocolManager().receivePacketSilently(channel, startPacket);
    }

    /**
     * Verifies against Mojang session servers whether a player has authenticated with the given
     * server hash.
     *
     * @param username the player username
     * @param serverHash the calculated server ID hash
     * @param hostIp the player's remote IP address
     * @return {@code true} if Mojang confirmed the session; {@code false} otherwise
     * @throws IOException if network communication fails
     */
    public boolean hasJoined(String username, String serverHash, InetAddress hostIp)
            throws IOException {
        String url;
        var encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        if (hostIp instanceof Inet6Address
                || plugin.getConfiguration().get(ConfigurationKeys.ALLOW_PROXY_CONNECTIONS)) {
            url =
                    String.format(
                            "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s",
                            encodedUsername, serverHash);
        } else {
            var encodedIP = URLEncoder.encode(hostIp.getHostAddress(), StandardCharsets.UTF_8);
            url =
                    String.format(
                            "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s&ip=%s",
                            encodedUsername, serverHash, encodedIP);
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            int responseCode = conn.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean enableEncryption(
            SecretKey loginKey,
            com.github.retrooper.packetevents.protocol.player.User user,
            Object channel)
            throws IllegalArgumentException {
        try {
            Object networkManager = ProtocolUtil.findNetworkManager(channel);
            if (networkManager == null) {
                kick("kick.handshake-failed", "Couldn't find network manager", user);
                return false;
            }

            var methods = encryptionMethods;
            if (methods == null) {
                synchronized (ENCRYPT_LOCK) {
                    methods = encryptionMethods;
                    if (methods == null) {
                        methods = resolveEncryptionMethods(networkManager.getClass());
                        // Published once, fully built: readers never see half-initialized state.
                        encryptionMethods = methods;
                    }
                }
            }

            if (methods.cipherFactory() == null) {
                // Paper native cipher: takes the SecretKey directly
                methods.encrypt().invoke(networkManager, loginKey);
            } else {
                // Vanilla cipher fallback
                Object decryptionCipher =
                        methods.cipherFactory().invoke(null, Cipher.DECRYPT_MODE, loginKey);
                Object encryptionCipher =
                        methods.cipherFactory().invoke(null, Cipher.ENCRYPT_MODE, loginKey);
                methods.encrypt().invoke(networkManager, decryptionCipher, encryptionCipher);
            }
        } catch (Exception ex) {
            plugin.getLogger()
                    .error(
                            "Failed to enable packet encryption in network pipeline for player: "
                                    + user.getName(),
                            ex);
            kick("kick.handshake-failed", "Couldn't enable encryption", user);
            return false;
        }

        return true;
    }

    /**
     * Refuses a login with a configurable message; the technical reason only goes to the debug log.
     */
    private void kick(
            String messageKey,
            String detail,
            com.github.retrooper.packetevents.protocol.player.User player) {
        if (plugin.getLogger().isDebugEnabled()) {
            plugin.getLogger().debug("Login of {} refused: {}", player.getName(), detail);
        }
        kickPlayer(plugin.getMessages().getMessage(messageKey), player);
    }

    // parseComponent: available across PacketEvents 2.x releases, unlike its replacement.
    @SuppressWarnings("deprecation")
    private void kickPlayer(
            Component reason, com.github.retrooper.packetevents.protocol.player.User player) {
        // PacketEvents' own Adventure Component (server-provided or bundled by PacketEvents).
        var kickPacket =
                new WrapperLoginServerDisconnect(
                        AdventureSerializer.parseComponent(ComponentBridge.toJson(reason)));
        try {
            PacketEvents.getAPI().getProtocolManager().sendPacket(player.getChannel(), kickPacket);
        } finally {
            player.closeConnection();
        }
    }

    private static EncryptionMethods resolveEncryptionMethods(Class<?> networkManagerClass)
            throws NoSuchMethodException {
        // 1. Paper's native cipher, by name (1.13.2 - 1.16.3 and 1.21.1 - 1.21.3 / 1.21.4+)
        var direct = Reflection.getMethod(networkManagerClass, "setupEncryption", SecretKey.class);
        if (direct == null) {
            direct = Reflection.getMethod(networkManagerClass, "setEncryptionKey", SecretKey.class);
        }
        // 2. Same, by signature: obfuscated (Spigot-mapped) servers such as 1.16.5 keep only "a"
        if (direct == null) {
            direct = findBySignature(networkManagerClass, SecretKey.class);
        }
        if (direct != null) {
            return new EncryptionMethods(direct, null);
        }

        // 3. Vanilla: setEncryptionKey(Cipher, Cipher) plus Crypt/MinecraftEncryption#getCipher
        var pair =
                Reflection.getMethod(
                        networkManagerClass, "setEncryptionKey", Cipher.class, Cipher.class);
        if (pair == null) {
            pair = findBySignature(networkManagerClass, Cipher.class, Cipher.class);
        }
        if (pair == null) {
            throw new NoSuchMethodException(
                    "No login encryption method on " + networkManagerClass.getName());
        }

        Class<?> cryptClass = null;
        for (String candidate :
                new String[] {
                    "net.minecraft.util.Crypt", "net.minecraft.util.MinecraftEncryption"
                }) {
            try {
                cryptClass = Class.forName(candidate);
                break;
            } catch (Throwable ignored) {
            }
        }
        if (cryptClass == null) {
            try {
                String craftBukkitPkg = Bukkit.getServer().getClass().getPackage().getName();
                String v = craftBukkitPkg.substring(craftBukkitPkg.lastIndexOf('.') + 1);
                cryptClass = Class.forName("net.minecraft.server." + v + ".MinecraftEncryption");
            } catch (Throwable ignored) {
            }
        }
        if (cryptClass == null) {
            throw new NoSuchMethodException("Cannot find the NMS encryption helper class");
        }

        var factory = Reflection.getMethod(cryptClass, "getCipher", int.class, Key.class);
        if (factory == null) {
            factory = Reflection.getMethod(cryptClass, "a", int.class, Key.class);
        }
        if (factory == null) {
            for (var method : cryptClass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == Cipher.class
                        && Arrays.equals(
                                method.getParameterTypes(),
                                new Class<?>[] {int.class, Key.class})) {
                    method.setAccessible(true);
                    factory = method;
                    break;
                }
            }
        }
        if (factory == null) {
            throw new NoSuchMethodException(
                    "Cannot find the NMS cipher factory on " + cryptClass.getName());
        }
        return new EncryptionMethods(pair, factory);
    }

    @Nullable
    private static Method findBySignature(Class<?> type, Class<?>... parameters) {
        for (Class<?> current = type;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (var method : current.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == void.class
                        && Arrays.equals(method.getParameterTypes(), parameters)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private boolean verifyNonce(
            WrapperLoginClientEncryptionResponse packet,
            ClientPublicKey clientPublicKey,
            byte[] expectedToken) {
        try {
            if (getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_19)
                    && !getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_19_3)) {
                if (clientPublicKey == null) {
                    if (packet.getEncryptedVerifyToken().isEmpty()) {
                        return false;
                    }
                    return EncryptionUtil.verifyNonce(
                            expectedToken,
                            keyPair.getPrivate(),
                            packet.getEncryptedVerifyToken().get());
                } else {
                    PublicKey publicKey = clientPublicKey.key();
                    var optSignature = packet.getSaltSignature();
                    if (optSignature.isEmpty()) {
                        return false;
                    }
                    var signature = optSignature.get();

                    return EncryptionUtil.verifySignedNonce(
                            expectedToken,
                            publicKey,
                            signature.getSalt(),
                            signature.getSignature());
                }
            } else {
                if (packet.getEncryptedVerifyToken().isEmpty()) {
                    return false;
                }
                byte[] nonce = packet.getEncryptedVerifyToken().get();
                return EncryptionUtil.verifyNonce(expectedToken, keyPair.getPrivate(), nonce);
            }
        } catch (NoSuchAlgorithmException
                | InvalidKeyException
                | SignatureException
                | NoSuchPaddingException
                | IllegalBlockSizeException
                | BadPaddingException signatureEx) {
            return false;
        }
    }
}
