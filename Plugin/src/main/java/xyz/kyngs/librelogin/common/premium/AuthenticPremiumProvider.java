/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.premium;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.premium.PremiumException;
import xyz.kyngs.librelogin.api.premium.PremiumProvider;
import xyz.kyngs.librelogin.api.premium.PremiumUser;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/**
 * Standard implementation of {@link PremiumProvider} handling premium Minecraft account
 * verification.
 *
 * <p>This provider resolves premium account UUIDs and profile metadata by querying the official
 * Mojang API with automatic fallback to secondary resolvers (PlayerDB, Minetools) in case of rate
 * limiting or service disruptions. Lookups are cached for 15 minutes using Caffeine (with negative
 * caching for offline players) to minimize outbound HTTP latency and avoid throttling.
 *
 * <p>This class is thread-safe. Cache lookups and HTTP requests are safe to invoke concurrently
 * across threads, though caller threads must not be the main server thread as asserted by {@link
 * AuthenticLibreLogin#reportMainThread()}.
 */
public class AuthenticPremiumProvider implements PremiumProvider {

    private final Cache<String, Optional<PremiumUser>> userCache;
    private final Cache<UUID, PremiumUser> uuidCache;
    private final Cache<String, PremiumException> failureCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(30, TimeUnit.SECONDS)
                    .maximumSize(10_000)
                    .build();
    private final Map<String, CompletableFuture<Optional<PremiumUser>>> inFlight =
            new ConcurrentHashMap<>();
    private final List<ThrowableFunction<String, PremiumUser, PremiumException>> fetchers;
    private final AuthenticLibreLogin<?, ?> plugin;

    /**
     * Constructs a new {@code AuthenticPremiumProvider} with configured fallback resolvers and
     * cache.
     *
     * @param plugin the plugin instance for logging and thread assertion
     */
    public AuthenticPremiumProvider(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        this.userCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();
        this.uuidCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();

        this.fetchers = new ArrayList<>(3);
        this.fetchers.add(this::getUserFromMojang);
        this.fetchers.add(this::getUserFromPlayerDB);
        this.fetchers.add(this::getUserFromMinetools);
    }

    @Override
    public PremiumUser getUserForName(String name) throws PremiumException {
        name = name.toLowerCase(Locale.ROOT);

        var result = userCache.getIfPresent(name);
        if (result == null) {
            result = resolve(name);
        }

        if (result != null && result.isPresent()) {
            PremiumUser user = result.get();
            if (user.uuid() != null) {
                uuidCache.put(user.uuid(), user);
            }
            return user;
        }

        return null;
    }

    /**
     * Runs the HTTP lookup outside of the cache (a Caffeine loader runs inside a map lock). One
     * lookup per name is in flight at a time; concurrent callers wait for it. Failures are kept for
     * a short time so a flood of joins with the same name cannot multiply the outbound calls.
     */
    private Optional<PremiumUser> resolve(String name) throws PremiumException {
        var failure = failureCache.getIfPresent(name);
        if (failure != null) {
            throw failure;
        }

        var mine = new CompletableFuture<Optional<PremiumUser>>();
        var running = inFlight.putIfAbsent(name, mine);
        if (running != null) {
            try {
                return running.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof PremiumException premium) {
                    throw premium;
                }
                throw new PremiumException(PremiumException.Issue.UNDEFINED, e);
            }
        }

        try {
            var result = lookup(name);
            userCache.put(name, result);
            mine.complete(result);
            return result;
        } catch (LookupFailure e) {
            // Not cached as "not premium": only remembered briefly as a failure.
            failureCache.put(name, e.cause);
            mine.completeExceptionally(e.cause);
            throw e.cause;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(name, mine);
        }
    }

    private static String encodeName(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Asks the resolvers in order. The first one that answers is final, including a "not found"
     * answer: Mojang is authoritative, and third-party mirrors may hold stale data that would mark
     * a free name as premium. Mirrors are consulted only when the previous resolver failed.
     */
    private Optional<PremiumUser> lookup(String name) {
        PremiumException last = null;
        for (int i = 0; i < fetchers.size(); i++) {
            try {
                return Optional.ofNullable(fetchers.get(i).apply(name));
            } catch (PremiumException e) {
                last = e;
                if (i < fetchers.size() - 1) {
                    plugin.getLogger()
                            .warn(
                                    "Premium lookup for '{}' failed ({}: {}); falling back to the"
                                            + " next resolver.",
                                    name,
                                    e.getIssue(),
                                    e.getMessage());
                }
            } catch (RuntimeException e) {
                last = new PremiumException(PremiumException.Issue.UNDEFINED, e);
                if (plugin.getLogger().isDebugEnabled()) {
                    plugin.getLogger()
                            .debug(
                                    "Unexpected runtime exception while fetching premium user "
                                            + name,
                                    e);
                }
            }
        }
        throw new LookupFailure(
                last != null
                        ? last
                        : new PremiumException(PremiumException.Issue.UNDEFINED, "No resolver"));
    }

    /** Carries a lookup failure out of the cache loader without caching a result. */
    private static final class LookupFailure extends RuntimeException {
        private final PremiumException cause;

        private LookupFailure(PremiumException cause) {
            super(null, cause, false, false);
            this.cause = cause;
        }
    }

    private PremiumUser getUserFromPlayerDB(String name) throws PremiumException {
        HttpURLConnection connection = null;
        try {
            plugin.reportMainThread();
            connection =
                    (HttpURLConnection)
                            URI.create(
                                            "https://playerdb.co/api/player/minecraft/"
                                                    + encodeName(name))
                                    .toURL()
                                    .openConnection();
            connection.setRequestProperty("User-Agent", GeneralUtil.userAgent(plugin.getVersion()));
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            switch (connection.getResponseCode()) {
                case 200 -> {
                    JsonObject data;
                    try (var in = connection.getInputStream();
                            var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        data = AuthenticLibreLogin.GSON.fromJson(reader, JsonObject.class);
                    }

                    var id =
                            data.get("data")
                                    .getAsJsonObject()
                                    .get("player")
                                    .getAsJsonObject()
                                    .get("id")
                                    .getAsString();
                    var username =
                            data.get("data")
                                    .getAsJsonObject()
                                    .get("player")
                                    .getAsJsonObject()
                                    .get("username")
                                    .getAsString();

                    return new PremiumUser(
                            UUID.fromString(id), username, username.equalsIgnoreCase(name));
                }
                case 400 -> {
                    return null;
                }
                case 500 ->
                        throw new PremiumException(
                                PremiumException.Issue.SERVER_EXCEPTION,
                                GeneralUtil.readInput(connection.getErrorStream()));
                default ->
                        throw new PremiumException(
                                PremiumException.Issue.UNDEFINED,
                                GeneralUtil.readInput(connection.getErrorStream()));
            }
        } catch (IOException e) {
            throw new PremiumException(PremiumException.Issue.SERVER_EXCEPTION, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private PremiumUser getUserFromMinetools(String name) throws PremiumException {
        HttpURLConnection connection = null;
        try {
            plugin.reportMainThread();
            connection =
                    (HttpURLConnection)
                            URI.create("https://api.minetools.eu/uuid/" + encodeName(name))
                                    .toURL()
                                    .openConnection();
            connection.setRequestProperty("User-Agent", GeneralUtil.userAgent(plugin.getVersion()));
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            switch (connection.getResponseCode()) {
                case 200 -> {
                    JsonObject data;
                    try (var in = connection.getInputStream();
                            var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        data = AuthenticLibreLogin.GSON.fromJson(reader, JsonObject.class);
                    }

                    var rawId = data.get("id");
                    if (rawId == null || rawId.isJsonNull()) {
                        var error = data.get("error");
                        if (error == null) {
                            return null;
                        }
                        var errorMessage = error.getAsString();
                        if (errorMessage.equals("Invalid UUID or nickname.")) {
                            return null;
                        } else {
                            throw new PremiumException(
                                    PremiumException.Issue.UNDEFINED, errorMessage);
                        }
                    }
                    var username = data.get("name").getAsString();

                    return new PremiumUser(
                            GeneralUtil.fromUnDashedUUID(rawId.getAsString()),
                            username,
                            username.equalsIgnoreCase(name));
                }
                case 400 -> {
                    return null;
                }
                case 500 ->
                        throw new PremiumException(
                                PremiumException.Issue.SERVER_EXCEPTION,
                                GeneralUtil.readInput(connection.getErrorStream()));
                default ->
                        throw new PremiumException(
                                PremiumException.Issue.UNDEFINED,
                                GeneralUtil.readInput(connection.getErrorStream()));
            }
        } catch (SocketTimeoutException te) {
            throw new PremiumException(PremiumException.Issue.THROTTLED, "Minetools API timed out");
        } catch (IOException e) {
            throw new PremiumException(PremiumException.Issue.SERVER_EXCEPTION, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private PremiumUser getUserFromMojang(String name) throws PremiumException {
        HttpURLConnection connection = null;
        try {
            plugin.reportMainThread();
            connection =
                    (HttpURLConnection)
                            URI.create(
                                            "https://api.minecraftservices.com/minecraft/profile/lookup/name/"
                                                    + encodeName(name))
                                    .toURL()
                                    .openConnection();
            connection.setRequestProperty("User-Agent", GeneralUtil.userAgent(plugin.getVersion()));
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            return switch (connection.getResponseCode()) {
                case 429 ->
                        throw new PremiumException(
                                PremiumException.Issue.THROTTLED,
                                GeneralUtil.readInput(connection.getErrorStream()));
                case 204, 404 -> null;
                case 200 -> {
                    JsonObject data;
                    try (var in = connection.getInputStream();
                            var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        data = AuthenticLibreLogin.GSON.fromJson(reader, JsonObject.class);
                    }

                    var id = data.get("id").getAsString();
                    var demo = data.get("demo");

                    yield (demo != null && demo.getAsBoolean())
                            ? null
                            : new PremiumUser(
                                    GeneralUtil.fromUnDashedUUID(id),
                                    data.get("name").getAsString(),
                                    true // Mojang API is always authoritative
                                    );
                }
                case 403 -> {
                    if ("text/html".equals(connection.getContentType())) {
                        throw new PremiumException(
                                PremiumException.Issue.SERVER_EXCEPTION,
                                GeneralUtil.readInput(connection.getErrorStream()));
                    }
                    throw new PremiumException(
                            PremiumException.Issue.UNDEFINED,
                            GeneralUtil.readInput(connection.getErrorStream()));
                }
                case 500 ->
                        throw new PremiumException(
                                PremiumException.Issue.SERVER_EXCEPTION,
                                GeneralUtil.readInput(connection.getErrorStream()));
                default ->
                        throw new PremiumException(
                                PremiumException.Issue.UNDEFINED,
                                GeneralUtil.readInput(connection.getErrorStream()));
            };
        } catch (SocketTimeoutException te) {
            throw new PremiumException(PremiumException.Issue.THROTTLED, "Mojang API timed out");
        } catch (IOException e) {
            throw new PremiumException(PremiumException.Issue.UNDEFINED, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public PremiumUser getUserForUUID(UUID uuid) throws PremiumException {
        if (uuid == null) return null;
        var cached = uuidCache.getIfPresent(uuid);
        if (cached != null) return cached;

        HttpURLConnection connection = null;
        try {
            plugin.reportMainThread();
            connection =
                    (HttpURLConnection)
                            URI.create(
                                            "https://sessionserver.mojang.com/session/minecraft/profile/"
                                                    + uuid.toString())
                                    .toURL()
                                    .openConnection();
            connection.setRequestProperty("User-Agent", GeneralUtil.userAgent(plugin.getVersion()));
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            var user =
                    switch (connection.getResponseCode()) {
                        case 429 ->
                                throw new PremiumException(
                                        PremiumException.Issue.THROTTLED,
                                        GeneralUtil.readInput(connection.getErrorStream()));
                        case 204, 404 -> null;
                        case 200 -> {
                            JsonObject data;
                            try (var in = connection.getInputStream();
                                    var reader =
                                            new InputStreamReader(in, StandardCharsets.UTF_8)) {
                                data = AuthenticLibreLogin.GSON.fromJson(reader, JsonObject.class);
                            }

                            var name = data.get("name").getAsString();

                            yield new PremiumUser(
                                    uuid, name, true); // Mojang API is always authoritative
                        }
                        case 500 ->
                                throw new PremiumException(
                                        PremiumException.Issue.SERVER_EXCEPTION,
                                        GeneralUtil.readInput(connection.getErrorStream()));
                        default ->
                                throw new PremiumException(
                                        PremiumException.Issue.UNDEFINED,
                                        GeneralUtil.readInput(connection.getErrorStream()));
                    };

            if (user != null) {
                uuidCache.put(uuid, user);
                userCache.put(user.name().toLowerCase(Locale.ROOT), Optional.of(user));
            }

            return user;
        } catch (IOException e) {
            throw new PremiumException(PremiumException.Issue.UNDEFINED, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
