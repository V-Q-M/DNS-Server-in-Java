package io.gitlab.teamproject2026.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.gitlab.teamproject2026.config.logging.LoggingSection;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.server.DNSPayload;
import io.gitlab.teamproject2026.server.DNSResolutionPipeline;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryCacheCNAMETest {

    private CacheHandler cache;
    private DNSResolutionPipeline pipeline;
    private Logger logger = new Logger(LoggingSection.defaultLogger());

    @BeforeEach
    void setup() throws Exception {
        cache = new CacheHandler(new DatabaseConnectionProvider());
        cache.clearAll();
        pipeline = new DNSResolutionPipeline(
                cache, null, null, null, null, logger, List.of(), false, null, System.currentTimeMillis());
    }

    @AfterEach
    void teardown() throws Exception {
        cache.clearAll();
    }

    // --- helpers ---

    private static ResourceRecord makeA(String name, byte[] address) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(name.split("\\."));
        // rr.setType(RRType.A);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        // rr.setRdlength(address.length);
        RDataA rdata = new RDataA();
        rdata.setAddress(address);
        rr.setRdata(rdata);
        return rr;
    }

    private static ResourceRecord makeCNAME(String name, String target) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(name.split("\\."));
        // rr.setType(RRType.CNAME);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(300);
        RDataCNAME rdata = new RDataCNAME();
        rdata.setCname(target.split("\\."));
        rr.setRdata(rdata);
        // rr.setRdlength(rdata.toBytes().length);
        return rr;
    }

    private static DNSPacket makeQuery(String name, RRType type) {
        QuestionSection q = new QuestionSection();
        q.setName(name.split("\\."));
        q.setQtype(type);
        q.setQclass(RRClass.IN);
        DNSPacket packet = new DNSPacket();
        packet.setQdcount(1);
        packet.setQuestionSections(new QuestionSection[] {q});
        return packet;
    }

    // --- tests ---

    @Test
    void directHit() throws Exception {
        cache.insertAll(new ResourceRecord[] {makeA("www.example.com", new byte[] {1, 2, 3, 4})});

        DNSPayload payload = pipeline.queryCache(makeQuery("www.example.com", RRType.A));

        assertEquals(1, payload.answer().size());
        assertEquals(0, payload.notResolved().size());
    }

    @Test
    void multiHopCnameHit() throws Exception {
        cache.insertAll(new ResourceRecord[] {makeCNAME("www.example.com", "alias.example.com")});
        cache.insertAll(new ResourceRecord[] {makeCNAME("alias.example.com", "server.example.com")});
        cache.insertAll(new ResourceRecord[] {makeA("server.example.com", new byte[] {1, 2, 3, 4})});

        DNSPayload payload = pipeline.queryCache(makeQuery("www.example.com", RRType.A));

        assertEquals(3, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().get(0).getType());
        assertEquals(RRType.CNAME, payload.answer().get(1).getType());
        assertEquals(RRType.A, payload.answer().get(2).getType());
        assertEquals(0, payload.notResolved().size());
    }

    @Test
    void cnameHit() throws Exception {
        cache.insertAll(new ResourceRecord[] {makeCNAME("www.example.com", "server.example.com")});
        cache.insertAll(new ResourceRecord[] {makeA("server.example.com", new byte[] {1, 2, 3, 4})});

        DNSPayload payload = pipeline.queryCache(makeQuery("www.example.com", RRType.A));

        assertEquals(2, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().get(0).getType());
        assertEquals(RRType.A, payload.answer().get(1).getType());
        assertEquals(0, payload.notResolved().size());
    }

    @Test
    void cnamePartialHit() throws Exception {
        cache.insertAll(new ResourceRecord[] {makeCNAME("www.example.com", "server.example.com")});
        // no A record for server.example.com

        DNSPayload payload = pipeline.queryCache(makeQuery("www.example.com", RRType.A));

        // Partial hit: the cached CNAME goes in the answer and only the unresolved
        // tail (server.example.com A) is forwarded to the stub.
        assertEquals(1, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().getFirst().getType());
        assertEquals(1, payload.notResolved().size());
        assertArrayEquals(
                new String[] {"server", "example", "com"},
                payload.notResolved().getFirst().getName());
        assertEquals(RRType.A, payload.notResolved().getFirst().getQtype());
        assertEquals(RRClass.IN, payload.notResolved().getFirst().getQclass());
    }

    @Test
    void multiHopPartialHit() throws Exception {
        // Two cached aliases, but the terminal A record is missing.
        cache.insertAll(new ResourceRecord[] {makeCNAME("www.example.com", "alias.example.com")});
        cache.insertAll(new ResourceRecord[] {makeCNAME("alias.example.com", "server.example.com")});
        // no A record for server.example.com

        DNSPayload payload = pipeline.queryCache(makeQuery("www.example.com", RRType.A));

        // Both cached CNAMEs are committed, and only the deepest unresolved tail is forwarded.
        assertEquals(2, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().get(0).getType());
        assertEquals(RRType.CNAME, payload.answer().get(1).getType());
        assertEquals(1, payload.notResolved().size());
        assertArrayEquals(
                new String[] {"server", "example", "com"},
                payload.notResolved().getFirst().getName());
        assertEquals(RRType.A, payload.notResolved().getFirst().getQtype());
    }

    @Test
    void cnameLoopForwardsToStub() throws Exception {
        // a -> b -> a is a cycle: never resolves, must not loop forever
        cache.insertAll(new ResourceRecord[] {makeCNAME("a.example.com", "b.example.com")});
        cache.insertAll(new ResourceRecord[] {makeCNAME("b.example.com", "a.example.com")});

        DNSPayload payload = pipeline.queryCache(makeQuery("a.example.com", RRType.A));

        // loop detected
        // nothing committed, whole query forwarded to the stub
        assertEquals(0, payload.answer().size());
        assertEquals(1, payload.notResolved().size());
    }

    @Test
    void cnameTypeDirectHit() throws Exception {
        cache.insertAll(new ResourceRecord[] {makeCNAME("www.example.com", "server.example.com")});

        DNSPayload payload = pipeline.queryCache(makeQuery("www.example.com", RRType.CNAME));

        assertEquals(1, payload.answer().size());
        assertEquals(RRType.CNAME, payload.answer().getFirst().getType());
        assertEquals(0, payload.notResolved().size());
    }
}
