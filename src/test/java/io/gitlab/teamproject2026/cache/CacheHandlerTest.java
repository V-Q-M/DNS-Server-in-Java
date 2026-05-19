package io.gitlab.teamproject2026.cache;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;

@EnabledIfSystemProperty(named = "localDB", matches = "true")
class CacheHandlerTest {

    @Test
    void cacheConnectionTest() throws Exception {
        CacheConnectionProvider provider = new CacheConnectionProvider();
        Connection conn = provider.getConnection();
        assertNotNull(conn);
        conn.close();
    }

    private CacheHandler handler;

    @BeforeEach
    void setup() throws Exception {
        handler = new CacheHandler(new CacheConnectionProvider());
        handler.clearAll();
    }
    @AfterEach
    void reset() throws Exception {
        handler.clearAll();
    }

    @Test
    void insertTest() throws Exception {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(new String[]{"test", "example", "com"});
        rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        rr.setRdlength(4);
        RDataA rdata = new RDataA();
        rdata.setAddress(new byte[]{1, 2, 3, 4});
        rr.setRdata(rdata);

        handler.insert(rr);

        ResourceRecord[] results = handler.lookup("test.example.com", RRType.A);
        assertTrue(results.length > 0);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void deleteTest() throws Exception {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(new String[]{"delete", "example", "com"});
        rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        rr.setRdlength(4);
        RDataA rdata = new RDataA();
        rdata.setAddress(new byte[]{9, 8, 7, 6});
        rr.setRdata(rdata);

        handler.insert(rr);
        handler.delete("delete.example.com", RRType.A);

        ResourceRecord[] results = handler.lookup("delete.example.com", RRType.A);
        assertEquals(0, results.length);
    }

    @Test
    void clearAllTest() throws Exception {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(new String[]{"clear", "example", "com"});
        rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        rr.setRdlength(4);
        RDataA rdata = new RDataA();
        rdata.setAddress(new byte[]{5, 6, 7, 8});
        rr.setRdata(rdata);

        handler.insert(rr);
        handler.clearAll();

        ResourceRecord[] results = handler.lookup("clear.example.com", RRType.A);
        assertEquals(0, results.length);
    }

    @Test
    void insertAllTest() throws Exception {
        ResourceRecord rr1 = new ResourceRecord();
        rr1.setName(new String[]{"bulk1", "example", "com"});
        rr1.setType(RRType.A);
        rr1.setRrclass(RRClass.IN);
        rr1.setTtl(300);
        rr1.setRdlength(4);
        RDataA rdata1 = new RDataA();
        rdata1.setAddress(new byte[]{10, 0, 0, 1});
        rr1.setRdata(rdata1);

        ResourceRecord rr2 = new ResourceRecord();
        rr2.setName(new String[]{"bulk2", "example", "com"});
        rr2.setType(RRType.A);
        rr2.setRrclass(RRClass.IN);
        rr2.setTtl(300);
        rr2.setRdlength(4);
        RDataA rdata2 = new RDataA();
        rdata2.setAddress(new byte[]{10, 0, 0, 2});
        rr2.setRdata(rdata2);

        handler.insertAll(new ResourceRecord[]{rr1, rr2});

        assertEquals(1, handler.lookup("bulk1.example.com", RRType.A).length);
        assertEquals(1, handler.lookup("bulk2.example.com", RRType.A).length);

    }
}
