package io.gitlab.teamproject2026.database;

import static org.junit.jupiter.api.Assertions.*;

import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheHandlerTest {

    private CacheHandler handler;

    @BeforeEach
    void setup() throws Exception {
        handler = new CacheHandler(new DatabaseConnectionProvider());
        handler.clearAll();
    }

    @AfterEach
    void reset() throws Exception {
        handler.clearAll();
    }

    private static ResourceRecord makeA(String name, long ttl, byte[] address) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(name.split("\\."));
        // rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(ttl);
        // rr.setRdlength(address.length);
        RDataA rdata = new RDataA();
        rdata.setAddress(address);
        rr.setRdata(rdata);
        return rr;
    }

    @Test
    void cacheConnectionTest() throws Exception {
        DatabaseConnectionProvider provider = new DatabaseConnectionProvider();
        Connection conn = provider.getConnection();
        assertNotNull(conn);
        conn.close();
    }

    @Test
    void insertTest() throws Exception {
        handler.insert(makeA("test.example.com", 300, new byte[] {1, 2, 3, 4}));

        ResourceRecord[] results = handler.lookup("test.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void deleteTest() throws Exception {
        handler.insert(makeA("delete.example.com", 300, new byte[] {9, 8, 7, 6}));
        handler.delete("delete.example.com", RRType.A);

        assertEquals(0, handler.lookup("delete.example.com", RRType.A).length);
    }

    @Test
    void clearAllTest() throws Exception {
        handler.insert(makeA("clear.example.com", 300, new byte[] {5, 6, 7, 8}));
        handler.clearAll();

        assertEquals(0, handler.lookup("clear.example.com", RRType.A).length);
    }

    @Test
    void insertAllTest() throws Exception {
        handler.insertAll(new ResourceRecord[] {
            makeA("bulk1.example.com", 300, new byte[] {10, 0, 0, 1}),
            makeA("bulk2.example.com", 300, new byte[] {10, 0, 0, 2})
        });

        assertEquals(1, handler.lookup("bulk1.example.com", RRType.A).length);
        assertEquals(1, handler.lookup("bulk2.example.com", RRType.A).length);
    }

    @Test
    void ttlExpiredRecordNotReturnedTest() throws Exception {
        handler.insert(makeA("ttl.example.com", 0, new byte[] {1, 2, 3, 4}));

        assertEquals(0, handler.lookup("ttl.example.com", RRType.A).length);
    }

    @Test
    void ttlReturnedAsRemainingTest() throws Exception {
        handler.insert(makeA("ttlremain.example.com", 300, new byte[] {1, 2, 3, 4}));
        Thread.sleep(1500);

        ResourceRecord[] results = handler.lookup("ttlremain.example.com", RRType.A);
        assertEquals(1, results.length);
        long ttl = results[0].getTtl();
        // TTL must be decremented by the elapsed time, not the original 300
        assertTrue(ttl <= 299, "expected decremented TTL <= 299 but was " + ttl);
        assertTrue(ttl >= 1, "TTL should never drop below 1, was " + ttl);
    }

    @Test
    void ttlFlooredAtOneTest() throws Exception {
        handler.insert(makeA("ttlfloor.example.com", 1, new byte[] {1, 2, 3, 4}));

        ResourceRecord[] results = handler.lookup("ttlfloor.example.com", RRType.A);
        assertEquals(1, results.length);
        // a record in its final second must report TTL 1, never 0
        assertEquals(1, results[0].getTtl());
    }

    @Test
    void insertReplacesExistingTest() throws Exception {
        handler.insert(makeA("replace.example.com", 300, new byte[] {1, 1, 1, 1}));
        handler.insert(makeA("replace.example.com", 300, new byte[] {2, 2, 2, 2}));

        ResourceRecord[] results = handler.lookup("replace.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new byte[] {2, 2, 2, 2}, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void insertAllReplacesExistingTest() throws Exception {
        handler.insertAll(new ResourceRecord[] {makeA("replace.example.com", 300, new byte[] {1, 1, 1, 1})});
        handler.insertAll(new ResourceRecord[] {makeA("replace.example.com", 300, new byte[] {2, 2, 2, 2})});

        ResourceRecord[] results = handler.lookup("replace.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new byte[] {2, 2, 2, 2}, ((RDataA) results[0].getRdata()).getAddress());
    }

    @Test
    void caseInsensitiveLookupTest() throws Exception {
        handler.insert(makeA("CASE.EXAMPLE.COM", 300, new byte[] {1, 2, 3, 4}));

        assertEquals(1, handler.lookup("case.example.com", RRType.A).length);
        assertEquals(1, handler.lookup("CASE.EXAMPLE.COM", RRType.A).length);
    }

    @Test
    void deleteExpiredTest() throws Exception {
        handler.insert(makeA("expired.example.com", 0, new byte[] {1, 2, 3, 4}));
        handler.deleteExpired();

        assertEquals(0, handler.lookup("expired.example.com", RRType.A).length);
    }

    @Test
    void casePreservationTest() throws Exception {
        handler.insert(makeA("CASE.EXAMPLE.COM", 300, new byte[] {1, 2, 3, 4}));

        ResourceRecord[] results = handler.lookup("case.example.com", RRType.A);
        assertEquals(1, results.length);
        assertArrayEquals(new String[] {"CASE", "EXAMPLE", "COM"}, results[0].getName());
    }

    @Test
    void insertAllRRsetTest() throws Exception {
        handler.insertAll(new ResourceRecord[] {
            makeA("rrset.example.com", 300, new byte[] {1, 1, 1, 1}),
            makeA("rrset.example.com", 300, new byte[] {2, 2, 2, 2})
        });
        assertEquals(2, handler.lookup("rrset.example.com", RRType.A).length);

        handler.insertAll(new ResourceRecord[] {makeA("rrset.example.com", 300, new byte[] {3, 3, 3, 3})});
        assertEquals(1, handler.lookup("rrset.example.com", RRType.A).length);
        assertArrayEquals(
                new byte[] {3, 3, 3, 3},
                ((RDataA) handler.lookup("rrset.example.com", RRType.A)[0].getRdata()).getAddress());
    }
}
