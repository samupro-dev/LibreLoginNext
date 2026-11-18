/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.crypto.LegacyHashNormalizer;
import xyz.kyngs.librelogin.common.database.AuthenticDatabaseProvider;
import xyz.kyngs.librelogin.common.database.AuthenticUser;

/**
 * Abstract SQL-based implementation of {@link AuthenticDatabaseProvider} managing user data
 * persistence across various SQL engines (MySQL, PostgreSQL, SQLite).
 */
public abstract class LibreLoginSQLDatabaseProvider
        extends AuthenticDatabaseProvider<SQLDatabaseConnector> {

    private final Cache<UUID, User> userByUuidCache;
    // Secondary keys map to the UUID only: evicting an account is then a single invalidation of
    // userByUuidCache, and a stale secondary entry is detected by checking the cached user's
    // fields.
    private final Cache<String, UUID> userByNameCache;
    private final Cache<UUID, UUID> userByPremiumUuidCache;

    /**
     * Constructs a new SQL database provider instance.
     *
     * @param connector the SQL connector to use for query execution
     * @param plugin the plugin instance
     */
    public LibreLoginSQLDatabaseProvider(
            SQLDatabaseConnector connector, AuthenticLibreLogin<?, ?> plugin) {
        super(connector, plugin);
        this.userByUuidCache = newCache();
        this.userByNameCache = newCache();
        this.userByPremiumUuidCache = newCache();
    }

    /**
     * Entries live 5 minutes when Redis broadcasts invalidations to the other instances, and only
     * 30 seconds otherwise: without that channel another proxy or backend on the same database
     * could change the row, and a long-lived stale copy would be written back by updateUser.
     */
    private <K, V> Cache<K, V> newCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(
                        new Expiry<K, V>() {
                            private long ttl() {
                                // Only while invalidations are actually received.
                                var redis = plugin.getRedisService();
                                return redis != null && redis.isConnected()
                                        ? TimeUnit.MINUTES.toNanos(5)
                                        : TimeUnit.SECONDS.toNanos(30);
                            }

                            @Override
                            public long expireAfterCreate(K key, V value, long currentTime) {
                                return ttl();
                            }

                            @Override
                            public long expireAfterUpdate(
                                    K key, V value, long currentTime, long currentDuration) {
                                return ttl();
                            }

                            @Override
                            public long expireAfterRead(
                                    K key, V value, long currentTime, long currentDuration) {
                                return currentDuration;
                            }
                        })
                .build();
    }

    private void cacheUser(User user) {
        if (user == null) return;
        userByUuidCache.put(user.getUuid(), user);
        if (user.getLastNickname() != null) {
            userByNameCache.put(user.getLastNickname().toLowerCase(Locale.ROOT), user.getUuid());
        }
        if (user.getPremiumUUID() != null) {
            userByPremiumUuidCache.put(user.getPremiumUUID(), user.getUuid());
        }
    }

    /**
     * Evicts the given account. Name and premium-UUID entries only point at the UUID, so they stop
     * resolving with it; users are changed in place (rename, premium switch), which is why a hit is
     * only trusted when the cached user still has that name or premium UUID.
     */
    private void evictAllKeysFor(UUID uuid) {
        if (uuid == null) return;
        userByUuidCache.invalidate(uuid);
    }

    private @Nullable User cachedByName(String lowerName) {
        var uuid = userByNameCache.getIfPresent(lowerName);
        var user = uuid == null ? null : userByUuidCache.getIfPresent(uuid);
        return user != null
                        && user.getLastNickname() != null
                        && user.getLastNickname().toLowerCase(Locale.ROOT).equals(lowerName)
                ? user
                : null;
    }

    private @Nullable User cachedByPremiumUuid(UUID premiumUuid) {
        var uuid = userByPremiumUuidCache.getIfPresent(premiumUuid);
        var user = uuid == null ? null : userByUuidCache.getIfPresent(uuid);
        return user != null && premiumUuid.equals(user.getPremiumUUID()) ? user : null;
    }

    /**
     * Invalidates any cached user entries for the given UUID.
     *
     * @param uuid the unique identifier of the user to invalidate
     */
    public void invalidateUserByUuid(UUID uuid) {
        evictAllKeysFor(uuid);
    }

    @Override
    public long countRegisteredByIP(String ip) {
        plugin.reportMainThread();
        return connector.runQuery(connection -> {
            try (var ps =
                    connection.prepareStatement(
                            "SELECT COUNT(*) FROM librepremium_data WHERE ip=? AND hashed_password"
                                    + " IS NOT NULL")) {
                ps.setString(1, ip);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public Collection<User> getByIP(String ip) {
        plugin.reportMainThread();
        return connector.runQuery(connection -> {
            try (var ps =
                    connection.prepareStatement("SELECT * FROM librepremium_data WHERE ip=?")) {
                ps.setString(1, ip);
                try (var rs = ps.executeQuery()) {
                    var users = new ArrayList<User>();
                    User user;
                    while ((user = getUserFromResult(rs)) != null) {
                        users.add(user);
                        cacheUser(user);
                    }
                    return users;
                }
            }
        });
    }

    @Override
    public User getByName(String name) {
        if (name == null) return null;
        var lower = name.toLowerCase(Locale.ROOT);
        var cached = cachedByName(lower);
        if (cached != null) return cached;
        plugin.reportMainThread();
        // Resolved before borrowing a connection: on MySQL the first call runs its own query.
        var sql = nicknameLookupQuery();
        var user =
                connector.runQuery(connection -> {
                    try (var ps = connection.prepareStatement(sql)) {
                        ps.setString(1, name);
                        try (var rs = ps.executeQuery()) {
                            return getUserFromResult(rs);
                        }
                    }
                });
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    @Override
    public Collection<User> getAllUsers() {
        plugin.reportMainThread();
        return connector.runQuery(connection -> {
            try (var ps = connection.prepareStatement("SELECT * FROM librepremium_data");
                    var rs = ps.executeQuery()) {
                var users = new ArrayList<User>();
                User user;
                while ((user = getUserFromResult(rs)) != null) {
                    users.add(user);
                    cacheUser(user);
                }
                return users;
            }
        });
    }

    @Override
    public User getByUUID(UUID uuid) {
        if (uuid == null) return null;
        var cached = userByUuidCache.getIfPresent(uuid);
        if (cached != null) return cached;
        plugin.reportMainThread();
        var user =
                connector.runQuery(connection -> {
                    try (var ps =
                            connection.prepareStatement(
                                    "SELECT * FROM librepremium_data WHERE uuid=?")) {
                        ps.setString(1, uuid.toString());
                        try (var rs = ps.executeQuery()) {
                            return getUserFromResult(rs);
                        }
                    }
                });
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    @Override
    public User getByPremiumUUID(UUID uuid) {
        if (uuid == null) return null;
        var cached = cachedByPremiumUuid(uuid);
        if (cached != null) return cached;
        plugin.reportMainThread();
        var user =
                connector.runQuery(connection -> {
                    try (var ps =
                            connection.prepareStatement(
                                    "SELECT * FROM librepremium_data WHERE premium_uuid=?")) {
                        ps.setString(1, uuid.toString());
                        try (var rs = ps.executeQuery()) {
                            return getUserFromResult(rs);
                        }
                    }
                });
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    @Nullable
    private User getUserFromResult(ResultSet rs) throws SQLException {
        if (rs.next()) {
            var id = UUID.fromString(rs.getString("uuid"));
            var premiumUUID = rs.getString("premium_uuid");
            var hashedPassword = rs.getString("hashed_password");
            var salt = rs.getString("salt");
            var algo = rs.getString("algo");
            var lastNickname = rs.getString("last_nickname");
            var joinDate = rs.getTimestamp("joined");
            var lastSeen = rs.getTimestamp("last_seen");

            return new AuthenticUser(
                    id,
                    premiumUUID == null ? null : UUID.fromString(premiumUUID),
                    hashedPassword == null
                            ? null
                            : LegacyHashNormalizer.normalize(
                                    new HashedPassword(hashedPassword, salt, algo)),
                    lastNickname,
                    joinDate,
                    lastSeen,
                    rs.getString("secret"),
                    rs.getString("ip"),
                    rs.getTimestamp("last_authentication"),
                    rs.getString("last_server"),
                    rs.getString("email"));
        } else {
            return null;
        }
    }

    @Override
    public void insertUser(User user) {
        plugin.reportMainThread();
        connector.runQuery(connection -> {
            try (var ps =
                    connection.prepareStatement(
                            "INSERT INTO librepremium_data(uuid, premium_uuid,"
                                    + " hashed_password, salt, algo, last_nickname, joined,"
                                    + " last_seen, secret, ip, last_authentication,"
                                    + " last_server, email) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,"
                                    + " ?, ?, ?, ?)")) {
                insertToStatement(ps, user);
                ps.executeUpdate();
            }
        });
        cacheUser(user);
    }

    @Override
    public void insertUsers(Collection<User> users) {
        if (users == null || users.isEmpty()) {
            logMigrationSummary(0, 0, 0, 0, 0);
            return;
        }
        plugin.reportMainThread();
        var valid = new ArrayList<User>(users.size());
        for (User user : users) {
            var name = user.getLastNickname();
            if (user.getUuid() == null || name == null || name.isBlank() || name.length() > 255) {
                plugin.getLogger()
                        .warn(
                                "Skipping invalid migrated row (uuid={}, name={})",
                                user.getUuid(),
                                name);
                continue;
            }
            valid.add(user);
        }
        var invalid = users.size() - valid.size();
        if (valid.isEmpty()) {
            logMigrationSummary(users.size(), 0, 0, invalid, 0);
            return;
        }
        // Counted on the table: batch update counts are unreliable across drivers.
        long before =
                connector.runQuery(connection -> {
                    return countUsers(connection);
                });
        int failed = 0;
        // Bounded chunks, each on its own connection and transaction: a long import no longer
        // holds one connection past the leak-detection threshold, and a failed chunk only
        // retries its own rows one by one.
        for (int from = 0; from < valid.size(); from += INSERT_CHUNK_SIZE) {
            var chunk = valid.subList(from, Math.min(valid.size(), from + INSERT_CHUNK_SIZE));
            failed +=
                    connector.runQuery(connection -> {
                        return insertChunk(connection, chunk);
                    });
        }
        long imported =
                connector.runQuery(connection -> {
                    return countUsers(connection);
                })
                        - before;
        logMigrationSummary(
                users.size(), imported, valid.size() - imported - failed, invalid, failed);
        for (User user : valid) {
            cacheUser(user);
        }
    }

    private static final int INSERT_CHUNK_SIZE = 1000;

    private int insertChunk(java.sql.Connection connection, List<User> chunk) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (var ps =
                    connection.prepareStatement(
                            "INSERT "
                                    + getIgnoreSyntax()
                                    + " INTO librepremium_data(uuid, premium_uuid, hashed_password,"
                                    + " salt, algo, last_nickname, joined, last_seen, secret, ip,"
                                    + " last_authentication, last_server, email) VALUES (?, ?, ?,"
                                    + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                                    + getIgnoreSuffix())) {
                for (User user : chunk) {
                    insertToStatement(ps, user);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            return 0;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            // One bad row aborted the batch (e.g. on PostgreSQL): insert row by row and report
            // each failure instead of losing every account of the chunk.
            plugin.getLogger()
                    .warn("Batch insert failed ({}); retrying row by row.", e.getMessage());
            connection.setAutoCommit(true);
            return insertOneByOne(connection, chunk);
        } finally {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    private long countUsers(java.sql.Connection connection) throws SQLException {
        try (var ps = connection.prepareStatement("SELECT COUNT(*) FROM librepremium_data");
                var rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private void logMigrationSummary(
            long read, long imported, long duplicates, long invalid, long failed) {
        plugin.getLogger()
                .info(
                        "Migration summary: {} read, {} imported, {} already present (UUID, premium"
                                + " UUID or name; ignored), {} invalid (skipped), {} failed.",
                        read,
                        imported,
                        duplicates,
                        invalid,
                        failed);
    }

    private int insertOneByOne(java.sql.Connection connection, Collection<User> users) {
        int failed = 0;
        for (User user : users) {
            try (var ps =
                    connection.prepareStatement(
                            "INSERT "
                                    + getIgnoreSyntax()
                                    + " INTO librepremium_data(uuid, premium_uuid,"
                                    + " hashed_password, salt, algo, last_nickname, joined,"
                                    + " last_seen, secret, ip, last_authentication,"
                                    + " last_server, email) VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
                                    + " ?, ?, ?, ?, ?)"
                                    + getIgnoreSuffix())) {
                insertToStatement(ps, user);
                ps.executeUpdate();
            } catch (SQLException e) {
                failed++;
                plugin.getLogger()
                        .error(
                                "Could not import user {} ({}): {}",
                                user.getLastNickname(),
                                user.getUuid(),
                                e.getMessage());
            }
        }
        if (failed > 0) {
            plugin.getLogger()
                    .error(
                            "{} of {} migrated users could not be imported; see above.",
                            failed,
                            users.size());
        }
        return failed;
    }

    private void insertToStatement(PreparedStatement ps, User user) throws SQLException {
        ps.setString(1, user.getUuid().toString());
        ps.setString(2, user.getPremiumUUID() == null ? null : user.getPremiumUUID().toString());
        ps.setString(3, user.getHashedPassword() == null ? null : user.getHashedPassword().hash());
        ps.setString(4, user.getHashedPassword() == null ? null : user.getHashedPassword().salt());
        ps.setString(5, user.getHashedPassword() == null ? null : user.getHashedPassword().algo());
        ps.setString(6, user.getLastNickname());
        ps.setTimestamp(7, user.getJoinDate());
        ps.setTimestamp(8, user.getLastSeen());
        ps.setString(9, user.getSecret());
        ps.setString(10, user.getIp());
        ps.setTimestamp(11, user.getLastAuthentication());
        ps.setString(12, user.getLastServer());
        ps.setString(13, user.getEmail());
    }

    @Override
    public void updateUser(User user) {
        plugin.reportMainThread();
        try {
            runUpdate(user);
        } catch (RuntimeException e) {
            // The object may already hold the uncommitted change: never keep serving it.
            evictAllKeysFor(user.getUuid());
            throw e;
        }
        evictAllKeysFor(user.getUuid());
        cacheUser(user);
        broadcastInvalidate(user.getUuid());
    }

    private void runUpdate(User user) {
        connector.runQuery(connection -> {
            try (var ps =
                    connection.prepareStatement(
                            "UPDATE librepremium_data SET premium_uuid=?,"
                                    + " hashed_password=?, salt=?, algo=?, last_nickname=?,"
                                    + " joined=?, last_seen=?, secret=?, ip=?,"
                                    + " last_authentication=?, last_server=?, email=? WHERE"
                                    + " uuid=?")) {
                ps.setString(
                        1, user.getPremiumUUID() == null ? null : user.getPremiumUUID().toString());
                ps.setString(
                        2,
                        user.getHashedPassword() == null ? null : user.getHashedPassword().hash());
                ps.setString(
                        3,
                        user.getHashedPassword() == null ? null : user.getHashedPassword().salt());
                ps.setString(
                        4,
                        user.getHashedPassword() == null ? null : user.getHashedPassword().algo());
                ps.setString(5, user.getLastNickname());
                ps.setTimestamp(6, user.getJoinDate());
                ps.setTimestamp(7, user.getLastSeen());
                ps.setString(8, user.getSecret());
                ps.setString(9, user.getIp());
                ps.setTimestamp(10, user.getLastAuthentication());
                ps.setString(11, user.getLastServer());
                ps.setString(12, user.getEmail());
                ps.setString(13, user.getUuid().toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteUser(User user) {
        plugin.reportMainThread();
        connector.runQuery(connection -> {
            try (var ps =
                    connection.prepareStatement("DELETE FROM librepremium_data WHERE uuid=?")) {
                ps.setString(1, user.getUuid().toString());
                ps.executeUpdate();
            }
        });
        evictAllKeysFor(user.getUuid());
        broadcastInvalidate(user.getUuid());
    }

    private void broadcastInvalidate(UUID uuid) {
        if (uuid == null) return;
        var redisService = plugin.getRedisService();
        if (redisService != null && redisService.getPubSubHandler() != null) {
            redisService.getPubSubHandler().publishInvalidateUser(uuid);
        }
    }

    /**
     * Drops every cached user. Called when the Redis connection goes up or down: invalidations sent
     * while it was down are lost, so cached copies may be stale.
     */
    public void invalidateAllUsers() {
        userByUuidCache.invalidateAll();
        userByNameCache.invalidateAll();
        userByPremiumUuidCache.invalidateAll();
    }

    @Override
    public int purgeInactiveUsers(int days) {
        if (days <= 0) return 0;
        plugin.reportMainThread();
        var cutoff = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(days));
        int affected = deleteInBatches("last_seen < ?", cutoff);
        userByUuidCache.invalidateAll();
        userByNameCache.invalidateAll();
        userByPremiumUuidCache.invalidateAll();
        return affected;
    }

    @Override
    public int purgeAbandonedUsers(int days) {
        if (days <= 0) return 0;
        plugin.reportMainThread();
        var cutoff = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(days));
        int affected =
                deleteInBatches(
                        "hashed_password IS NULL AND premium_uuid IS NULL"
                                + " AND last_authentication IS NULL AND secret IS NULL"
                                + " AND email IS NULL AND joined < ?"
                                // also unseen since the cutoff, so an online player is never hit
                                + " AND (last_seen IS NULL OR last_seen < ?)",
                        cutoff,
                        cutoff);
        if (affected > 0) {
            userByUuidCache.invalidateAll();
            userByNameCache.invalidateAll();
            userByPremiumUuidCache.invalidateAll();
        }
        return affected;
    }

    private static final int DELETE_BATCH = 500;

    /**
     * Deletes matching rows in batches of {@value #DELETE_BATCH}, one short transaction each, with
     * SQL every supported engine accepts (SELECT ... LIMIT, then DELETE ... WHERE uuid IN).
     */
    private int deleteInBatches(String where, Object... parameters) {
        int total = 0;
        while (true) {
            int deleted =
                    connector.runQuery(connection -> {
                        var uuids = new ArrayList<String>();
                        try (var ps =
                                connection.prepareStatement(
                                        "SELECT uuid FROM librepremium_data WHERE "
                                                + where
                                                + " LIMIT "
                                                + DELETE_BATCH)) {
                            for (int i = 0; i < parameters.length; i++) {
                                ps.setObject(i + 1, parameters[i]);
                            }
                            try (var rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    uuids.add(rs.getString(1));
                                }
                            }
                        }
                        if (uuids.isEmpty()) {
                            return 0;
                        }
                        var marks =
                                String.join(",", java.util.Collections.nCopies(uuids.size(), "?"));
                        try (var ps =
                                connection.prepareStatement(
                                        "DELETE FROM librepremium_data WHERE uuid IN ("
                                                + marks
                                                + ")")) {
                            for (int i = 0; i < uuids.size(); i++) {
                                ps.setString(i + 1, uuids.get(i));
                            }
                            return ps.executeUpdate();
                        }
                    });
            total += deleted;
            if (deleted < DELETE_BATCH) {
                return total;
            }
        }
    }

    @Override
    public void archiveUser(User user) {
        plugin.reportMainThread();
        connector.runQuery(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                try (var ps =
                        connection.prepareStatement(
                                "INSERT INTO librepremium_data_archive (uuid, premium_uuid,"
                                    + " hashed_password, salt, algo, last_nickname, joined,"
                                    + " last_seen, secret, ip, last_authentication, last_server,"
                                    + " email, archived_at) SELECT uuid, premium_uuid,"
                                    + " hashed_password, salt, algo, last_nickname, joined,"
                                    + " last_seen, secret, ip, last_authentication, last_server,"
                                    + " email, CURRENT_TIMESTAMP FROM librepremium_data WHERE"
                                    + " uuid=?")) {
                    ps.setString(1, user.getUuid().toString());
                    ps.executeUpdate();
                }
                try (var ps =
                        connection.prepareStatement("DELETE FROM librepremium_data WHERE uuid=?")) {
                    ps.setString(1, user.getUuid().toString());
                    ps.executeUpdate();
                }
                connection.commit();
                return null;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException ignored) {
                }
            }
        });
        evictAllKeysFor(user.getUuid());
        broadcastInvalidate(user.getUuid());
    }

    @Override
    public long getCachedUsersCount() {
        return userByUuidCache.estimatedSize();
    }

    @Override
    public void validateSchema() {
        connector.runQuery(connection -> {
            // Fresh tables get uniqueness from the column constraints below; the extra unique
            // indexes are only for tables created by older versions without them.
            boolean existed = !getColumnNames(connection).isEmpty();

            try (var ps =
                    connection.prepareStatement(
                            "CREATE TABLE IF NOT EXISTS librepremium_data("
                                    + "uuid VARCHAR(255) NOT NULL PRIMARY KEY,"
                                    + "premium_uuid VARCHAR(255) UNIQUE,"
                                    + "hashed_password VARCHAR(255),"
                                    + "salt VARCHAR(255),"
                                    + "algo VARCHAR(255),"
                                    + "last_nickname VARCHAR(255) NOT NULL UNIQUE,"
                                    + "joined TIMESTAMP NULL DEFAULT NULL,"
                                    + "last_seen TIMESTAMP NULL DEFAULT NULL,"
                                    + "last_server VARCHAR(255)"
                                    + ")")) {
                ps.executeUpdate();
            }

            var columns = getColumnNames(connection);

            if (existed)
                try (var ps = connection.prepareStatement(addUnique("premium_uuid"))) {
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                    // Index already exists
                }

            if (!columns.contains("secret")) {
                try (var ps =
                        connection.prepareStatement(
                                "ALTER TABLE librepremium_data ADD COLUMN secret VARCHAR(255) NULL"
                                        + " DEFAULT NULL")) {
                    ps.executeUpdate();
                }
            }
            if (!columns.contains("ip")) {
                try (var ps =
                        connection.prepareStatement(
                                "ALTER TABLE librepremium_data ADD COLUMN ip VARCHAR(255) NULL"
                                        + " DEFAULT NULL")) {
                    ps.executeUpdate();
                }
            }
            if (!columns.contains("last_authentication")) {
                try (var ps =
                        connection.prepareStatement(
                                "ALTER TABLE librepremium_data ADD COLUMN last_authentication"
                                        + " TIMESTAMP NULL DEFAULT NULL")) {
                    ps.executeUpdate();
                }
            }
            if (!columns.contains("last_server")) {
                try (var ps =
                        connection.prepareStatement(
                                "ALTER TABLE librepremium_data ADD COLUMN last_server VARCHAR(255)"
                                        + " NULL DEFAULT NULL")) {
                    ps.executeUpdate();
                }
            }
            if (!columns.contains("email")) {
                try (var ps =
                        connection.prepareStatement(
                                "ALTER TABLE librepremium_data ADD COLUMN email VARCHAR(255) NULL"
                                        + " DEFAULT NULL")) {
                    ps.executeUpdate();
                }
            }

            if (existed)
                try (var ps = connection.prepareStatement(addUnique("last_nickname"))) {
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                    // Index already exists
                }

            try (var ps = connection.prepareStatement(addIndex("ip_index", "ip"))) {
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Index already exists
            }

            try (var ps = connection.prepareStatement(addIndex("last_seen_index", "last_seen"))) {
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Index already exists
            }

            try (var ps =
                    connection.prepareStatement(
                            "CREATE TABLE IF NOT EXISTS librepremium_data_archive AS SELECT * FROM"
                                    + " librepremium_data WHERE 1=0")) {
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Already exists
            }
            try (var ps =
                    connection.prepareStatement(
                            "ALTER TABLE librepremium_data_archive ADD COLUMN archived_at TIMESTAMP"
                                    + " NULL")) {
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Column already exists
            }

            var nicknameIndex = addNicknameLookupIndex();
            if (nicknameIndex != null) {
                try (var ps = connection.prepareStatement(nicknameIndex)) {
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                    // Index already exists or unsupported by this engine version
                }
            }
        });
    }

    /**
     * Returns the case-insensitive lookup by name. Names are unique case-insensitively.
     *
     * @return the SQL query with one parameter (the name)
     */
    protected String nicknameLookupQuery() {
        return "SELECT * FROM librepremium_data WHERE LOWER(last_nickname)=LOWER(?)";
    }

    /**
     * Returns the statement creating an index usable by {@link #nicknameLookupQuery()}, so a
     * pre-login lookup of an unknown name is not a full table scan.
     *
     * @return the SQL statement, or {@code null} if the engine needs none
     */
    protected String addNicknameLookupIndex() {
        // Expression index: supported by PostgreSQL and SQLite (3.9+).
        return "CREATE INDEX IF NOT EXISTS last_nickname_lower_index ON"
                + " librepremium_data(LOWER(last_nickname))";
    }

    /**
     * Retrieves the existing column names of the {@code librepremium_data} table.
     *
     * @param connection the active database connection
     * @return a list of column names present in the table
     * @throws SQLException if a database access error occurs
     */
    protected abstract List<String> getColumnNames(Connection connection) throws SQLException;

    /**
     * Returns dialect-specific insert ignore syntax keyword (e.g. {@code "IGNORE"} or {@code "OR
     * IGNORE"}).
     *
     * @return the SQL ignore syntax prefix, or empty string if not applicable
     */
    protected String getIgnoreSyntax() {
        return "";
    }

    /**
     * Returns dialect-specific insert ignore suffix clause (e.g. {@code " ON CONFLICT DO
     * NOTHING"}).
     *
     * @return the SQL ignore suffix clause, or empty string if not applicable
     */
    protected String getIgnoreSuffix() {
        return "";
    }

    /**
     * Generates a dialect-specific SQL statement to add a unique index on the given column.
     *
     * @param column the column name to create a unique index for
     * @return the SQL statement to execute
     */
    protected abstract String addUnique(String column);

    /**
     * Generates a dialect-specific SQL statement to add an index on the given column.
     *
     * @param indexName the index name to create
     * @param column the column name to create an index for
     * @return the SQL statement to execute
     */
    protected abstract String addIndex(String indexName, String column);
}
