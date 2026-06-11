package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

// This disables the Cache-Tests during normal Testrun 
// manually run these tests via > mvn test -DlocalDB=true
@EnabledIfSystemProperty(named = "localDB", matches = "true")
class ZoneHandlerTest {

    private ZoneHandler handler;

    @BeforeEach
    void setup() throws Exception {
        handler = new ZoneHandler(new DatabaseConnectionProvider());
        handler.clearAll();
    }

    @AfterEach
    void reset() throws Exception {
        handler.clearAll();
    }

    @Test
    void setupTest() throws SQLException {
        handler.insertZone(ConfigManager.loadZoneFile("zones", "db.example.com", "example.com"));

        ResourceRecord aResult = handler.lookup("example.com", RRType.A, "example.com").getFirst();
        assertEquals(86400, aResult.getTtl());
        assertArrayEquals(new byte[]{(byte) 129, (byte) 0, (byte) 0, (byte) 1},  ((RDataA) aResult.getRdata()).getAddress());
        ResourceRecord soaResult = handler.lookup("example.com", RRType.SOA, "example.com").getFirst();
        assertEquals(86400, soaResult.getTtl());
        assertEquals(1209600,  ((RDataSOA) soaResult.getRdata()).getExpire());
        assertEquals(2026060201,  ((RDataSOA) soaResult.getRdata()).getSerial());
        assertEquals(3600,  ((RDataSOA) soaResult.getRdata()).getRefresh());
    }

    @Test
    void clearAllTest() throws Exception {
        handler.insert(new ResourceRecord(new String[]{"clear", "test", "com"}, RRClass.IN, 86400, new RDataA(new byte[]{(byte) 2, (byte) 3, (byte) 4, (byte) 5})), "test.com");
        handler.clearAll();

        assertEquals(0, handler.lookup("clear.test.com", RRType.A, "test.com").size());
    }

}
