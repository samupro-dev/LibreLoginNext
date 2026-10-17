package xyz.kyngs.librelogin.common.database.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KeepaliveTest {

    @Test
    void keepsHikariDefaultForLongLifetimes() {
        assertEquals(120_000, AuthenticDatabaseConnector.keepaliveFor(600_000));
        assertEquals(120_000, AuthenticDatabaseConnector.keepaliveFor(0));
    }

    @Test
    void staysBelowShortLifetimes() {
        assertEquals(60_000, AuthenticDatabaseConnector.keepaliveFor(120_000));
        assertEquals(30_000, AuthenticDatabaseConnector.keepaliveFor(60_000));
    }

    @Test
    void disablesKeepaliveWhenItWouldFallBelowHikariMinimum() {
        assertEquals(0, AuthenticDatabaseConnector.keepaliveFor(50_000));
    }
}
