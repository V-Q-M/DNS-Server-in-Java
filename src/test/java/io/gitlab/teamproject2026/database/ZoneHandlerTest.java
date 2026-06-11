package io.gitlab.teamproject2026.database;

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
        handler.insertZone(new ResourceRecord[]{
                new ResourceRecord(new String[]{"test", "com"}, RRClass.IN, 86400, new RDataA(new byte[]{(byte) 2, (byte) 3, (byte) 4, (byte) 5})),
                new ResourceRecord(new String[]{"ns0", "test", "com"}, RRClass.IN, 86500, new RDataA(new byte[]{(byte) 1, (byte) 3, (byte) 4, (byte) 5}))
        }, "test.com");

        ResourceRecord aResult = handler.lookup("test.com", RRType.A, "test.com").getFirst();
        assertEquals(86400, aResult.getTtl());
        assertArrayEquals(new byte[]{(byte) 2, (byte) 3, (byte) 4, (byte) 5},  ((RDataA) aResult.getRdata()).getAddress());
        aResult = handler.lookup("ns0.test.com", RRType.A, "test.com").getFirst();
        assertEquals(86500, aResult.getTtl());
        assertArrayEquals(new byte[]{(byte) 1, (byte) 3, (byte) 4, (byte) 5},  ((RDataA) aResult.getRdata()).getAddress());
    }

    @Test
    void clearAllTest() throws Exception {
        handler.insert(new ResourceRecord(new String[]{"clear", "test", "com"}, RRClass.IN, 86400, new RDataA(new byte[]{(byte) 2, (byte) 3, (byte) 4, (byte) 5})), "test.com");
        handler.clearAll();

        assertEquals(0, handler.lookup("clear.test.com", RRType.A, "test.com").size());
    }

}
