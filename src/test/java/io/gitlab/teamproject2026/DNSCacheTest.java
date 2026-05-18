package io.gitlab.teamproject2026;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.gitlab.teamproject2026.cache.CacheConnectionProvider;
import io.gitlab.teamproject2026.cache.CacheHandler;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;

import java.sql.Connection;

public class DNSCacheTest {
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
    }

    @Test
    void insertAndLookupA() throws Exception {
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

        handler.delete("test.example.com", RRType.A);
    }

    @Test
    void deleteRemovesRecord() throws Exception {
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
}
