package io.gitlab.teamproject2026.cache;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;

// This disables the Cache-Tests during normal Testrun 
// manually run these tests via > mvn test -DlocalDB=true
@EnabledIfSystemProperty(named = "localDB", matches = "true")
class CacheHandlerTest {

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

    private static ResourceRecord makeA(String name, long ttl, byte[] address) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(name.split("\\."));
        rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(ttl);
        rr.setRdlength(address.length);
        RDataA rdata = new RDataA();
        rdata.setAddress(address);
        rr.setRdata(rdata);
        return rr;
    }

    @Test
    void cacheConnectionTest() throws Exception {
        CacheConnectionProvider provider = new CacheConnectionProvider();
        Connection conn = provider.getConnection();
        assertNotNull(conn);
        conn.close();
    }

    @Test
    void insertTest() throws Exception {
        handler.insert(makeA("test.example.com", 300, new byte[] { 1, 2, 3, 4 }));

        ResourceRecord[] results = handler.lookup("test.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void deleteTest() throws Exception {
        handler.insert(makeA("delete.example.com", 300, new byte[] { 9, 8, 7, 6 }));
        handler.delete("delete.example.com", RRType.A);

        assertEquals(0, handler.lookup("delete.example.com", RRType.A).length);
    }

    @Test
    void clearAllTest() throws Exception {
        handler.insert(makeA("clear.example.com", 300, new byte[] { 5, 6, 7, 8 }));
        handler.clearAll();

        assertEquals(0, handler.lookup("clear.example.com", RRType.A).length);
    }

    @Test
    void insertAllTest() throws Exception {
        handler.insertAll(new ResourceRecord[] {
                makeA("bulk1.example.com", 300, new byte[] { 10, 0, 0, 1 }),
                makeA("bulk2.example.com", 300, new byte[] { 10, 0, 0, 2 })
        });

        assertEquals(1, handler.lookup("bulk1.example.com", RRType.A).length);
        assertEquals(1, handler.lookup("bulk2.example.com", RRType.A).length);
    }

    @Test
    void ttlExpiredRecordNotReturnedTest() throws Exception {
        handler.insert(makeA("ttl.example.com", 0, new byte[] { 1, 2, 3, 4 }));

        assertEquals(0, handler.lookup("ttl.example.com", RRType.A).length);
    }

    @Test
    void insertReplacesExistingTest() throws Exception {
        handler.insert(makeA("replace.example.com", 300, new byte[] { 1, 1, 1, 1 }));
        handler.insert(makeA("replace.example.com", 300, new byte[] { 2, 2, 2, 2 }));

        ResourceRecord[] results = handler.lookup("replace.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new byte[] { 2, 2, 2, 2 }, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void insertAllReplacesExistingTest() throws Exception {
        handler.insertAll(new ResourceRecord[] { makeA("replace.example.com", 300, new byte[] { 1, 1, 1, 1 }) });
        handler.insertAll(new ResourceRecord[] { makeA("replace.example.com", 300, new byte[] { 2, 2, 2, 2 }) });

        ResourceRecord[] results = handler.lookup("replace.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new byte[] { 2, 2, 2, 2 }, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void caseInsensitiveLookupTest() throws Exception {
        handler.insert(makeA("CASE.EXAMPLE.COM", 300, new byte[] { 1, 2, 3, 4 }));

        assertEquals(1, handler.lookup("case.example.com", RRType.A).length);
        assertEquals(1, handler.lookup("CASE.EXAMPLE.COM", RRType.A).length);
    }

    @Test
    void deleteExpiredTest() throws Exception {
        handler.insert(makeA("expired.example.com", 0, new byte[] { 1, 2, 3, 4 }));
        handler.deleteExpired();

        assertEquals(0, handler.lookup("expired.example.com", RRType.A).length);
    }
}
