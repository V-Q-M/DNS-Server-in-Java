package io.gitlab.teamproject2026;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;



public class DNSCacheConnectionTest extends AbstractDNSCacheTests {

    @Test
    void DatabaseConnection() {
        try {
            connectionProvider.getConnection();
            assertTrue(true);
        } catch (SQLException e) {
            System.err.println(e);
            assertTrue(false);
        } catch (ClassNotFoundException f) {
            System.err.println(f + "This shouldn't happen, perhaps the dependencies aren't configured correctly?");
            assertTrue(false);
        }
    }
}