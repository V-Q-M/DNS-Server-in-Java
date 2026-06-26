package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.OpCode;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import io.gitlab.teamproject2026.model.rdata.RDataAAAA;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


public class RecursiveResolverUnitTest {

    private static CacheHandler cache;

    @BeforeAll
    static void setUp() {
        try {
            cache = new CacheHandler(new DatabaseConnectionProvider());
        } catch (Exception e) {
            cache = null; 
        }
    }

    // ---- cnameTarget ----

    @Test
    void cnameTargetReturnsCanonicalName() {
        DNSPacket response = new DNSPacket();
        response.setAnswerSections(new ResourceRecord[]{
                record(new String[]{"www", "example", "com"}, new RDataCNAME(new String[]{"target", "example", "net"}))
        });
        assertArrayEquals(new String[]{"target", "example", "net"}, RecursiveResolver.cnameTarget(response));
    }

    @Test
    void cnameTargetReturnsNullWithoutCname() {
        DNSPacket response = new DNSPacket();
        response.setAnswerSections(new ResourceRecord[0]);
        assertNull(RecursiveResolver.cnameTarget(response));
    }

    // ---- prependCnames ----

    @Test
    void prependCnamesBuildsChainInOrder() {
        DNSPacket cnameResponse = new DNSPacket();
        cnameResponse.setAnswerSections(new ResourceRecord[]{
                record(new String[]{"www", "example", "com"}, new RDataCNAME(new String[]{"target", "example", "net"}))
        });

        byte[] resolved = answerPacket(
                record(new String[]{"target", "example", "net"}, new RDataA(new byte[]{1, 2, 3, 4}))).toBytes();

        DNSPacket merged = DNSPacket.fromBytes(RecursiveResolver.prependCnames(cnameResponse, resolved));
        ResourceRecord[] answer = merged.getAnswerSections();

        assertEquals(2, answer.length, "chain should be CNAME then the final record");
        assertEquals(RRType.CNAME, answer[0].getType());
        assertEquals(RRType.A, answer[1].getType());
    }

    // ---- extractDelegations (glue) ----

    @Test
    void extractDelegationsWithGlue() {
        DNSPacket referral = referralPacket(
                record(new String[]{"example", "com"}, new RDataNS(new String[]{"ns1", "example", "com"})),
                record(new String[]{"ns1", "example", "com"}, new RDataA(new byte[]{5, 6, 7, 8})));

        List<RecursiveResolver.Delegation> dels = RecursiveResolver.extractDelegations(referral);

        assertEquals(1, dels.size());
        assertTrue(dels.get(0).hasGlue());
        assertEquals("5.6.7.8", dels.get(0).ipv4().toString());
    }

    @Test
    void extractDelegationsWithoutGlueGivesZeroAddress() {
        DNSPacket referral = referralPacket(
                record(new String[]{"example", "com"}, new RDataNS(new String[]{"ns1", "example", "com"})),
                null);

        List<RecursiveResolver.Delegation> dels = RecursiveResolver.extractDelegations(referral);

        assertEquals(1, dels.size());
        assertFalse(dels.get(0).hasGlue(), "no glue record -> zero address");
    }

    // ---- glue resolver cache interaction ----

    @Test
    void resolveNsAddressUsesCachedGlue() throws Exception {
        assumeTrue(cache != null, "needs a running database");

        // put ns1.cachetest.example -> 7.7.7.7 into the cache
        cache.insertAll(new ResourceRecord[]{
                record(new String[]{"ns1", "cachetest", "example"}, new RDataA(new byte[]{7, 7, 7, 7}))
        });

        RecursiveResolver resolver = new RecursiveResolver(cache, null, 2000, 500);

        // a glue-less delegation for that name server
        RecursiveResolver.Delegation glueless = new RecursiveResolver.Delegation(
                record(new String[]{"cachetest", "example"}, new RDataNS(new String[]{"ns1", "cachetest", "example"})),
                new RDataA(new byte[4]), new RDataAAAA(new byte[16]));
        assertFalse(glueless.hasGlue());

        RecursiveResolver.Delegation resolved =
                resolver.resolveNsAddress(glueless, RecursiveResolver.ResolveContext.start());

        // address came straight from the cache, no network query
        assertEquals("7.7.7.7", resolved.ipv4().toString());
    }

    // ---- cname cache-first ----

    @Test
    void cachedResponseReturnsCachedRecords() throws Exception {
        assumeTrue(cache != null, "needs a running database");

        cache.insertAll(new ResourceRecord[]{
                record(new String[]{"target", "cnamecache", "example"}, new RDataA(new byte[]{8, 8, 4, 4}))
        });
        RecursiveResolver resolver = new RecursiveResolver(cache, null, 2000, 500);

        byte[] resp = resolver.cachedResponse(new String[]{"target", "cnamecache", "example"}, RRType.A);

        assertNotNull(resp, "cached records should produce a response");
        DNSPacket packet = DNSPacket.fromBytes(resp);
        assertEquals(RCode.NOERROR, packet.getRcode());
        assertEquals(1, packet.getAnswerSections().length);
        assertEquals(RRType.A, packet.getAnswerSections()[0].getType());
        assertEquals("8.8.4.4", packet.getAnswerSections()[0].getRdata().toString());
    }

    @Test
    void cachedResponseReturnsNullOnMiss() {
        assumeTrue(cache != null, "needs a running database");

        RecursiveResolver resolver = new RecursiveResolver(cache, null, 2000, 500);
        byte[] resp = resolver.cachedResponse(new String[]{"absent", "cnamecache", "example"}, RRType.A);
        assertNull(resp, "a cache miss should return null so the loop falls back to iterating");
    }

    // ---- glue resolver AAAA fallback ----

    @Test
    void resolveAddressFindsCachedAaaa() throws Exception {
        assumeTrue(cache != null, "needs a running database");

        // an IPv6-only name server: only an AAAA is cached
        cache.insertAll(new ResourceRecord[]{
                record(new String[]{"ns6", "v6test", "example"}, new RDataAAAA(new byte[]{
                        0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01
                }))
        });
        RecursiveResolver resolver = new RecursiveResolver(cache, null, 2000, 500);

        var aaaa = resolver.resolveAddress(new String[]{"ns6", "v6test", "example"}, RRType.AAAA,
                RecursiveResolver.ResolveContext.start());

        assertNotNull(aaaa, "cached AAAA should be found");
        assertEquals(RRType.AAAA, aaaa.getType());
    }

    @Test
    void resolveAddressFindsCachedA() throws Exception {
        assumeTrue(cache != null, "needs a running database");

        cache.insertAll(new ResourceRecord[]{
                record(new String[]{"ns4", "v6test", "example"}, new RDataA(new byte[]{1, 1, 1, 1}))
        });
        RecursiveResolver resolver = new RecursiveResolver(cache, null, 2000, 500);

        var a = resolver.resolveAddress(new String[]{"ns4", "v6test", "example"}, RRType.A,
                RecursiveResolver.ResolveContext.start());

        assertNotNull(a, "cached A should be found");
        assertEquals(RRType.A, a.getType());
        assertEquals("1.1.1.1", a.toString());
    }

    // ---- helpers ----

    private static ResourceRecord record(String[] name, io.gitlab.teamproject2026.model.rdata.RData rdata) {
        return new ResourceRecord(name, RRClass.IN, 300L, rdata);
    }

    private static DNSPacket answerPacket(ResourceRecord answer) {
        DNSPacket packet = blankPacket();
        packet.setAnswerSections(new ResourceRecord[]{answer});
        return packet;
    }

    private static DNSPacket referralPacket(ResourceRecord ns, ResourceRecord glue) {
        DNSPacket packet = blankPacket();
        packet.setAnswerSections(new ResourceRecord[0]);
        packet.setAuthoritySections(new ResourceRecord[]{ns});
        packet.setAdditionalSections(glue == null ? new ResourceRecord[0] : new ResourceRecord[]{glue});
        return packet;
    }

    private static DNSPacket blankPacket() {
        DNSPacket packet = new DNSPacket();
        packet.setId(new byte[]{0x00, 0x01});
        packet.setOpcode(OpCode.QUERY);
        packet.setRcode(RCode.NOERROR);
        QuestionSection question = new QuestionSection();
        question.setName(new String[]{"example", "com"});
        question.setQtype(RRType.A);
        question.setQclass(RRClass.IN);
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[0]);
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[0]);
        return packet;
    }
}
