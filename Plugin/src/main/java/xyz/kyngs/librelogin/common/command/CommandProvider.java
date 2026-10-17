/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command;

import co.aikar.commands.CommandManager;
import co.aikar.commands.MessageKeys;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.commands.authorization.ChangePasswordCommand;
import xyz.kyngs.librelogin.common.command.commands.authorization.LoginCommand;
import xyz.kyngs.librelogin.common.command.commands.authorization.RegisterCommand;
import xyz.kyngs.librelogin.common.command.commands.authorization.UnregisterCommand;
import xyz.kyngs.librelogin.common.command.commands.mail.EMailCommand;
import xyz.kyngs.librelogin.common.command.commands.premium.CrackedCommand;
import xyz.kyngs.librelogin.common.command.commands.premium.PremiumCommand;
import xyz.kyngs.librelogin.common.command.commands.staff.LibreLoginCommand;
import xyz.kyngs.librelogin.common.command.commands.tfa.TwoFactorCommand;
import xyz.kyngs.librelogin.common.util.RateLimiter;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;
import xyz.kyngs.librelogin.lib.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Manages the registration of commands, context resolvers, rate limiting, exception handling, and
 * message injection into ACF (Aikar's Command Framework).
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific sender type
 */
public class CommandProvider<P, S> extends AuthenticHandler<P, S> {

    public static final LegacyComponentSerializer ACF_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private final CommandManager<?, ?, ?, ?, ?, ?> manager;
    private final RateLimiter<UUID> limiter;
    private final Cache<UUID, Object> confirmCache;

    /**
     * Constructs a new command provider, initializes context resolvers, exception handlers, rate
     * limiters, and registers all commands supported by the configuration.
     *
     * @param plugin the plugin instance
     */
    public CommandProvider(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);

        limiter = new RateLimiter<>(1, TimeUnit.SECONDS);
        manager = plugin.provideManager();

        injectMessages();

        var contexts = manager.getCommandContexts();

        // Register Audience as both issuer-only and issuer-aware
        contexts.registerIssuerOnlyContext(
                Audience.class,
                context -> {
                    var issuer = context.getIssuer();
                    if (issuer.isPlayer() && limiter.tryAndLimit(issuer.getUniqueId())) {
                        throw new InvalidCommandArgument(
                                plugin.getMessages().getMessage("error.throttle"));
                    }
                    return plugin.getAudienceFromIssuer(issuer);
                });
        contexts.registerIssuerAwareContext(
                Audience.class,
                context -> {
                    var issuer = context.getIssuer();
                    if (issuer.isPlayer() && limiter.tryAndLimit(issuer.getUniqueId())) {
                        throw new InvalidCommandArgument(
                                plugin.getMessages().getMessage("error.throttle"));
                    }
                    return plugin.getAudienceFromIssuer(issuer);
                });

        // Register generic player context for erased <P> parameters (both issuer-only and
        // issuer-aware)
        contexts.registerIssuerOnlyContext(
                Object.class,
                context -> {
                    var player = plugin.getPlayerFromIssuer(context.getIssuer());

                    if (player == null) {
                        throw new co.aikar.commands.InvalidCommandArgument(
                                MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                    }

                    return player;
                });
        contexts.registerIssuerAwareContext(
                Object.class,
                context -> {
                    var player = plugin.getPlayerFromIssuer(context.getIssuer());

                    if (player == null) {
                        throw new co.aikar.commands.InvalidCommandArgument(
                                MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                    }

                    return player;
                });
        contexts.registerContext(
                Object.class,
                context -> {
                    var player = plugin.getPlayerFromIssuer(context.getIssuer());

                    if (player == null) {
                        throw new co.aikar.commands.InvalidCommandArgument(
                                MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                    }

                    return player;
                });

        // Register concrete platform player class (Velocity Player / Paper Player)
        var playerClass = plugin.getPlatformHandle().getPlayerClass();
        if (playerClass != null && playerClass != Object.class) {
            contexts.registerIssuerOnlyContext(
                    playerClass,
                    context -> {
                        var player = plugin.getPlayerFromIssuer(context.getIssuer());

                        if (player == null) {
                            throw new co.aikar.commands.InvalidCommandArgument(
                                    MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                        }

                        return player;
                    });
            contexts.registerIssuerAwareContext(
                    playerClass,
                    context -> {
                        var player = plugin.getPlayerFromIssuer(context.getIssuer());

                        if (player == null) {
                            throw new co.aikar.commands.InvalidCommandArgument(
                                    MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                        }

                        return player;
                    });
            contexts.registerContext(
                    playerClass,
                    context -> {
                        var player = plugin.getPlayerFromIssuer(context.getIssuer());

                        if (player == null) {
                            throw new co.aikar.commands.InvalidCommandArgument(
                                    MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                        }

                        return player;
                    });
        }

        contexts.registerIssuerOnlyContext(
                UUID.class,
                context -> {
                    var player = plugin.getPlayerFromIssuer(context.getIssuer());

                    if (player == null) {
                        throw new co.aikar.commands.InvalidCommandArgument(
                                MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                    }

                    return plugin.getPlatformHandle().getUUIDForPlayer(player);
                });
        contexts.registerIssuerAwareContext(
                UUID.class,
                context -> {
                    var player = plugin.getPlayerFromIssuer(context.getIssuer());

                    if (player == null) {
                        throw new co.aikar.commands.InvalidCommandArgument(
                                MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
                    }

                    return plugin.getPlatformHandle().getUUIDForPlayer(player);
                });

        manager.setDefaultExceptionHandler(
                (command, registeredCommand, sender, args, t) -> {
                    Throwable cause = t;
                    while (cause instanceof java.util.concurrent.CompletionException
                            || cause instanceof java.util.concurrent.ExecutionException
                            || cause instanceof java.lang.reflect.InvocationTargetException) {
                        if (cause.getCause() != null) {
                            cause = cause.getCause();
                        } else {
                            break;
                        }
                    }

                    if (cause instanceof InvalidCommandArgument ourEx) {
                        plugin.getAudienceFromIssuer(sender).sendMessage(ourEx.getErrorMessage());
                        return true;
                    }

                    plugin.getLogger()
                            .error(
                                    "An unexpected exception occurred while executing a command."
                                            + " Please attach the stack trace below and report this"
                                            + " issue.",
                                    cause);

                    return false;
                },
                false);

        confirmCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build();

        manager.registerCommand(new LoginCommand<>(plugin));
        manager.registerCommand(new RegisterCommand<>(plugin));
        manager.registerCommand(new UnregisterCommand<>(plugin));
        manager.registerCommand(new ChangePasswordCommand<>(plugin));
        manager.registerCommand(new PremiumCommand<>(plugin));
        manager.registerCommand(new CrackedCommand<>(plugin));
        manager.registerCommand(new LibreLoginCommand<>(plugin));

        if (plugin.getTOTPProvider() != null) {
            manager.registerCommand(new TwoFactorCommand<>(plugin));
        }

        if (plugin.getEmailHandler() != null) {
            manager.registerCommand(new EMailCommand<>(plugin));
        }
    }

    /**
     * Registers a pending premium confirmation request for a player UUID.
     *
     * @param uuid the player's unique identifier
     */
    public void registerConfirm(UUID uuid) {
        confirmCache.put(uuid, new Object());
    }

    /**
     * Executes the confirmation logic for enabling premium mode on a user account.
     *
     * @param player the player
     * @param audience the message audience
     * @param user the database user
     * @throws InvalidCommandArgument if confirmation was not initiated or timed out
     */
    public void onConfirm(P player, Audience audience, User user) {
        if (confirmCache.asMap().remove(user.getUuid()) == null) {
            throw new InvalidCommandArgument(plugin.getMessages().getMessage("error.no-confirm"));
        }

        audience.sendMessage(plugin.getMessages().getMessage("info.enabling"));

        LibreLoginCommand.enablePremium(player, user, plugin, true);

        plugin.getDatabaseProvider().updateUser(user);

        platformHandle.kick(player, plugin.getMessages().getMessage("kick.premium-info-enabled"));
    }

    /**
     * Obtains a localized text message by key.
     *
     * @param key the message key
     * @return the text component
     */
    public TextComponent getMessage(String key) {
        return plugin.getMessages().getMessage(key);
    }

    private String getMessageAsString(String key) {
        return ACF_SERIALIZER.serialize(getMessage(key));
    }

    /**
     * Obtains the command rate limiter.
     *
     * @return the rate limiter
     */
    public RateLimiter<UUID> getLimiter() {
        return limiter;
    }

    /**
     * Injects ACF core messages and syntax/autocomplete replacements from LibreLogin's messages
     * configuration.
     */
    public void injectMessages() {
        var locales = manager.getLocales();
        var localeMap = new HashMap<String, String>();

        localeMap.put("acf-core.permission_denied", getMessageAsString("error.no-permission"));
        localeMap.put(
                "acf-core.permission_denied_parameter", getMessageAsString("error.no-permission"));
        localeMap.put("acf-core.invalid_syntax", getMessageAsString("error.invalid-syntax"));
        localeMap.put("acf-core.unknown_command", getMessageAsString("error.unknown-command"));

        plugin.getMessages()
                .getMessages()
                .forEach(
                        (key, value) -> {
                            if (key.startsWith("syntax")) {
                                localeMap.put(key, ACF_SERIALIZER.serialize(value));
                            } else if (key.startsWith("autocomplete")) {
                                var serialized = ACF_SERIALIZER.serialize(value);
                                manager.getCommandReplacements()
                                        .addReplacement(
                                                key,
                                                serialized.isBlank()
                                                        ? serialized
                                                        : serialized + " @nothing");
                            }
                        });

        locales.addMessageStrings(locales.getDefaultLocale(), localeMap);
    }

    /** Unregisters all commands from the manager and invalidates confirmation caches. */
    public void unregister() {
        if (manager instanceof co.aikar.commands.BukkitCommandManager bcm) {
            bcm.unregisterCommands();
        } else if (manager instanceof co.aikar.commands.VelocityCommandManager vcm) {
            vcm.unregisterCommands();
        }
        confirmCache.invalidateAll();
    }
}
