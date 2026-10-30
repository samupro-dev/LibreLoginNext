/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.AUTO_PURGE_INACTIVE_DAYS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.CHECK_FOR_UPDATES;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.DATABASE_TYPE;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEFAULT_CRYPTO_PROVIDER;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.FORBIDDEN_PASSWORDS_DOWNLOAD;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.IP_HASH_SALT;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.IP_STORAGE_MODE;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.LIMBO;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.LOBBY;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MAIL_ENABLED;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MIGRATION_MYSQL_OLD_DATABASE_TABLE;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MIGRATION_POSTGRESQL_OLD_DATABASE_TABLE;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MINIMUM_PASSWORD_LENGTH;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.PASSWORD_REGEX;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REMEMBER_LAST_SERVER;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.TOTP_ENABLED;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.BiHolder;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.ReadDatabaseProvider;
import xyz.kyngs.librelogin.api.database.ReadDatabaseProviderRegistration;
import xyz.kyngs.librelogin.api.database.ReadWriteDatabaseProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.WriteDatabaseProvider;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.api.database.connector.MySQLDatabaseConnector;
import xyz.kyngs.librelogin.api.database.connector.PostgreSQLDatabaseConnector;
import xyz.kyngs.librelogin.api.database.connector.SQLiteDatabaseConnector;
import xyz.kyngs.librelogin.api.integration.LimboIntegration;
import xyz.kyngs.librelogin.api.premium.PremiumException;
import xyz.kyngs.librelogin.api.premium.PremiumUser;
import xyz.kyngs.librelogin.api.server.ServerHandler;
import xyz.kyngs.librelogin.api.totp.TOTPProvider;
import xyz.kyngs.librelogin.api.util.SemanticVersion;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider;
import xyz.kyngs.librelogin.common.command.CommandProvider;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.HoconMessages;
import xyz.kyngs.librelogin.common.config.HoconPluginConfiguration;
import xyz.kyngs.librelogin.common.crypto.Argon2IDCryptoProvider;
import xyz.kyngs.librelogin.common.crypto.BCrypt2ACryptoProvider;
import xyz.kyngs.librelogin.common.crypto.LegacyMessageDigestCryptoProvider;
import xyz.kyngs.librelogin.common.crypto.PBKDF2CryptoProvider;
import xyz.kyngs.librelogin.common.crypto.SCryptCryptoProvider;
import xyz.kyngs.librelogin.common.crypto.VerifyOnlyCryptoProvider;
import xyz.kyngs.librelogin.common.database.AuthenticDatabaseProvider;
import xyz.kyngs.librelogin.common.database.AuthenticUser;
import xyz.kyngs.librelogin.common.database.connector.AuthenticMySQLDatabaseConnector;
import xyz.kyngs.librelogin.common.database.connector.AuthenticPostgreSQLDatabaseConnector;
import xyz.kyngs.librelogin.common.database.connector.AuthenticSQLiteDatabaseConnector;
import xyz.kyngs.librelogin.common.database.connector.DatabaseConnectorRegistration;
import xyz.kyngs.librelogin.common.database.provider.LibreLoginMySQLDatabaseProvider;
import xyz.kyngs.librelogin.common.database.provider.LibreLoginPostgreSQLDatabaseProvider;
import xyz.kyngs.librelogin.common.database.provider.LibreLoginSQLiteDatabaseProvider;
import xyz.kyngs.librelogin.common.event.AuthenticEventProvider;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;
import xyz.kyngs.librelogin.common.integration.FloodgateIntegration;
import xyz.kyngs.librelogin.common.integration.luckperms.LuckPermsIntegration;
import xyz.kyngs.librelogin.common.listener.LoginTryListener;
import xyz.kyngs.librelogin.common.log.Log4JFilter;
import xyz.kyngs.librelogin.common.log.LogFilter;
import xyz.kyngs.librelogin.common.log.SimpleLogFilter;
import xyz.kyngs.librelogin.common.mail.AuthenticEMailHandler;
import xyz.kyngs.librelogin.common.migrate.AuthMeSQLMigrateReadProvider;
import xyz.kyngs.librelogin.common.migrate.FastLoginSQLMigrateReadProvider;
import xyz.kyngs.librelogin.common.migrate.LibreLoginSQLMigrateReadProvider;
import xyz.kyngs.librelogin.common.migrate.LoginToSQLMigrateReadProvider;
import xyz.kyngs.librelogin.common.premium.AuthenticPremiumProvider;
import xyz.kyngs.librelogin.common.redis.RedisService;
import xyz.kyngs.librelogin.common.server.AuthenticServerHandler;
import xyz.kyngs.librelogin.common.totp.AuthenticTOTPProvider;
import xyz.kyngs.librelogin.common.util.CancellableTask;
import xyz.kyngs.librelogin.common.util.CryptoUtil;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.common.util.GitHubUpdateChecker;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Common base implementation of {@link LibreLoginPlugin}. Central coordinator for authentication,
 * cryptography, database persistence, event dispatching, configuration management, server
 * switching, and platform integrations.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public abstract class AuthenticLibreLogin<P, S> implements LibreLoginPlugin<P, S> {

    public static final Gson GSON;
    public static volatile ExecutorService EXECUTOR;
    public static volatile ExecutorService LOGIN_EXECUTOR;
    private static volatile boolean shuttingDown;
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    static {
        GSON = new GsonBuilder().setPrettyPrinting().create();
        initExecutor();
    }

    /**
     * Initializes the worker pools if they are not already running.
     *
     * <p>{@link #EXECUTOR} runs commands and other work for connected players. {@link
     * #LOGIN_EXECUTOR} runs untrusted pre-authentication work (login handshakes, Mojang lookups),
     * so a join flood can only exhaust its own queue and never starves logged-in players. Both
     * pools use core = max so they really grow to their size (a {@link ThreadPoolExecutor} only
     * adds threads above core once its queue is full), and both reject instead of queueing without
     * bound: callers must handle {@link java.util.concurrent.RejectedExecutionException}.
     */
    public static synchronized void initExecutor() {
        shuttingDown = false;
        int cpus = Runtime.getRuntime().availableProcessors();
        if (EXECUTOR == null || EXECUTOR.isShutdown()) {
            EXECUTOR = newPool("LibreLoginNext-Worker-", Math.max(8, cpus * 4), 2000);
        }
        if (LOGIN_EXECUTOR == null || LOGIN_EXECUTOR.isShutdown()) {
            LOGIN_EXECUTOR = newPool("LibreLoginNext-Login-", Math.max(4, cpus * 2), 512);
        }
    }

    private static ExecutorService newPool(String prefix, int threads, int queue) {
        var executor =
                new ThreadPoolExecutor(
                        threads,
                        threads,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(queue),
                        new ThreadFactory() {
                            private final AtomicInteger threadNumber = new AtomicInteger(1);

                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = new Thread(r, prefix + threadNumber.getAndIncrement());
                                t.setDaemon(true);
                                return t;
                            }
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Retrieves the active shared executor service, initializing it if necessary.
     *
     * @return the active {@link ExecutorService}
     */
    public static ExecutorService getExecutor() {
        var executor = EXECUTOR;
        if (executor == null || executor.isShutdown()) {
            if (shuttingDown) {
                // A late task after disable() must not create a pool that is never shut down.
                throw new RejectedExecutionException("LibreLogin Next is disabled");
            }
            initExecutor();
            executor = EXECUTOR;
        }
        return executor;
    }

    /**
     * Retrieves the pool for untrusted pre-authentication work, initializing it if necessary.
     *
     * @return the login executor
     */
    public static ExecutorService getLoginExecutor() {
        var executor = LOGIN_EXECUTOR;
        if (executor == null || executor.isShutdown()) {
            if (shuttingDown) {
                throw new RejectedExecutionException("LibreLogin Next is disabled");
            }
            initExecutor();
            executor = LOGIN_EXECUTOR;
        }
        return executor;
    }

    private final Map<String, CryptoProvider> cryptoProviders;
    private final Map<String, ReadDatabaseProviderRegistration<?, ?, ?>> readProviders;
    private final Map<Class<?>, DatabaseConnectorRegistration<?, ?>> databaseConnectors;
    private final SetMultimap<UUID, CancellableTask> cancelOnExit;
    private final PlatformHandle<P, S> platformHandle;

    /** Age after which a name that never registered or logged in is released. */
    private static final int ABANDONED_ACCOUNT_DAYS = 7;

    private volatile LogFilter logFilter;

    private static final String FORBIDDEN_PASSWORDS_FILE = "forbidden-passwords.txt";
    private static final String FORBIDDEN_PASSWORDS_REMOTE_FILE = "forbidden-passwords-remote.txt";
    private static final long MAX_FORBIDDEN_PASSWORDS_DOWNLOAD_BYTES = 16L * 1024 * 1024;

    /** Longest password the operator's password-regex is evaluated against (bounds ReDoS cost). */
    private static final int MAX_REGEX_INPUT_LENGTH = 128;

    private volatile Set<String> forbiddenPasswords = Set.of();
    private volatile Set<String> localForbiddenPasswords = Set.of();
    private volatile Set<String> remoteForbiddenPasswords = Set.of();
    private volatile Pattern passwordPattern;
    private volatile String passwordPatternSource;
    private final Set<UUID> localOnlinePlayers;
    protected Logger logger;
    private AuthenticPremiumProvider premiumProvider;
    private AuthenticEventProvider<P, S> eventProvider;
    private AuthenticServerHandler<P, S> serverHandler;
    private TOTPProvider totpProvider;
    private AuthenticImageProjector<P, S> imageProjector;
    private FloodgateIntegration floodgateApi;
    private LuckPermsIntegration<P, S> luckpermsApi;
    private SemanticVersion version;
    private HoconPluginConfiguration configuration;
    private HoconMessages messages;
    private AuthenticAuthorizationProvider<P, S> authorizationProvider;
    private CommandProvider<P, S> commandProvider;
    private ReadWriteDatabaseProvider databaseProvider;
    private DatabaseConnector<?, ?> databaseConnector;
    private AuthenticEMailHandler eMailHandler;
    private LoginTryListener<P, S> loginTryListener;
    private RedisService redisService;

    /** Constructs the common LibreLogin plugin state. */
    protected AuthenticLibreLogin() {
        cryptoProviders = new ConcurrentHashMap<>();
        readProviders = new ConcurrentHashMap<>();
        databaseConnectors = new ConcurrentHashMap<>();
        platformHandle = providePlatformHandle();
        localOnlinePlayers = ConcurrentHashMap.newKeySet();
        cancelOnExit = Multimaps.synchronizedSetMultimap(HashMultimap.create());
    }

    public Map<Class<?>, DatabaseConnectorRegistration<?, ?>> getDatabaseConnectors() {
        return databaseConnectors;
    }

    @Override
    public <E extends Exception, C extends DatabaseConnector<E, ?>> void registerDatabaseConnector(
            Class<?> clazz, ThrowableFunction<String, C, E> factory, String id) {
        registerDatabaseConnector(new DatabaseConnectorRegistration<>(factory, null, id), clazz);
    }

    @Override
    public void registerReadProvider(ReadDatabaseProviderRegistration<?, ?, ?> registration) {
        readProviders.put(registration.id(), registration);
    }

    @Override
    public AuthenticEMailHandler getEmailHandler() {
        return eMailHandler;
    }

    @Nullable
    @Override
    public LimboIntegration<S> getLimboIntegration() {
        return null;
    }

    @Override
    public User createUser(
            UUID uuid,
            UUID premiumUUID,
            HashedPassword hashedPassword,
            String lastNickname,
            Timestamp joinDate,
            Timestamp lastSeen,
            String secret,
            String ip,
            Timestamp lastAuthentication,
            String lastServer,
            String email) {
        return new AuthenticUser(
                uuid,
                premiumUUID,
                hashedPassword,
                lastNickname,
                joinDate,
                lastSeen,
                secret,
                ip,
                lastAuthentication,
                lastServer,
                email);
    }

    public void registerDatabaseConnector(
            DatabaseConnectorRegistration<?, ?> registration, Class<?> clazz) {
        databaseConnectors.put(clazz, registration);
    }

    @Override
    public PlatformHandle<P, S> getPlatformHandle() {
        return platformHandle;
    }

    protected abstract PlatformHandle<P, S> providePlatformHandle();

    @Override
    public SemanticVersion getParsedVersion() {
        return version;
    }

    /**
     * Returns the compiled password-regex, compiling it once per configured value. An invalid
     * expression is reported once and then ignored.
     */
    private Pattern passwordPattern() {
        var regex = configuration.get(PASSWORD_REGEX);
        if (regex == null || regex.isBlank()) {
            return null;
        }
        if (!regex.equals(passwordPatternSource)) {
            Pattern compiled = null;
            try {
                compiled = Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                logger.error(
                        "Invalid password-regex in config.conf: '{}' - regex validation is disabled"
                                + " until it is fixed ({}).",
                        regex,
                        e.getDescription());
            }
            passwordPattern = compiled;
            passwordPatternSource = regex;
        }
        return passwordPattern;
    }

    @Override
    public boolean validPassword(String password) {
        if (password == null) {
            return false;
        }

        var minLength = configuration.get(MINIMUM_PASSWORD_LENGTH);
        if (minLength > 0 && password.length() < minLength) {
            return false;
        }

        var pattern = passwordPattern();
        if (pattern != null) {
            if (password.length() > MAX_REGEX_INPUT_LENGTH
                    || !pattern.matcher(password).matches()) {
                return false;
            }
        }

        var upper = password.toUpperCase(Locale.ROOT);

        return !forbiddenPasswords.contains(upper);
    }

    @Override
    public Map<String, ReadDatabaseProviderRegistration<?, ?, ?>> getReadProviders() {
        return Map.copyOf(readProviders);
    }

    public CommandProvider<P, S> getCommandProvider() {
        return commandProvider;
    }

    @Override
    public ReadWriteDatabaseProvider getDatabaseProvider() {
        return databaseProvider;
    }

    @Override
    public AuthenticPremiumProvider getPremiumProvider() {
        return premiumProvider;
    }

    @Override
    public TOTPProvider getTOTPProvider() {
        return totpProvider;
    }

    @Override
    public AuthenticImageProjector<P, S> getImageProjector() {
        return imageProjector;
    }

    @Override
    public ServerHandler<P, S> getServerHandler() {
        return serverHandler;
    }

    /**
     * Initializes and enables core plugin modules, database connections, cryptography providers,
     * and event handlers.
     */
    protected void enable() {
        initExecutor();
        version = SemanticVersion.parse(getVersion());
        if (logger == null) {
            logger = provideLogger();
        }

        try {
            var filter = new Log4JFilter();
            filter.inject();
            logFilter = filter;
        } catch (Throwable ignored) {
            logger.info("LogFilter is not supported on this platform");
            var simpleLogger = getSimpleLogger();

            if (simpleLogger != null) {
                logger.info("Using SimpleLogFilter");
                var filter = new SimpleLogFilter(simpleLogger);
                filter.inject();
                logFilter = filter;
            }
        }

        var folder = getDataFolder();
        if (!folder.exists()) {
            var oldFolder =
                    new File(
                            folder.getParentFile(),
                            folder.getName().equals("librelogin")
                                    ? "librepremium"
                                    : "LibrePremium");
            if (oldFolder.exists()) {
                logger.info("Migrating configuration and messages from old folder...");
                if (!oldFolder.renameTo(folder)) {
                    throw new RuntimeException(
                            "Can't migrate configuration and messages from old folder!");
                }
            }
        }

        try (var in = getResourceAsStream("LICENSE.txt")) {
            if (in != null) {
                Files.copy(
                        in,
                        new File(folder, "LICENSE.txt").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Silently ignore
        }

        if (platformHandle.getPlatformIdentifier().equals("paper")) {
            LIMBO.setDefault(List.of("limbo"));

            var lobby = HashMultimap.<String, String>create();
            lobby.put("root", "world");

            LOBBY.setDefault(lobby);
        }

        eventProvider = new AuthenticEventProvider<>(this);
        premiumProvider = new AuthenticPremiumProvider(this);

        registerCryptoProvider(new Argon2IDCryptoProvider(logger));
        registerCryptoProvider(new BCrypt2ACryptoProvider());
        registerCryptoProvider(new SCryptCryptoProvider());
        registerCryptoProvider(new PBKDF2CryptoProvider());
        // Verify-only: imported AuthMe / LibreLogin hashes, re-hashed on the next successful login.
        registerCryptoProvider(new LegacyMessageDigestCryptoProvider("SHA-256", "SHA-256", false));
        registerCryptoProvider(new LegacyMessageDigestCryptoProvider("SHA-512", "SHA-512", false));
        registerCryptoProvider(
                new LegacyMessageDigestCryptoProvider("LOGIT-SHA-256", "SHA-256", true));

        setupDB();

        checkDataFolder();

        loadConfigs();

        logger.info("Loading forbidden passwords...");

        try {
            loadForbiddenPasswords();
        } catch (IOException e) {
            logger.error("An unexpected error occurred while loading forbidden passwords list.", e);
            shutdownProxy(1);
        }

        logger.info("Loaded {} forbidden passwords.", forbiddenPasswords.size());

        connectToDB();

        // Pre-login stores a row for every new name; remove the ones that never became accounts
        // (bot joins) so they do not accumulate. Their UUIDs are deterministic, so a returning
        // player simply gets a new row.
        delay(
                () -> {
                    if (databaseProvider instanceof AuthenticDatabaseProvider<?> authDb) {
                        try {
                            int purged = authDb.purgeAbandonedUsers(ABANDONED_ACCOUNT_DAYS);
                            if (purged > 0) {
                                logger.info(
                                        "Removed {} never-registered name reservations older than"
                                                + " {} days.",
                                        purged,
                                        ABANDONED_ACCOUNT_DAYS);
                            }
                        } catch (RuntimeException e) {
                            logger.warn(
                                    "Failed to remove never-registered name reservations: {}",
                                    e.getMessage());
                        }
                    }
                },
                10000);

        var autoPurgeDays = configuration.get(AUTO_PURGE_INACTIVE_DAYS);
        if (autoPurgeDays > 0) {
            delay(
                    () -> {
                        if (databaseProvider instanceof AuthenticDatabaseProvider<?> authDb) {
                            int purged = authDb.purgeInactiveUsers(autoPurgeDays);
                            if (purged > 0) {
                                logger.info(
                                        "Automatically purged {} inactive accounts older than {}"
                                                + " days (GDPR retention policy).",
                                        purged,
                                        autoPurgeDays);
                            }
                        }
                    },
                    5000);
        }

        serverHandler = new AuthenticServerHandler<>(this);

        this.loginTryListener = new LoginTryListener<>(this);

        GeneralUtil.checkAndMigrate(configuration, logger, this);

        imageProjector = provideImageProjector();

        if (imageProjector != null) {
            if (!configuration.get(TOTP_ENABLED)) {
                imageProjector = null;
                logger.info(
                        "2FA module is disabled in configuration; skipping in-game image projector"
                                + " setup.");
            } else {
                imageProjector.enable();
            }
        }

        // The projector only renders the QR map; without it the secret is shown in chat, so 2FA
        // must stay available (and enforced) whenever it is enabled.
        totpProvider = configuration.get(TOTP_ENABLED) ? new AuthenticTOTPProvider(this) : null;
        eMailHandler = configuration.get(MAIL_ENABLED) ? new AuthenticEMailHandler(this) : null;

        authorizationProvider = new AuthenticAuthorizationProvider<>(this);
        commandProvider = new CommandProvider<>(this);

        if (version.dev()) {
            logger.warn("!! YOU ARE RUNNING A DEVELOPMENT BUILD OF LIBRELOGIN NEXT !!");
            logger.warn(
                    "!! THIS IS NOT A RELEASE, USE THIS ONLY IF YOU WERE INSTRUCTED TO DO SO. DO"
                            + " NOT USE THIS IN PRODUCTION !!");
        }

        if (configuration.get(CHECK_FOR_UPDATES)) {
            delay(() -> GitHubUpdateChecker.check(logger, getVersion()), 1000);
        }

        if (pluginPresent("floodgate")) {
            logger.info("Floodgate detected, enabling bedrock support...");
            floodgateApi = new FloodgateIntegration();
        }

        if (pluginPresent("luckperms")) {
            logger.info("LuckPerms detected, enabling context provider");
            luckpermsApi = new LuckPermsIntegration<>(this);
        }

        if (configuration.get(ConfigurationKeys.REDIS_ENABLED)) {
            logger.info("Initializing Lettuce Redis clustering service...");
            redisService = new RedisService(this);
            if (redisService.connect()) {
                logger.info(
                        "Redis clustering enabled (Node ID: %s)"
                                .formatted(redisService.getServerId()));
                long intervalMillis = redisService.getHeartbeatIntervalSeconds() * 1000L;
                repeat(this::sendRedisHeartbeat, intervalMillis, intervalMillis);
            } else {
                logger.warn("Redis clustering failed to connect, continuing in standalone mode.");
            }
        }

        if (multiProxyEnabled()) {
            logger.info("Detected MultiProxy setup, enabling MultiProxy support...");
        }
    }

    @SuppressWarnings("unchecked")
    public <C extends DatabaseConnector<?, ?>>
            DatabaseConnectorRegistration<?, C> getDatabaseConnector(Class<C> clazz) {
        return (DatabaseConnectorRegistration<?, C>) databaseConnectors.get(clazz);
    }

    private void connectToDB() {
        logger.info("Connecting to the database...");

        try {
            var registration = readProviders.get(configuration.get(DATABASE_TYPE));
            if (registration == null) {
                logger.error(
                        "Database type %s doesn't exist, please check your configuration"
                                .formatted(configuration.get(DATABASE_TYPE)));
                shutdownProxy(1);
            }

            DatabaseConnector<?, ?> connector = null;

            if (registration.databaseConnector() != null) {
                var connectorRegistration = getDatabaseConnector(registration.databaseConnector());

                if (connectorRegistration == null) {
                    logger.error(
                            "Database type %s is corrupted, please use a different one"
                                    .formatted(configuration.get(DATABASE_TYPE)));
                    shutdownProxy(1);
                }

                connector =
                        connectorRegistration
                                .factory()
                                .apply("database.properties." + connectorRegistration.id() + ".");

                connector.connect();
            }

            var provider = registration.create(connector);

            if (provider instanceof ReadWriteDatabaseProvider casted) {
                databaseProvider = casted;
                databaseConnector = connector;
            } else {
                logger.error(
                        "Database type %s cannot be used for writing, please use a different one"
                                .formatted(configuration.get(DATABASE_TYPE)));
                shutdownProxy(1);
            }

        } catch (ShutdownException e) {
            throw e; // already reported; do not log it as a connection failure or shut down twice
        } catch (Exception e) {
            var cause = GeneralUtil.getFurthestCause(e);
            logger.error("!! THIS IS MOST LIKELY NOT AN ERROR CAUSED BY LIBRELOGIN NEXT !!");
            logger.error(
                    "Failed to connect to the database, this most likely is caused by wrong credentials. Cause: %s: %s"
                            .formatted(cause.getClass().getSimpleName(), cause.getMessage()));
            shutdownProxy(1);
        }

        logger.info("Successfully connected to the database");

        if (databaseProvider instanceof AuthenticDatabaseProvider<?> casted) {
            logger.info("Validating schema");

            try {
                casted.validateSchema();
            } catch (Exception e) {
                var cause = GeneralUtil.getFurthestCause(e);
                logger.error(
                        "Failed to validate schema! Cause: %s: %s"
                                .formatted(cause.getClass().getSimpleName(), cause.getMessage()));
                logger.error("Please open an issue on our GitHub, or visit Discord support");
                shutdownProxy(1);
            }

            logger.info("Schema validated");
        }
    }

    private void loadConfigs() {
        logger.info("Loading messages...");

        messages = new HoconMessages(logger);

        try {
            messages.reload(this);
        } catch (IOException e) {
            logger.error("Failed to load messages file ('messages.conf') from disk.", e);
            shutdownProxy(1);
        } catch (CorruptedConfigurationException e) {
            var cause = GeneralUtil.getFurthestCause(e);
            logger.error(
                    "The messages file ('messages.conf') is corrupted or contains syntax errors:"
                            + " {}: {}",
                    cause.getClass().getSimpleName(),
                    cause.getMessage());
            logger.error(
                    "Please review syntax in messages.conf or delete it to regenerate the default"
                            + " template.");
            shutdownProxy(1);
        }

        logger.info("Loading configuration...");

        var defaults = new ArrayList<BiHolder<Class<?>, String>>();

        for (DatabaseConnectorRegistration<?, ?> value : databaseConnectors.values()) {
            if (value.configClass() == null) continue;
            defaults.add(
                    new BiHolder<>(value.configClass(), "database.properties." + value.id() + "."));
            defaults.add(
                    new BiHolder<>(
                            value.configClass(), "migration.old-database." + value.id() + "."));
        }

        configuration = new HoconPluginConfiguration(logger, defaults);

        try {
            if (configuration.reload(this)) {
                logger.info(
                        "A new configuration file ('config.conf') has been generated with default"
                                + " settings. Please review it before starting the server.");
                shutdownProxy(0);
            }

            var limbos = configuration.get(LIMBO);
            var lobby = configuration.get(LOBBY);

            for (String value : lobby.values()) {
                if (limbos.contains(value)) {
                    throw new CorruptedConfigurationException(
                            "Lobby server/world %s is also a limbo server/world, this is not allowed"
                                    .formatted(value));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration file ('config.conf') from disk.", e);
            shutdownProxy(1);
        } catch (CorruptedConfigurationException e) {
            var cause = GeneralUtil.getFurthestCause(e);
            logger.error(
                    "The configuration file ('config.conf') is corrupted or contains syntax errors:"
                            + " {}: {}",
                    cause.getClass().getSimpleName(),
                    cause.getMessage());
            logger.error(
                    "Please review syntax in config.conf or delete it to regenerate the default"
                            + " template.");
            shutdownProxy(1);
        }
    }

    private void setupDB() {
        registerDatabaseConnector(
                new DatabaseConnectorRegistration<>(
                        prefix -> new AuthenticMySQLDatabaseConnector(this, prefix),
                        AuthenticMySQLDatabaseConnector.Configuration.class,
                        "mysql"),
                MySQLDatabaseConnector.class);
        registerDatabaseConnector(
                new DatabaseConnectorRegistration<>(
                        prefix -> new AuthenticSQLiteDatabaseConnector(this, prefix),
                        AuthenticSQLiteDatabaseConnector.Configuration.class,
                        "sqlite"),
                SQLiteDatabaseConnector.class);
        registerDatabaseConnector(
                new DatabaseConnectorRegistration<>(
                        prefix -> new AuthenticPostgreSQLDatabaseConnector(this, prefix),
                        AuthenticPostgreSQLDatabaseConnector.Configuration.class,
                        "postgresql"),
                PostgreSQLDatabaseConnector.class);

        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector -> new LibreLoginMySQLDatabaseProvider(connector, this),
                        "librelogin-mysql",
                        MySQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector -> new LibreLoginSQLiteDatabaseProvider(connector, this),
                        "librelogin-sqlite",
                        SQLiteDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector -> new LibreLoginPostgreSQLDatabaseProvider(connector, this),
                        "librelogin-postgresql",
                        PostgreSQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new AuthMeSQLMigrateReadProvider(
                                        configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE),
                                        logger,
                                        connector),
                        "authme-mysql",
                        MySQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new AuthMeSQLMigrateReadProvider(
                                        configuration.get(MIGRATION_POSTGRESQL_OLD_DATABASE_TABLE),
                                        logger,
                                        connector),
                        "authme-postgresql",
                        PostgreSQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector -> new AuthMeSQLMigrateReadProvider("authme", logger, connector),
                        "authme-sqlite",
                        SQLiteDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new FastLoginSQLMigrateReadProvider(
                                        "premium",
                                        logger,
                                        connector,
                                        databaseConnector,
                                        premiumProvider),
                        "fastlogin-mysql",
                        MySQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new FastLoginSQLMigrateReadProvider(
                                        "premium",
                                        logger,
                                        connector,
                                        databaseConnector,
                                        premiumProvider),
                        "fastlogin-sqlite",
                        SQLiteDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new LibreLoginSQLMigrateReadProvider(
                                        configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE),
                                        logger,
                                        connector),
                        "librelogin-migration-mysql",
                        MySQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new LibreLoginSQLMigrateReadProvider(
                                        configuration.get(MIGRATION_POSTGRESQL_OLD_DATABASE_TABLE),
                                        logger,
                                        connector),
                        "librelogin-migration-postgresql",
                        PostgreSQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new LibreLoginSQLMigrateReadProvider(
                                        "librepremium_data", logger, connector),
                        "librelogin-migration-sqlite",
                        SQLiteDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        connector ->
                                new LoginToSQLMigrateReadProvider(
                                        configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE),
                                        logger,
                                        connector),
                        "loginto-mysql",
                        MySQLDatabaseConnector.class));
        registerReadProvider(
                new ReadDatabaseProviderRegistration<>(
                        // LoginTo 4.x table name (auth_sources/LoginTo-main Database#initDatabase)
                        connector ->
                                new LoginToSQLMigrateReadProvider(
                                        "LoginTo_Players_Accounts", logger, connector),
                        "loginto-sqlite",
                        SQLiteDatabaseConnector.class));
    }

    /**
     * (Re)loads the forbidden password lists: the operator's {@code forbidden-passwords.txt} plus
     * the downloaded {@code forbidden-passwords-remote.txt}. The combined set is rebuilt and
     * swapped in atomically, so removals take effect on reload.
     *
     * @throws IOException if the local list cannot be created or read
     */
    public void loadForbiddenPasswords() throws IOException {
        var file = new File(getDataFolder(), FORBIDDEN_PASSWORDS_FILE);

        if (!file.exists()) {
            try (var fallbackIn = getResourceAsStream("forbidden-passwords-template.txt")) {
                if (fallbackIn != null) {
                    Files.copy(fallbackIn, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else if (!file.createNewFile()) {
                    throw new IOException("Failed to create forbidden-passwords.txt");
                }
            }
        }

        localForbiddenPasswords = readForbiddenPasswordsFromFile(file);
        var remote = new File(getDataFolder(), FORBIDDEN_PASSWORDS_REMOTE_FILE);
        try {
            remoteForbiddenPasswords = readForbiddenPasswordsFromFile(remote);
        } catch (IOException e) {
            logger.warn(
                    "Ignoring unreadable {}: {}", FORBIDDEN_PASSWORDS_REMOTE_FILE, e.getMessage());
            remoteForbiddenPasswords = Set.of();
        }
        publishForbiddenPasswords();

        if (configuration.get(FORBIDDEN_PASSWORDS_DOWNLOAD)) {
            downloadForbiddenPasswordsAsync(1);
        }
    }

    private void publishForbiddenPasswords() {
        var combined = new HashSet<String>(localForbiddenPasswords);
        combined.addAll(remoteForbiddenPasswords);
        forbiddenPasswords = Set.copyOf(combined);
    }

    private static Set<String> readForbiddenPasswordsFromFile(File file) throws IOException {
        if (!file.exists()) return Set.of();
        var set = new HashSet<String>();
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isBlank()) {
                    continue;
                }
                set.add(trimmed.toUpperCase(Locale.ROOT));
            }
        }
        return set;
    }

    private void downloadForbiddenPasswordsAsync(int attempt) {
        if (EXECUTOR == null || EXECUTOR.isShutdown()) {
            return;
        }
        GeneralUtil.runAsync(
                () -> {
                    if (EXECUTOR == null || EXECUTOR.isShutdown()) {
                        return;
                    }
                    try {
                        var connection =
                                (HttpURLConnection)
                                        URI.create(
                                                        "https://raw.githubusercontent.com/samupro-dev/LibreLoginNext/refs/heads/master/forbidden-passwords.txt")
                                                .toURL()
                                                .openConnection();

                        connection.setRequestProperty(
                                "User-Agent", GeneralUtil.userAgent(getVersion()));
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(10000);

                        var tempFile =
                                new File(getDataFolder(), FORBIDDEN_PASSWORDS_REMOTE_FILE + ".tmp");
                        try (var in = new BufferedInputStream(connection.getInputStream());
                                var fos = new FileOutputStream(tempFile)) {
                            var dataBuffer = new byte[8192];
                            long total = 0;
                            int bytesRead;
                            while ((bytesRead = in.read(dataBuffer)) != -1) {
                                total += bytesRead;
                                if (total > MAX_FORBIDDEN_PASSWORDS_DOWNLOAD_BYTES) {
                                    throw new IOException(
                                            "remote list exceeds "
                                                    + MAX_FORBIDDEN_PASSWORDS_DOWNLOAD_BYTES
                                                    + " bytes");
                                }
                                fos.write(dataBuffer, 0, bytesRead);
                            }
                        } catch (IOException e) {
                            Files.deleteIfExists(tempFile.toPath());
                            throw e;
                        } finally {
                            connection.disconnect();
                        }

                        // The operator's own forbidden-passwords.txt is never overwritten.
                        var targetFile = new File(getDataFolder(), FORBIDDEN_PASSWORDS_REMOTE_FILE);
                        try {
                            Files.move(
                                    tempFile.toPath(),
                                    targetFile.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.ATOMIC_MOVE);
                        } catch (IOException e) {
                            Files.move(
                                    tempFile.toPath(),
                                    targetFile.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }

                        remoteForbiddenPasswords = readForbiddenPasswordsFromFile(targetFile);
                        publishForbiddenPasswords();
                        logger.info(
                                "Successfully updated forbidden passwords list from remote"
                                        + " repository ({} passwords loaded).",
                                forbiddenPasswords.size());
                    } catch (Exception e) {
                        logger.warn(
                                "Failed to download updated forbidden passwords list (attempt"
                                        + " {}/5): {}.",
                                attempt,
                                e.getMessage());
                        if (attempt < 5 && EXECUTOR != null && !EXECUTOR.isShutdown()) {
                            delay(() -> downloadForbiddenPasswordsAsync(attempt + 1), 60000L);
                        }
                    }
                });
    }

    /**
     * Generates a new UUID based on the configured creator strategy.
     *
     * @param name the player username
     * @param premiumID the Mojang premium UUID, if known
     * @return the generated UUID
     */
    public UUID generateNewUUID(String name, @Nullable UUID premiumID) {
        return switch (configuration.getNewUUIDCreator()) {
            case RANDOM -> UUID.randomUUID();
            case MOJANG -> premiumID == null ? GeneralUtil.getCrackedUUIDFromName(name) : premiumID;
            case CRACKED -> GeneralUtil.getCrackedUUIDFromName(name);
        };
    }

    /**
     * Anonymizes a raw IP address based on the configured privacy storage mode.
     *
     * @param rawIp the raw IP address string
     * @return the raw IP or HMAC-SHA256 hashed IP string
     */
    public String anonymizeIP(@Nullable String rawIp) {
        if (rawIp == null) return null;
        var mode = configuration.get(IP_STORAGE_MODE);
        if ("HASHED".equalsIgnoreCase(mode)) {
            var salt = configuration.get(IP_HASH_SALT);
            return CryptoUtil.hmacSha256(rawIp, salt);
        }
        return rawIp;
    }

    /** Shuts down database connections, integration listeners, and background executor pools. */
    protected void disable() {
        if (commandProvider != null) {
            try {
                commandProvider.unregister();
            } catch (Throwable t) {
                logger.error("Failed to unregister commands cleanly", t);
            }
        }
        var filter = logFilter;
        logFilter = null;
        if (filter != null) {
            try {
                filter.remove();
            } catch (Throwable ignored) {
            }
        }
        shuttingDown = true;
        shutdownPool(LOGIN_EXECUTOR);
        LOGIN_EXECUTOR = null;
        shutdownPool(EXECUTOR);
        EXECUTOR = null;
        if (redisService != null) {
            try {
                redisService.shutdown();
            } catch (Exception e) {
                logger.error("Failed to shutdown Redis service cleanly: {}", e.getMessage());
            }
        }
        if (databaseConnector != null) {
            try {
                databaseConnector.disconnect();
            } catch (Exception e) {
                logger.error("Failed to cleanly disconnect from database connector.", e);
            }
        }
        if (serverHandler != null) {
            try {
                serverHandler.shutdown();
            } catch (Throwable ignored) {
            }
        }
        if (luckpermsApi != null) {
            try {
                luckpermsApi.disable();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public HoconPluginConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public HoconMessages getMessages() {
        return messages;
    }

    @Override
    public void checkDataFolder() {
        var folder = getDataFolder();

        if (!folder.exists()) {
            if (!folder.mkdir()) {
                throw new RuntimeException("Failed to create datafolder");
            }
        }
    }

    protected abstract Logger provideLogger();

    public abstract CommandManager<?, ?, ?, ?, ?, ?> provideManager();

    public abstract P getPlayerFromIssuer(CommandIssuer issuer);

    public abstract P getPlayerForUUID(UUID uuid);

    /**
     * Kicks a player connected to this local platform node.
     *
     * @param player the player instance
     * @param reason the kick reason
     */
    public void kickPlayer(P player, xyz.kyngs.librelogin.lib.adventure.text.Component reason) {
        if (player != null && platformHandle != null) {
            platformHandle.kick(player, reason);
        }
    }

    public abstract void authorize(P player, User user, Audience audience);

    public abstract CancellableTask delay(Runnable runnable, long delayInMillis);

    public abstract CancellableTask repeat(
            Runnable runnable, long delayInMillis, long repeatInMillis);

    public abstract boolean pluginPresent(String pluginName);

    protected abstract AuthenticImageProjector<P, S> provideImageProjector();

    /**
     * Resolves a premium user by username, throwing an {@link InvalidCommandArgument} if resolution
     * fails.
     *
     * @param username the player username
     * @return the resolved premium user profile
     * @throws InvalidCommandArgument if resolution fails or is rate-limited
     */
    public PremiumUser getUserOrThrowICA(String username) throws InvalidCommandArgument {
        try {
            return getPremiumProvider().getUserForName(username);
        } catch (PremiumException e) {
            throw new InvalidCommandArgument(
                    getMessages()
                            .getMessage(
                                    switch (e.getIssue()) {
                                        case THROTTLED -> "error.premium-throttled";
                                        default -> "error.premium-unknown";
                                    }));
        }
    }

    @Override
    public AuthenticAuthorizationProvider<P, S> getAuthorizationProvider() {
        return authorizationProvider;
    }

    @Override
    public CryptoProvider getCryptoProvider(String id) {
        return cryptoProviders.get(id);
    }

    @Override
    public void registerCryptoProvider(CryptoProvider provider) {
        cryptoProviders.put(provider.getIdentifier(), provider);
    }

    @Override
    public CryptoProvider getDefaultCryptoProvider() {
        var provider = getCryptoProvider(configuration.get(DEFAULT_CRYPTO_PROVIDER));
        if (provider == null || provider instanceof VerifyOnlyCryptoProvider) {
            // Unknown or verify-only algorithms can never hash new passwords.
            return getCryptoProvider(DEFAULT_CRYPTO_PROVIDER.defaultValue());
        }
        return provider;
    }

    @Override
    public void migrate(ReadDatabaseProvider from, WriteDatabaseProvider to) {
        logger.info("Reading legacy authentication records...");
        var users = from.getAllUsers();
        logger.info("Read {} users, writing records to destination database...", users.size());
        to.insertUsers(users);
        logger.info("Migration completed successfully.");
    }

    @Override
    public AuthenticEventProvider<P, S> getEventProvider() {
        return eventProvider;
    }

    public LoginTryListener<P, S> getLoginTryListener() {
        return loginTryListener;
    }

    /**
     * Handles player quit/disconnect logic: cancels registered per-session tasks and optionally
     * saves last server.
     *
     * @param player the exiting player
     */
    public void onExit(P player) {
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (uuid != null) {
            P current = getPlayerForUUID(uuid);
            if (current != null && current != player) {
                // Player has already reconnected with a newer session! Abort stale disconnect
                // cleanup.
                return;
            }
            cancelOnExit.removeAll(uuid).forEach(CancellableTask::cancel);
            localOnlinePlayers.remove(uuid);
            if (redisService != null
                    && redisService.isConnected()
                    && redisService.getPresenceProvider() != null) {
                redisService.getPresenceProvider().unregisterPlayer(uuid);
            }
        }
        if (configuration.get(REMEMBER_LAST_SERVER)) {
            var server = platformHandle.getPlayersServerName(player);
            if (server == null) return;
            if (uuid == null) return;
            GeneralUtil.runAsync(() -> saveLastServer(uuid, server))
                    .exceptionally(
                            throwable -> {
                                logger.error(
                                        "Failed to update last server for player "
                                                + uuid
                                                + " upon exit",
                                        throwable);
                                return null;
                            });
        }
    }

    private void saveLastServer(UUID uuid, String server) {
        var user = databaseProvider.getByUUID(uuid);
        if (user != null && !getConfiguration().get(LIMBO).contains(server)) {
            user.setLastServer(server);
            databaseProvider.updateUser(user);
        }
    }

    /**
     * Synchronously saves the last server of every given (still online) player. Used on shutdown,
     * where the platform disables plugins before players quit, so the asynchronous save in {@link
     * #onExit} would never run.
     *
     * @param players the online players
     */
    protected void saveLastServersNow(Collection<? extends P> players) {
        if (databaseProvider == null || !configuration.get(REMEMBER_LAST_SERVER)) {
            return;
        }
        for (P player : players) {
            var uuid = platformHandle.getUUIDForPlayer(player);
            var server = platformHandle.getPlayersServerName(player);
            if (uuid == null || server == null) {
                continue;
            }
            try {
                saveLastServer(uuid, server);
            } catch (RuntimeException e) {
                logger.error("Failed to save last server for player " + uuid + " on shutdown", e);
            }
        }
    }

    /**
     * Registers a local player session and records presence in Redis if enabled.
     *
     * @param uuid the unique identifier of the connecting player
     */
    public void registerLocalPlayer(@Nullable UUID uuid) {
        if (uuid == null) return;
        localOnlinePlayers.add(uuid);
        if (redisService != null
                && redisService.isConnected()
                && redisService.getPresenceProvider() != null) {
            redisService.getPresenceProvider().registerPlayer(uuid);
        }
    }

    private void sendRedisHeartbeat() {
        if (redisService != null
                && redisService.isConnected()
                && redisService.getPresenceProvider() != null) {
            redisService.getPresenceProvider().heartbeat(localOnlinePlayers);
        }
    }

    /**
     * Returns the active Lettuce Redis service if enabled.
     *
     * @return the {@link RedisService} instance, or {@code null} if disabled
     */
    @Nullable
    public RedisService getRedisService() {
        return redisService;
    }

    @Override
    public boolean isPresent(UUID uuid) {
        if (redisService != null
                && redisService.isConnected()
                && redisService.getPresenceProvider() != null) {
            return redisService.getPresenceProvider().isPlayerOnline(uuid);
        }
        return getPlayerForUUID(uuid) != null;
    }

    @Override
    public boolean multiProxyEnabled() {
        return redisService != null && redisService.isConnected();
    }

    /**
     * Registers a cancellable task that must be cancelled when the specified player exits the
     * proxy/server.
     *
     * @param task the task to cancel
     * @param player the player session
     */
    public void cancelOnExit(CancellableTask task, P player) {
        if (player == null || task == null) return;
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (uuid != null) {
            cancelOnExit.put(uuid, task);
        }
    }

    /**
     * Cancels and removes all pending session tasks registered for the specified player UUID.
     *
     * @param uuid the player UUID
     */
    public void cancelTasksForPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            cancelOnExit.removeAll(uuid).forEach(CancellableTask::cancel);
        }
    }

    public boolean floodgateEnabled() {
        return floodgateApi != null;
    }

    public boolean luckpermsEnabled() {
        return luckpermsApi != null;
    }

    public boolean fromFloodgate(UUID uuid) {
        return floodgateApi != null && uuid != null && floodgateApi.isFloodgateId(uuid);
    }

    /**
     * Stops the platform after a fatal startup error. Implementations must not return normally:
     * they throw {@link ShutdownException} so {@code enable()} stops at the failing step. A plugin
     * never terminates the JVM itself.
     *
     * @param code exit status code (informational)
     */
    public void shutdownProxy(int code) {
        throw new ShutdownException("Fatal LibreLogin Next startup error (code " + code + ")");
    }

    public abstract Audience getAudienceFromIssuer(CommandIssuer issuer);

    public boolean mainThread() {
        return false;
    }

    /** Emits a warning if called on the main server thread. */
    public void reportMainThread() {
        if (mainThread()) {
            logger.error(
                    "Blocking I/O operation detected on main server thread! Please report this"
                            + " stack trace:",
                    new IllegalStateException("Main-thread blocking I/O violation"));
        }
    }

    public boolean fromFloodgate(String username) {
        return floodgateApi != null && floodgateApi.getPlayer(username) != null;
    }

    protected java.util.logging.Logger getSimpleLogger() {
        return null;
    }

    private static void shutdownPool(ExecutorService pool) {
        if (pool == null || pool.isShutdown()) {
            return;
        }
        try {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
