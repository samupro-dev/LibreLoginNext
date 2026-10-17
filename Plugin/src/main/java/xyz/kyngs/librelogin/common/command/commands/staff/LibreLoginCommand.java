/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.staff;

import static xyz.kyngs.librelogin.common.AuthenticLibreLogin.DATE_TIME_FORMATTER;
import static xyz.kyngs.librelogin.common.AuthenticLibreLogin.GSON;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.database.AuthenticDatabaseProvider;
import xyz.kyngs.librelogin.common.database.AuthenticUser;
import xyz.kyngs.librelogin.common.event.events.AuthenticPasswordChangeEvent;
import xyz.kyngs.librelogin.common.event.events.AuthenticPremiumLoginSwitchEvent;
import xyz.kyngs.librelogin.common.server.AuthenticServerHandler;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Main administrative and management command for LibreLogin (/librelogin).
 *
 * @param <P> the platform-specific player type
 */
@CommandAlias("librelogin")
public class LibreLoginCommand<P> extends StaffCommand<P> {

    private static final int MAX_ALTS_SHOWN = 50;

    /** A configurable message as plain text, for use as a placeholder value. */
    private String text(String key) {
        var message = getMessage(key);
        return message == null ? "" : PlainTextComponentSerializer.plainText().serialize(message);
    }

    /**
     * Constructs the main LibreLogin management command.
     *
     * @param plugin the plugin instance
     */
    public LibreLoginCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Displays plugin version, author, and license information (publicly viewable).
     *
     * @param audience the audience receiving output
     * @return a completion stage representing execution
     */
    @Subcommand("about")
    @Default
    public CompletionStage<Void> onAbout(Audience audience) {
        return runAsync(
                () ->
                        audience.sendMessage(
                                getMessage("info.about", "%version%", plugin.getVersion())));
    }

    /**
     * Displays system health diagnostics including platform, JVM memory, database status, and
     * cached users count.
     *
     * @param audience the audience receiving output
     * @return a completion stage representing execution
     */
    @Subcommand("health")
    @CommandPermission("librelogin.admin.health")
    public CompletionStage<Void> onHealth(Audience audience) {
        return runAsync(
                () -> {
                    var runtime = Runtime.getRuntime();
                    long maxMemory = runtime.maxMemory() / 1048576L;
                    long totalMemory = runtime.totalMemory() / 1048576L;
                    long freeMemory = runtime.freeMemory() / 1048576L;
                    long usedMemory = totalMemory - freeMemory;

                    String platformName =
                            switch (plugin.getPlatformHandle()
                                    .getPlatformIdentifier()
                                    .toLowerCase(Locale.ROOT)) {
                                case "paper" -> "Paper";
                                case "velocity" -> "Velocity";
                                default -> plugin.getPlatformHandle().getPlatformIdentifier();
                            };

                    boolean connected =
                            plugin.getDatabaseProvider() != null
                                    && plugin.getDatabaseProvider().connected();
                    String dbStatus =
                            text(
                                    connected
                                            ? "health.status-connected"
                                            : "health.status-disconnected");
                    String dbType =
                            plugin.getConfiguration() != null
                                    ? plugin.getConfiguration().get(ConfigurationKeys.DATABASE_TYPE)
                                    : "Unknown";

                    long cachedUsers = 0;
                    if (plugin.getDatabaseProvider()
                            instanceof AuthenticDatabaseProvider<?> authDb) {
                        cachedUsers = authDb.getCachedUsersCount();
                    }

                    audience.sendMessage(getMessage("health.header"));
                    audience.sendMessage(
                            getMessage(
                                    "health.platform",
                                    "%platform%",
                                    platformName,
                                    "%version%",
                                    plugin.getVersion()));
                    audience.sendMessage(
                            getMessage(
                                    "health.memory",
                                    "%used%",
                                    String.valueOf(usedMemory),
                                    "%max%",
                                    String.valueOf(maxMemory),
                                    "%free%",
                                    String.valueOf(freeMemory)));
                    audience.sendMessage(
                            getMessage("health.database", "%status%", dbStatus, "%type%", dbType));
                    audience.sendMessage(
                            getMessage("health.caches", "%users%", String.valueOf(cachedUsers)));
                    audience.sendMessage(getMessage("health.footer"));
                });
    }

    /**
     * Sends a test email to verify SMTP configuration.
     *
     * @param audience the audience receiving output
     * @param email the recipient email address
     * @return a completion stage representing execution
     */
    @Subcommand("email test")
    @CommandPermission("librelogin.admin.email.test")
    @Syntax("{@@syntax.email-test}")
    @CommandCompletion("%autocomplete.email-test")
    public CompletionStage<Void> onEmailTest(Audience audience, String email) {
        return runAsync(
                () -> {
                    if (plugin.getEmailHandler() == null) {
                        throw new InvalidCommandArgument(
                                getMessage("error.password-resetting-disabled"));
                    }
                    audience.sendMessage(getMessage("info.sending-email"));
                    plugin.getEmailHandler().sendTestMail(email);
                    audience.sendMessage(getMessage("info.sent-email"));
                });
    }

    /**
     * Generates a diagnostic JSON dump containing server state, plugins, and thread dump.
     *
     * @param audience the audience receiving output
     * @return a completion stage representing execution
     */
    @Subcommand("dump")
    @CommandPermission("librelogin.admin.dump")
    public CompletionStage<Void> onDump(Audience audience) {
        return runAsync(
                () -> {
                    audience.sendMessage(getMessage("info.dumping"));

                    var dumpFolder = new File(plugin.getDataFolder(), "dumps");

                    if (!dumpFolder.exists()) {
                        dumpFolder.mkdirs();
                    }

                    var dumpFile =
                            new File(
                                    dumpFolder,
                                    "dump-%date%.json"
                                            .replace(
                                                    "%date%",
                                                    DateTimeFormatter.ofPattern(
                                                                    "dd-MM-yyyy_HH-mm-ss")
                                                            .format(LocalDateTime.now())));

                    if (dumpFile.exists()) {
                        dumpFile.delete();
                    }

                    try {
                        dumpFile.createNewFile();
                    } catch (IOException e) {
                        plugin.getLogger()
                                .error(
                                        "Failed to create diagnostic dump file: "
                                                + dumpFile.getPath(),
                                        e);
                        throw new InvalidCommandArgument(getMessage("error.unknown"));
                    }

                    var dump = new JsonObject();

                    dump.addProperty("version", plugin.getVersion());
                    dump.addProperty("date", DATE_TIME_FORMATTER.format(LocalDateTime.now()));

                    var server = new JsonObject();
                    var proxyData = plugin.getPlatformHandle().getProxyData();

                    server.addProperty("name", proxyData.name());
                    server.add("plugins", GSON.toJsonTree(proxyData.plugins()));
                    server.add("servers", GSON.toJsonTree(proxyData.servers()));
                    server.add("limbos", GSON.toJsonTree(proxyData.limbos()));
                    server.add("lobbies", GSON.toJsonTree(proxyData.lobbies()));

                    var threads = new JsonObject();
                    var mxBean = ManagementFactory.getThreadMXBean();

                    for (ThreadInfo info : mxBean.dumpAllThreads(true, true)) {
                        var thread = new JsonObject();

                        thread.addProperty("id", info.getThreadId());
                        thread.addProperty("name", info.getThreadName());
                        thread.addProperty("state", info.getThreadState().name());
                        thread.addProperty("priority", info.getPriority());
                        thread.addProperty("isDaemon", info.isDaemon());
                        thread.addProperty("isInNative", info.isInNative());
                        thread.addProperty("isSuspended", info.isSuspended());

                        var lock = new JsonObject();

                        if (info.getLockName() != null) {
                            lock.addProperty("name", info.getLockName());
                            lock.addProperty("ownerId", info.getLockOwnerId());
                            lock.addProperty("ownerName", info.getLockOwnerName());
                        }

                        thread.add("lock", lock);

                        var stackTrace = new JsonArray();

                        for (StackTraceElement element : info.getStackTrace()) {
                            stackTrace.add(
                                    element.getClassName()
                                            + "#"
                                            + element.getMethodName()
                                            + "#"
                                            + element.getLineNumber());
                        }

                        thread.add("stackTrace", stackTrace);
                        threads.add(info.getThreadName(), thread);
                    }

                    server.add("threads", threads);
                    dump.add("server", server);

                    try (var writer = new FileWriter(dumpFile)) {
                        writer.write(GSON.toJson(dump));
                    } catch (IOException e) {
                        plugin.getLogger()
                                .error(
                                        "Failed to write diagnostic dump file: "
                                                + dumpFile.getPath(),
                                        e);
                        throw new InvalidCommandArgument(getMessage("error.unknown"));
                    }

                    audience.sendMessage(getMessage("info.dumped", "%file%", dumpFile.getPath()));
                });
    }

    /**
     * Reloads both plugin configuration and messages from disk.
     *
     * @param audience the audience receiving output
     * @return a completion stage representing execution
     */
    @Subcommand("reload")
    @CommandPermission("librelogin.admin.reload")
    public CompletionStage<Void> onReload(Audience audience) {
        return runAsync(
                () -> {
                    audience.sendMessage(getMessage("info.reloading"));

                    try {
                        plugin.getConfiguration().reload(plugin);
                    } catch (IOException e) {
                        plugin.getLogger()
                                .error("Failed to reload configuration file ('config.conf'):", e);
                        throw new InvalidCommandArgument(getMessage("error.unknown"));
                    } catch (CorruptedConfigurationException e) {
                        var cause = GeneralUtil.getFurthestCause(e);
                        throw new InvalidCommandArgument(
                                getMessage(
                                        "error.corrupted-configuration",
                                        "%cause%",
                                        "%s: %s"
                                                .formatted(
                                                        cause.getClass().getSimpleName(),
                                                        cause.getMessage())));
                    }

                    try {
                        plugin.getMessages().reload(plugin);
                    } catch (IOException e) {
                        plugin.getLogger()
                                .error("Failed to reload messages file ('messages.conf'):", e);
                        throw new InvalidCommandArgument(getMessage("error.unknown"));
                    } catch (CorruptedConfigurationException e) {
                        var cause = GeneralUtil.getFurthestCause(e);
                        throw new InvalidCommandArgument(
                                getMessage(
                                        "error.corrupted-messages",
                                        "%cause%",
                                        "%s: %s"
                                                .formatted(
                                                        cause.getClass().getSimpleName(),
                                                        cause.getMessage())));
                    }

                    if (plugin.getServerHandler() instanceof AuthenticServerHandler<?, ?> ash) {
                        ash.reload();
                    }
                    try {
                        plugin.loadForbiddenPasswords();
                    } catch (IOException e) {
                        plugin.getLogger()
                                .warn("Failed to reload forbidden passwords: {}", e.getMessage());
                    }

                    plugin.getCommandProvider().injectMessages();
                    audience.sendMessage(getMessage("info.reloaded"));
                });
    }

    /**
     * Reloads configuration from disk.
     *
     * @param audience the audience receiving output
     * @return a completion stage representing execution
     */
    @Subcommand("reload config|reload configuration")
    @CommandPermission("librelogin.admin.reload")
    public CompletionStage<Void> onReloadConfiguration(Audience audience) {
        return runAsync(
                () -> {
                    audience.sendMessage(getMessage("info.reloading"));

                    try {
                        plugin.getConfiguration().reload(plugin);
                    } catch (IOException e) {
                        plugin.getLogger()
                                .error("Failed to reload configuration file ('config.conf'):", e);
                        throw new InvalidCommandArgument(getMessage("error.unknown"));
                    } catch (CorruptedConfigurationException e) {
                        var cause = GeneralUtil.getFurthestCause(e);
                        throw new InvalidCommandArgument(
                                getMessage(
                                        "error.corrupted-configuration",
                                        "%cause%",
                                        "%s: %s"
                                                .formatted(
                                                        cause.getClass().getSimpleName(),
                                                        cause.getMessage())));
                    }

                    if (plugin.getServerHandler() instanceof AuthenticServerHandler<?, ?> ash) {
                        ash.reload();
                    }
                    try {
                        plugin.loadForbiddenPasswords();
                    } catch (IOException e) {
                        plugin.getLogger()
                                .warn("Failed to reload forbidden passwords: {}", e.getMessage());
                    }

                    audience.sendMessage(getMessage("info.reloaded"));
                });
    }

    /**
     * Reloads messages configuration and reinjects translations into ACF.
     *
     * @param audience the audience receiving output
     * @return a completion stage representing execution
     */
    @Subcommand("reload messages")
    @CommandPermission("librelogin.admin.reload")
    public CompletionStage<Void> onReloadMessages(Audience audience) {
        return runAsync(
                () -> {
                    audience.sendMessage(getMessage("info.reloading"));

                    try {
                        plugin.getMessages().reload(plugin);
                    } catch (IOException e) {
                        plugin.getLogger()
                                .error("Failed to reload messages file ('messages.conf'):", e);
                        throw new InvalidCommandArgument(getMessage("error.unknown"));
                    } catch (CorruptedConfigurationException e) {
                        var cause = GeneralUtil.getFurthestCause(e);
                        throw new InvalidCommandArgument(
                                getMessage(
                                        "error.corrupted-messages",
                                        "%cause%",
                                        "%s: %s"
                                                .formatted(
                                                        cause.getClass().getSimpleName(),
                                                        cause.getMessage())));
                    }

                    plugin.getCommandProvider().injectMessages();
                    audience.sendMessage(getMessage("info.reloaded"));
                });
    }

    /**
     * Displays detailed profile information for a user.
     *
     * @param audience the audience receiving output
     * @param name the username to inspect
     * @return a completion stage representing execution
     */
    @Subcommand("user info")
    @CommandPermission("librelogin.admin.user.info")
    @Syntax("{@@syntax.user-info}")
    @CommandCompletion("%autocomplete.user-info")
    public CompletionStage<Void> onUserInfo(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    audience.sendMessage(
                            getMessage(
                                    "info.user",
                                    "%name%",
                                    user.getLastNickname(),
                                    "%uuid%",
                                    user.getUuid().toString(),
                                    "%premium_uuid%",
                                    user.getPremiumUUID() == null
                                            ? text("info.value-na")
                                            : user.getPremiumUUID().toString(),
                                    "%last_seen%",
                                    DATE_TIME_FORMATTER.format(
                                            user.getLastSeen().toLocalDateTime()),
                                    "%joined%",
                                    DATE_TIME_FORMATTER.format(
                                            user.getJoinDate().toLocalDateTime()),
                                    "%2fa%",
                                    text(
                                            user.getSecret() != null
                                                    ? "info.value-enabled"
                                                    : "info.value-disabled"),
                                    "%email%",
                                    user.getEmail() == null
                                            ? text("info.value-na")
                                            : user.getEmail(),
                                    "%ip%",
                                    user.getIp() == null ? text("info.value-na") : user.getIp(),
                                    "%last_authenticated%",
                                    user.getLastAuthentication() == null
                                            ? text("info.value-na")
                                            : DATE_TIME_FORMATTER.format(
                                                    user.getLastAuthentication()
                                                            .toLocalDateTime())));
                });
    }

    /**
     * Enables premium autologin for a user, validating official Mojang account presence and case
     * sensitivity.
     *
     * @param <P> the player type
     * @param player the player handle if online
     * @param user the database user
     * @param plugin the plugin instance
     * @param onlyValid whether to enforce reliable provider check
     */
    public static <P> void enablePremium(
            P player, User user, AuthenticLibreLogin<P, ?> plugin, boolean onlyValid) {
        var id = plugin.getUserOrThrowICA(user.getLastNickname());

        if (onlyValid && id != null && !id.reliable()) {
            plugin.getLogger()
                    .warn(
                            "Profile data from Mojang API is unverified for user '{}'. Cannot"
                                    + " safely auto-enable premium mode without exact name case"
                                    + " verification.",
                            user.getLastNickname());
            throw new InvalidCommandArgument(plugin.getMessages().getMessage("error.not-paid"));
        }

        if (id == null || !id.name().equals(user.getLastNickname())) {
            throw new InvalidCommandArgument(plugin.getMessages().getMessage("error.not-paid"));
        }

        user.setPremiumUUID(id.uuid());

        plugin.getEventProvider()
                .unsafeFire(
                        plugin.getEventTypes().premiumLoginSwitch,
                        new AuthenticPremiumLoginSwitchEvent<>(user, player, plugin));
    }

    /**
     * Migrates a user profile to a new nickname.
     *
     * @param audience the audience receiving output
     * @param name current username
     * @param newName target username
     * @return a completion stage representing execution
     */
    @Subcommand("user migrate")
    @CommandPermission("librelogin.admin.user.migrate")
    @Syntax("{@@syntax.user-migrate}")
    @CommandCompletion("%autocomplete.user-migrate")
    public CompletionStage<Void> onUserMigrate(Audience audience, String name, String newName) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);
                    var colliding = getDatabaseProvider().getByName(newName);

                    if (colliding != null && !colliding.getUuid().equals(user.getUuid())) {
                        throw new InvalidCommandArgument(
                                getMessage("error.occupied-user", "%name%", newName));
                    }

                    requireOffline(user);

                    audience.sendMessage(getMessage("info.editing"));

                    user.setLastNickname(newName);
                    if (user.getPremiumUUID() != null) {
                        user.setPremiumUUID(null);
                        plugin.getEventProvider()
                                .unsafeFire(
                                        plugin.getEventTypes().premiumLoginSwitch,
                                        new AuthenticPremiumLoginSwitchEvent<>(user, null, plugin));
                    }
                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));
                });
    }

    /**
     * Unregisters a user, clearing authentication details while preserving account history.
     *
     * @param audience the audience receiving output
     * @param name the username to unregister
     * @return a completion stage representing execution
     */
    @Subcommand("user unregister")
    @CommandPermission("librelogin.admin.user.unregister")
    @Syntax("{@@syntax.user-unregister}")
    @CommandCompletion("%autocomplete.user-unregister")
    public CompletionStage<Void> onUserUnregister(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    requireOffline(user);

                    audience.sendMessage(getMessage("info.editing"));

                    user.setHashedPassword(null);
                    user.setSecret(null);
                    user.setIp(null);
                    user.setLastAuthentication(null);
                    user.setPremiumUUID(null);
                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));
                });
    }

    /**
     * Completely removes a user profile from the database.
     *
     * @param audience the audience receiving output
     * @param name the username to delete
     * @return a completion stage representing execution
     */
    @Subcommand("user delete")
    @CommandPermission("librelogin.admin.user.delete")
    @Syntax("{@@syntax.user-delete}")
    @CommandCompletion("%autocomplete.user-delete")
    public CompletionStage<Void> onUserDelete(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    requireOffline(user);

                    audience.sendMessage(getMessage("info.deleting"));

                    getDatabaseProvider().deleteUser(user);

                    audience.sendMessage(getMessage("info.deleted"));
                });
    }

    /**
     * Completely removes a user profile and all associated data from the database (GDPR Right to be
     * Forgotten).
     *
     * @param audience the audience receiving output
     * @param name the username to forget
     * @return a completion stage representing execution
     */
    @Subcommand("user forget")
    @CommandPermission("librelogin.admin.user.forget")
    @Syntax("{@@syntax.user-forget}")
    @CommandCompletion("%autocomplete.user-forget")
    public CompletionStage<Void> onUserForget(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);
                    var player = getPossiblyOnlinePlayerOnThisProxy(user);

                    if (player != null) {
                        plugin.getPlatformHandle()
                                .kick(player, getMessage("kick.already-connected"));
                    }

                    audience.sendMessage(getMessage("info.deleting"));

                    getDatabaseProvider().deleteUser(user);

                    audience.sendMessage(getMessage("info.user-forgotten", "%name%", name));
                });
    }

    /**
     * Manually sets a user account to premium mode.
     *
     * @param audience the audience receiving output
     * @param name the username to configure
     * @return a completion stage representing execution
     */
    @Subcommand("user premium")
    @CommandPermission("librelogin.admin.user.premium")
    @Syntax("{@@syntax.user-premium}")
    @CommandCompletion("%autocomplete.user-premium")
    public CompletionStage<Void> onUserPremium(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);
                    var player = getPossiblyOnlinePlayerOnThisProxy(user);

                    audience.sendMessage(getMessage("info.editing"));

                    enablePremium(null, user, plugin, false);

                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));

                    if (player != null) {
                        plugin.getPlatformHandle()
                                .kick(player, getMessage("kick.premium-info-enabled"));
                    }
                });
    }

    /**
     * Manually resets a user account to cracked/manual authentication mode.
     *
     * @param audience the audience receiving output
     * @param name the username to configure
     * @return a completion stage representing execution
     */
    @Subcommand("user cracked")
    @CommandPermission("librelogin.admin.user.cracked")
    @Syntax("{@@syntax.user-cracked}")
    @CommandCompletion("%autocomplete.user-cracked")
    public CompletionStage<Void> onUserCracked(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);
                    var player = getPossiblyOnlinePlayerOnThisProxy(user);

                    if (!user.isRegistered()) {
                        // A passwordless cracked account could be claimed by anyone.
                        throw new InvalidCommandArgument(
                                getMessage("error.cracked-requires-password-staff"));
                    }

                    audience.sendMessage(getMessage("info.editing"));

                    user.setPremiumUUID(null);
                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));

                    if (player != null) {
                        plugin.getPlatformHandle()
                                .kick(player, getMessage("kick.premium-info-disabled"));
                    }
                });
    }

    /**
     * Manually registers a new account for a username with the specified password.
     *
     * @param audience the audience receiving output
     * @param name the username
     * @param password the password
     * @return a completion stage representing execution
     */
    @Private
    @Subcommand("user register")
    @CommandPermission("librelogin.admin.user.register")
    @Syntax("{@@syntax.user-register}")
    @CommandCompletion("%autocomplete.user-register")
    public CompletionStage<Void> onUserRegister(Audience audience, String name, String password) {
        return runAsync(
                () -> {
                    audience.sendMessage(getMessage("info.registering"));

                    var user = getDatabaseProvider().getByName(name);

                    if (user != null) {
                        throw new InvalidCommandArgument(getMessage("error.occupied-user"));
                    }

                    var hashedPassword = plugin.getDefaultCryptoProvider().createHash(password);

                    if (hashedPassword == null) {
                        throw new InvalidCommandArgument(getMessage("error.password-too-long"));
                    }
                    var premiumUser = plugin.getUserOrThrowICA(name);
                    user =
                            new AuthenticUser(
                                    plugin.generateNewUUID(
                                            name, premiumUser == null ? null : premiumUser.uuid()),
                                    null,
                                    hashedPassword,
                                    name,
                                    Timestamp.valueOf(LocalDateTime.now()),
                                    Timestamp.valueOf(LocalDateTime.now()),
                                    null,
                                    null,
                                    Timestamp.valueOf(LocalDateTime.now()),
                                    null,
                                    null);

                    getDatabaseProvider().insertUser(user);

                    audience.sendMessage(getMessage("info.registered"));
                });
    }

    /**
     * Forces authorization for an online player.
     *
     * @param audience the audience receiving output
     * @param name the username to authorize
     * @return a completion stage representing execution
     */
    @Subcommand("user login")
    @CommandPermission("librelogin.admin.user.login")
    @Syntax("{@@syntax.user-login}")
    @CommandCompletion("%autocomplete.user-login")
    public CompletionStage<Void> onUserLogin(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    var target = requireOnline(user);
                    requireUnAuthorized(target);
                    requireRegistered(user);

                    audience.sendMessage(getMessage("info.logging-in"));

                    plugin.getAuthorizationProvider()
                            .authorize(user, target, AuthenticatedEvent.AuthenticationReason.LOGIN);

                    audience.sendMessage(getMessage("info.logged-in"));
                });
    }

    /**
     * Disables two-factor authentication (TOTP) for a user.
     *
     * @param audience the audience receiving output
     * @param name the username
     * @return a completion stage representing execution
     */
    @Subcommand("user remove2fa")
    @CommandPermission("librelogin.admin.user.remove2fa")
    @Syntax("{@@syntax.user-remove-2fa}")
    @CommandCompletion("%autocomplete.user-remove-2fa")
    public CompletionStage<Void> onUserRemove2FA(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    audience.sendMessage(getMessage("info.editing"));

                    user.setSecret(null);
                    revokeSessions(user);
                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));
                });
    }

    /**
     * Clears the recovery email address for a user.
     *
     * @param audience the audience receiving output
     * @param name the username
     * @return a completion stage representing execution
     */
    @Subcommand("user removeemail")
    @CommandPermission("librelogin.admin.user.removeemail")
    @Syntax("{@@syntax.user-remove-email}")
    @CommandCompletion("%autocomplete.user-remove-email")
    public CompletionStage<Void> onUserRemoveEMail(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    audience.sendMessage(getMessage("info.editing"));

                    user.setEmail(null);
                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));
                });
    }

    /**
     * Manually sets the recovery email address for a user.
     *
     * @param audience the audience receiving output
     * @param name the username
     * @param email the email address
     * @return a completion stage representing execution
     */
    @Subcommand("user setemail")
    @CommandPermission("librelogin.admin.user.setemail")
    @Syntax("{@@syntax.user-set-email}")
    @CommandCompletion("%autocomplete.user-set-email")
    public CompletionStage<Void> onUserSetEMail(Audience audience, String name, String email) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    if (!GeneralUtil.isValidEmail(email)) {
                        throw new InvalidCommandArgument(getMessage("error.invalid-email"));
                    }

                    audience.sendMessage(getMessage("info.editing"));

                    user.setEmail(email.trim());
                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));
                });
    }

    /**
     * Administratively resets the password for a user.
     *
     * @param audience the audience receiving output
     * @param name the username
     * @param password the new password
     * @return a completion stage representing execution
     */
    @Private
    @Subcommand("user setpassword")
    @CommandPermission("librelogin.admin.user.setpassword")
    @Syntax("{@@syntax.user-set-password}")
    @CommandCompletion("%autocomplete.user-set-password")
    public CompletionStage<Void> onUserSetPassword(
            Audience audience, String name, String password) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);
                    var old = user.getHashedPassword();

                    setPassword(audience, user, password, "info.editing");

                    getDatabaseProvider().updateUser(user);

                    audience.sendMessage(getMessage("info.edited"));

                    plugin.getEventProvider()
                            .unsafeFire(
                                    plugin.getEventTypes().passwordChange,
                                    new AuthenticPasswordChangeEvent<>(user, null, plugin, old));
                });
    }

    /**
     * Lists alternate accounts that have logged in using the same IP address as the specified user.
     *
     * @param audience the audience receiving output
     * @param name the username to search alts for
     * @return a completion stage representing execution
     */
    @Subcommand("user alts")
    @CommandPermission("librelogin.admin.user.alts")
    @Syntax("{@@syntax.user-alts}")
    @CommandCompletion("%autocomplete.user-alts")
    public CompletionStage<Void> onUserAlts(Audience audience, String name) {
        return runAsync(
                () -> {
                    var user = getUserOtherWiseInform(name);

                    var alts = getDatabaseProvider().getByIP(user.getIp());

                    if (alts.isEmpty()) {
                        audience.sendMessage(getMessage("info.no-alts"));
                        return;
                    }

                    audience.sendMessage(
                            getMessage("info.alts", "%count%", String.valueOf(alts.size())));

                    int shown = 0;
                    for (var alt : alts) {
                        if (shown++ == MAX_ALTS_SHOWN) {
                            audience.sendMessage(
                                    getMessage(
                                            "info.alts-more",
                                            "%count%",
                                            String.valueOf(alts.size() - MAX_ALTS_SHOWN)));
                            break;
                        }
                        audience.sendMessage(
                                getMessage(
                                        "info.alts-entry",
                                        "%name%",
                                        alt.getLastNickname(),
                                        "%last_seen%",
                                        alt.getLastSeen() == null
                                                ? text("info.value-na")
                                                : DATE_TIME_FORMATTER.format(
                                                        alt.getLastSeen().toLocalDateTime())));
                    }
                });
    }
}
