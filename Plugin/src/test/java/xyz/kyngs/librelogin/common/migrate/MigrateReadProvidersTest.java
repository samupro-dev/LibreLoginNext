/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.password4j.BcryptFunction;
import com.password4j.Password;
import com.password4j.types.Bcrypt;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.crypto.Argon2IDCryptoProvider;
import xyz.kyngs.librelogin.common.crypto.BCrypt2ACryptoProvider;
import xyz.kyngs.librelogin.common.crypto.LegacyMessageDigestCryptoProvider;
import xyz.kyngs.librelogin.common.crypto.NavAuthPBKDF2CryptoProvider;

class MigrateReadProvidersTest {

    private static final Logger NO_OP =
            new Logger() {
                @Override
                public void info(String message) {}

                @Override
                public void info(String message, Throwable throwable) {}

                @Override
                public void warn(String message) {}

                @Override
                public void warn(String message, Throwable throwable) {}

                @Override
                public void error(String message) {}

                @Override
                public void error(String message, Throwable throwable) {}

                @Override
                public void debug(String message) {}

                @Override
                public void debug(String message, Throwable throwable) {}
            };

    private static final UUID ALICE = UUID.fromString("4f1c2a8e-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("4f1c2a8e-0000-4000-8000-000000000002");
    private static final UUID BOB_MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID CAROL = UUID.fromString("4f1c2a8e-0000-4000-8000-000000000003");

    // NavAuth SHACredentialsHasher: PBKDF2, 100 000 iterations, unpadded Base64 salt and hash.
    private static String navAuthPbkdf2(String password, String hmac, int keyBytes)
            throws Exception {
        var salt = "navauth-salt-16b".getBytes(StandardCharsets.US_ASCII);
        var spec = new PBEKeySpec(password.toCharArray(), salt, 100_000, keyBytes * 8);
        var hash =
                SecretKeyFactory.getInstance("PBKDF2With" + hmac).generateSecret(spec).getEncoded();
        var encoder = Base64.getEncoder().withoutPadding();
        var prefix = hmac.equals("HmacSHA512") ? "pbkdf2-sha512" : "pbkdf2-sha256";
        return prefix + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    // NavAuth LibreLoginSHACredentialsHasher: hex(SHA(hex(SHA(password)) + salt)) with %064x.
    private static String sha512Hex(String value) throws Exception {
        var digest =
                MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8));
        return String.format("%064x", new BigInteger(1, digest));
    }

    @Test
    void verifiesNavAuthPbkdf2Hashes() throws Exception {
        var provider = new NavAuthPBKDF2CryptoProvider();
        for (var hash :
                new String[] {
                    navAuthPbkdf2("Hunter2!", "HmacSHA512", 64),
                    navAuthPbkdf2("Hunter2!", "HmacSHA256", 32)
                }) {
            var password = NavAuthSQLMigrateReadProvider.toHashedPassword("SHA512", hash);
            assertEquals(NavAuthPBKDF2CryptoProvider.IDENTIFIER, password.algo());
            assertTrue(provider.matches("Hunter2!", password));
            assertFalse(provider.matches("Hunter3!", password));
        }
        assertNull(provider.createHash("anything"));
        assertFalse(
                provider.matches(
                        "x", new HashedPassword("pbkdf2-md5$AAAA$AAAA", null, "NavAuth-PBKDF2")));
    }

    @Test
    void convertsEveryNavAuthAlgorithm() throws Exception {
        var bcrypt =
                Password.hash("hunter2").with(BcryptFunction.getInstance(Bcrypt.A, 10)).getResult();
        var converted = NavAuthSQLMigrateReadProvider.toHashedPassword("BCRYPT", bcrypt);
        assertTrue(new BCrypt2ACryptoProvider().matches("hunter2", converted));

        // Vector from NavAuth 0.3.0 Argon2CredentialsHasherTests (p=4, 16-byte hash).
        var argon2 =
                NavAuthSQLMigrateReadProvider.toHashedPassword(
                        "ARGON2",
                        "$argon2id$v=19$m=65536,t=3,p=4$cz7sQK/RvBYgFpiBqQxM3w$6fo3usNp7CdtjYZxbfT0OA");
        var argon2Provider = new Argon2IDCryptoProvider(NO_OP);
        assertTrue(argon2Provider.matches("TestPassword123!", argon2));
        assertFalse(argon2Provider.matches("wrong", argon2));

        var salt = "0123456789abcdef";
        var sha =
                NavAuthSQLMigrateReadProvider.toHashedPassword(
                        "LIBRELOGIN_SHA512",
                        "SHA512$" + salt + "$" + sha512Hex(sha512Hex("hunter2") + salt));
        assertEquals("SHA-512", sha.algo());
        var shaProvider = new LegacyMessageDigestCryptoProvider("SHA-512", "SHA-512", false);
        assertTrue(shaProvider.matches("hunter2", sha));
        assertFalse(shaProvider.matches("hunter3", sha));

        assertNull(NavAuthSQLMigrateReadProvider.toHashedPassword("MD5", "abc"));
        assertNull(NavAuthSQLMigrateReadProvider.toHashedPassword(null, "abc"));
        assertNull(NavAuthSQLMigrateReadProvider.toHashedPassword("BCRYPT", "plain"));
        assertNull(
                NavAuthSQLMigrateReadProvider.toHashedPassword("LIBRELOGIN_SHA512", "SHA512$only"));
    }

    @Test
    void readsNavAuthSqliteDatabase(@TempDir Path dir) throws Exception {
        var bcrypt =
                Password.hash("hunter2").with(BcryptFunction.getInstance(Bcrypt.A, 10)).getResult();
        try (var connection = open(dir)) {
            createTables(connection, true);
            execute(
                    connection,
                    "INSERT INTO navauth_users VALUES (?, NULL, 'Alice', 'alice', 1)",
                    ALICE);
            execute(
                    connection,
                    "INSERT INTO navauth_users VALUES (?, ?, 'Bob', 'bob', 0)",
                    BOB,
                    BOB_MOJANG);
            execute(
                    connection,
                    "INSERT INTO navauth_credentials VALUES (?, ?, 'BCRYPT', 'JBSWY3DPEHPK3PXP')",
                    ALICE,
                    bcrypt);
            execute(
                    connection,
                    "INSERT INTO navauth_users_sessions VALUES (?, 1000, 2000, 3232235777)",
                    ALICE);
            execute(
                    connection,
                    "INSERT INTO navauth_users_sessions VALUES (?, 5000, 9000, 167772161)",
                    ALICE);

            var users =
                    new NavAuthSQLMigrateReadProvider(NO_OP, connector(connection)).getAllUsers();
            assertEquals(2, users.size());

            User alice =
                    users.stream().filter(u -> u.getUuid().equals(ALICE)).findFirst().orElseThrow();
            assertEquals("Alice", alice.getLastNickname());
            assertNull(alice.getPremiumUUID());
            assertEquals("JBSWY3DPEHPK3PXP", alice.getSecret());
            assertTrue(new BCrypt2ACryptoProvider().matches("hunter2", alice.getHashedPassword()));
            assertEquals(1000, alice.getJoinDate().getTime());
            assertEquals(9000, alice.getLastSeen().getTime());
            assertEquals("10.0.0.1", alice.getIp());

            User bob =
                    users.stream().filter(u -> u.getUuid().equals(BOB)).findFirst().orElseThrow();
            assertEquals(BOB_MOJANG, bob.getPremiumUUID());
            assertNull(bob.getHashedPassword());
            assertNull(bob.getJoinDate());
        }
    }

    @Test
    void locksAccountsThatCannotLogIn(@TempDir Path dir) throws Exception {
        try (var connection = open(dir)) {
            createTables(connection, false);
            execute(
                    connection,
                    "INSERT INTO navauth_users VALUES (?, NULL, 'Carol', 'carol', 1)",
                    CAROL);
            execute(
                    connection,
                    "INSERT INTO navauth_credentials VALUES (?, NULL, NULL, 'JBSWY3DPEHPK3PXP')",
                    CAROL);
            execute(
                    connection,
                    "INSERT INTO navauth_users VALUES (?, NULL, 'Alice', 'alice', 1)",
                    ALICE);
            execute(
                    connection,
                    "INSERT INTO navauth_credentials VALUES (?, 'md5$abc', 'MD5', NULL)",
                    ALICE);

            var users =
                    new NavAuthSQLMigrateReadProvider(NO_OP, connector(connection)).getAllUsers();
            assertEquals(2, users.size());
            for (User user : users) {
                // Registered, so nobody can /register the name, but no provider can verify it.
                assertTrue(user.isRegistered());
                assertEquals(
                        SQLMigrateReadProvider.LOCKED_ALGORITHM, user.getHashedPassword().algo());
            }
            User carol =
                    users.stream().filter(u -> u.getUuid().equals(CAROL)).findFirst().orElseThrow();
            assertEquals("JBSWY3DPEHPK3PXP", carol.getSecret());
            User alice =
                    users.stream().filter(u -> u.getUuid().equals(ALICE)).findFirst().orElseThrow();
            assertEquals("md5$abc", alice.getHashedPassword().hash());
        }
    }

    @Test
    void abortsOnRowsWithoutAValidUuid(@TempDir Path dir) throws Exception {
        try (var connection = open(dir)) {
            createTables(connection, false);
            execute(
                    connection,
                    "INSERT INTO navauth_users VALUES ('not-a-uuid', NULL, 'Dave', 'dave', 1)");

            var reader = new NavAuthSQLMigrateReadProvider(NO_OP, connector(connection));
            assertThrows(IllegalStateException.class, reader::getAllUsers);
        }
    }

    @Test
    void locksAuthMeAccountsWithUnsupportedHashes(@TempDir Path dir) throws Exception {
        var bcrypt =
                Password.hash("hunter2").with(BcryptFunction.getInstance(Bcrypt.A, 10)).getResult();
        try (var connection = open(dir)) {
            try (var statement = connection.createStatement()) {
                statement.execute(
                        "CREATE TABLE authme (username VARCHAR(255), realname VARCHAR(255),"
                                + " password VARCHAR(255))");
            }
            execute(connection, "INSERT INTO authme VALUES ('erin', 'Erin', ?)", bcrypt);
            execute(connection, "INSERT INTO authme VALUES ('frank', 'Frank', '$MD5$unsupported')");
            execute(connection, "INSERT INTO authme VALUES ('gina', 'Gina', NULL)");

            var users =
                    new AuthMeSQLMigrateReadProvider("authme", NO_OP, connector(connection))
                            .getAllUsers();
            assertEquals(3, users.size());
            User erin =
                    users.stream()
                            .filter(u -> u.getLastNickname().equals("Erin"))
                            .findFirst()
                            .orElseThrow();
            assertTrue(new BCrypt2ACryptoProvider().matches("hunter2", erin.getHashedPassword()));
            User frank =
                    users.stream()
                            .filter(u -> u.getLastNickname().equals("Frank"))
                            .findFirst()
                            .orElseThrow();
            assertTrue(frank.isRegistered());
            assertEquals(SQLMigrateReadProvider.LOCKED_ALGORITHM, frank.getHashedPassword().algo());
            // No password: locked too, so nobody can /register the name.
            User gina =
                    users.stream()
                            .filter(u -> u.getLastNickname().equals("Gina"))
                            .findFirst()
                            .orElseThrow();
            assertTrue(gina.isRegistered());
            assertEquals(SQLMigrateReadProvider.LOCKED_ALGORITHM, gina.getHashedPassword().algo());
        }
    }

    private static Connection open(Path dir) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("navauth.db"));
    }

    // Schema created by NavAuth's ORMLite records (UUIDs as VARCHAR, dates as epoch millis).
    private static void createTables(Connection connection, boolean sessions) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE navauth_users (uuid VARCHAR(48) PRIMARY KEY, mojang_uuid"
                            + " VARCHAR(48), username VARCHAR(255) NOT NULL, username_lowercase"
                            + " VARCHAR(255) NOT NULL, credentials_required BOOLEAN NOT NULL)");
            statement.execute(
                    "CREATE TABLE navauth_credentials (uuid VARCHAR(48) PRIMARY KEY, passwordHash"
                            + " VARCHAR(255), algo VARCHAR(255), twoFactorSecret VARCHAR(255))");
            if (sessions) {
                statement.execute(
                        "CREATE TABLE navauth_users_sessions (uuid VARCHAR(48) NOT NULL, joined_at"
                                + " BIGINT NOT NULL, left_at BIGINT NOT NULL, ip BIGINT)");
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values)
            throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setString(i + 1, values[i].toString());
            }
            statement.executeUpdate();
        }
    }

    private static SQLDatabaseConnector connector(Connection connection) {
        return new SQLDatabaseConnector() {
            @Override
            public void connect() {}

            @Override
            public boolean connected() {
                return true;
            }

            @Override
            public void disconnect() {}

            @Override
            public Connection obtainInterface() {
                return connection;
            }

            @Override
            public <V> V runQuery(ThrowableFunction<Connection, V, SQLException> function) {
                try {
                    return function.apply(connection);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
