package io.gitlab.teamproject2026.cache;

import io.gitlab.teamproject2026.DNS_SERVER;
import io.gitlab.teamproject2026.cache.CacheConnectionProvider;
import io.gitlab.teamproject2026.cache.CacheHandler;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

// Run with: mvn test -DlocalDB=true
@EnabledIfSystemProperty(named = "localDB", matches = "true")
class QueryCacheCNAMETest {

    private CacheHandler cache;

    @BeforeEach
    void setup() throws Exception {
        cache = new CacheHandler(new CacheConnectionProvider());
        cache.clearAll();
    }

    @AfterEach
    void teardown() throws Exception {
        cache.clearAll();
    }

    // --- helpers ---

    private static ResourceRecord makeA(String name, byte[] address) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(name.split("\\."));
        rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        rr.setRdlength(address.length);
        RDataA rdata = new RDataA();
        rdata.setAddress(address);
        rr.setRdata(rdata);
        return rr;
    }

    private static ResourceRecord makeCNAME(String name, String target) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(name.split("\\."));
        rr.setType(RRType.CNAME);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        RDataCNAME rdata = new RDataCNAME();
        rdata.setCname(target.split("\\."));
        rr.setRdata(rdata);
        rr.setRdlength(rdata.toBytes().length);
        return rr;
    }

    private static DNSPacket makeQuery(String name, RRType type) {
        QuestionSection q = new QuestionSection();
        q.setName(name.split("\\."));
        q.setQtype(type);
        q.setQclass(RRClass.IN);
        DNSPacket packet = new DNSPacket();
        packet.setQdcount(1);
        packet.setQuestionSections(new QuestionSection[]{ q });
        return packet;
    }

    // --- tests ---

    @Test
    void directHit() throws Exception {
        cache.insertAll(new ResourceRecord[]{ makeA("www.example.com", new byte[]{ 1, 2, 3, 4 }) });

        DNS_SERVER.dnsPayload payload = DNS_SERVER.queryCache(makeQuery("www.example.com", RRType.A), cache);

        assertEquals(1, payload.answer().size());
        assertEquals(0, payload.notCached().size());
    }

    @Test
    void cnameHit() throws Exception {
        cache.insertAll(new ResourceRecord[]{ makeCNAME("www.example.com", "server.example.com") });
        cache.insertAll(new ResourceRecord[]{ makeA("server.example.com", new byte[]{ 1, 2, 3, 4 }) });

        DNS_SERVER.dnsPayload payload = DNS_SERVER.queryCache(makeQuery("www.example.com", RRType.A), cache);

        assertEquals(2, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().get(0).getType());
        assertEquals(RRType.A,     payload.answer().get(1).getType());
        assertEquals(0, payload.notCached().size());
    }

    @Test
    void cnameMissForwardsToStub() throws Exception {
        cache.insertAll(new ResourceRecord[]{ makeCNAME("www.example.com", "server.example.com") });
        // no A record for server.example.com

        DNS_SERVER.dnsPayload payload = DNS_SERVER.queryCache(makeQuery("www.example.com", RRType.A), cache);

        assertEquals(0, payload.answer().size());
        assertEquals(1, payload.notCached().size());
    }

    @Test
    void cnameTypeDirectHit() throws Exception {
        cache.insertAll(new ResourceRecord[]{ makeCNAME("www.example.com", "server.example.com") });

        DNS_SERVER.dnsPayload payload = DNS_SERVER.queryCache(makeQuery("www.example.com", RRType.CNAME), cache);

        assertEquals(1, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().get(0).getType());
        assertEquals(0, payload.notCached().size());
    }
}
