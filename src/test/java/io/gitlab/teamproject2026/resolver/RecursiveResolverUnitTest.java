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
    private static StubResolver stubResolver;
    private static RecursiveResolver recursiveResolver;

    @BeforeAll
    static void setUp() {
        try {
            int timeoutMs = 4000;
            int retryDelayMs = 1000;
            int maxRetries = 3;
            StubResolverStrategy strategy = StubResolverStrategy.ROUND_ROBIN;
            List<NameServer> forwarders = List.of(new NameServer("8.8.8.8", 53, "8.8.8.8")        );
            CacheHandler cacheHandler = new CacheHandler(new DatabaseConnectionProvider());
            stubResolver = new StubResolver(forwarders, timeoutMs, maxRetries, retryDelayMs, strategy);
            recursiveResolver = new RecursiveResolver(cacheHandler, timeoutMs, retryDelayMs);
            cache = new CacheHandler(new DatabaseConnectionProvider());
        } catch (Exception e) {
            cache = null; 
        }
    }




    //--------------------A-RR test: test recursiveResolver.resolve------------------------------------------------
    @Test
    public void testCompareStubAndRecursiveResolutionAnswerSection() {
        // build querypacket for example.com,A
        DNSPacket queryPacketA = new DNSPacket();
        queryPacketA.setId(new byte[]{0x12, 0x34});
        queryPacketA.setQr(false);
        queryPacketA.setOpcode(OpCode.QUERY);
        queryPacketA.setRd(true);

        QuestionSection questionA = new QuestionSection();
        questionA.setName(new String[]{"example", "com"});
        questionA.setQtype(RRType.A);
        questionA.setQclass(RRClass.IN);
        queryPacketA.setQuestionSections(new QuestionSection[]{questionA});
        queryPacketA.setQdcount(1);
        queryPacketA.setAnswerSections(new ResourceRecord[0]);
        queryPacketA.setAuthoritySections(new ResourceRecord[0]);
        queryPacketA.setAdditionalSections(new ResourceRecord[0]);


        // raw bytes for stub (rd=true)
        byte[] rawQueryBytesA = queryPacketA.toBytes();
        // raw bytes for recursive resolver: rd=false
        queryPacketA.setRd(false);
        byte[] rawQueryBytesRecursiveA = queryPacketA.toBytes();

        // stub resolve
        byte[] stubRawResponse = null;
        try {
            stubRawResponse = stubResolver.resolve(rawQueryBytesA);
        } catch (Exception e) {
            fail("StubResolver crashed or timed out: " + e.getMessage());
        }
        // recursive resolve
        byte[] recursiveRawResponse = null;
        try {
            recursiveRawResponse = recursiveResolver.resolve(rawQueryBytesRecursiveA);
        } catch (Exception e) {
            fail("RecursiveResolver crashed or timed out: " + e.getMessage());
        }

        DNSPacket stubResponse = DNSPacket.fromBytes(stubRawResponse);
        DNSPacket recursiveResponse = DNSPacket.fromBytes(recursiveRawResponse);

        // assert both stub and recursive return SUCCESS
        assertEquals(RCode.NOERROR, stubResponse.getRcode(), "stub should return NOERROR");
        assertEquals(RCode.NOERROR, recursiveResponse.getRcode(), "recursive resolver should return NOERROR");
        // assert both stub and recursive return not-empty answers
        assertTrue(stubResponse.getAnswerSections().length > 0, "stub answer should not be empty");
        assertTrue(recursiveResponse.getAnswerSections().length > 0, "stub answer should not be empty");
        //assert A-RR is returned
        ResourceRecord stubAnswer = stubResponse.getAnswerSections()[0];
        ResourceRecord recursiveAnswer = recursiveResponse.getAnswerSections()[0];
        //ttl in stubAnswer may be different from ttl in recursiveAnswer, so set both to 0
        stubAnswer.setTtl(0L);
        recursiveAnswer.setTtl(0L);
        //example.com. has more than 1 valid IPv4-address, so just check that type A, RCode=0,... match
        stubAnswer.setRdata(new RDataA());
        recursiveAnswer.setRdata(new RDataA());
        assertArrayEquals(stubAnswer.toBytes(), recursiveAnswer.toBytes(), "stub and recursive should both return A-RR");
        assertEquals(stubAnswer.toString(), recursiveAnswer.toString(), "stub and recursive should both return same A-RR");




    }
    // ---------------SOA test: test recursiveResolver.resolve---------------------------------------------------------------
    @Test
    public void testCompareStubAndRecursiveResolutionResponse() {

        DNSPacket queryPacketSOA = new DNSPacket();
        queryPacketSOA.setId(new byte[]{0x12, 0x33});
        queryPacketSOA.setQr(false);
        queryPacketSOA.setOpcode(OpCode.QUERY);
        queryPacketSOA.setRd(true);

        QuestionSection questionSOA = new QuestionSection();
        questionSOA.setName(new String[]{"example", "com"});
        questionSOA.setQtype(RRType.SOA);
        questionSOA.setQclass(RRClass.IN);

        queryPacketSOA.setQuestionSections(new QuestionSection[]{questionSOA});
        queryPacketSOA.setQdcount(1);
        queryPacketSOA.setAnswerSections(new ResourceRecord[0]);
        queryPacketSOA.setAuthoritySections(new ResourceRecord[0]);
        queryPacketSOA.setAdditionalSections(new ResourceRecord[0]);

        // raw bytes for stub (rd=true)
        byte[] rawQueryBytesSOA = queryPacketSOA.toBytes();
        // raw bytes for recursive resolver: rd=false
        queryPacketSOA.setRd(false);
        byte[] rawQueryBytesRecursiveSOA = queryPacketSOA.toBytes();

        // stub resolve
        byte[] stubRawResponseSOA = null;
        try {
            stubRawResponseSOA = stubResolver.resolve(rawQueryBytesSOA);
        } catch (Exception e) {
            fail("StubResolver crashed or timed out: " + e.getMessage());
        }
        // recursive resolve
        byte[] recursiveRawResponseSOA = null;
        try {
            recursiveRawResponseSOA = recursiveResolver.resolve(rawQueryBytesRecursiveSOA);
        } catch (Exception e) {
            fail("RecursiveResolver crashed or timed out: " + e.getMessage());
        }

        DNSPacket stubResponseSOA = DNSPacket.fromBytes(stubRawResponseSOA);
        DNSPacket recursiveResponseSOA = DNSPacket.fromBytes(recursiveRawResponseSOA);
        ResourceRecord stubAnswerSOA = stubResponseSOA.getAnswerSections()[0];
        ResourceRecord recursiveAnswerSOA = recursiveResponseSOA.getAnswerSections()[0];

        //ttl in stubAnswer may be different from ttl in recursiveAnswer, so set both to 0
        stubAnswerSOA.setTtl(0L);
        recursiveAnswerSOA.setTtl(0L);
        //assert answer sections are the same (byte version and parsed version)
        //since SOA is unique, it is possible to test same contents of SOA (different from A-RR)
        assertArrayEquals(stubAnswerSOA.toBytes(), recursiveAnswerSOA.toBytes(), "stub and recursive should both return same SOA-RR");
        assertEquals(stubAnswerSOA.toString(), recursiveAnswerSOA.toString(), "stub and recursive should both return same SOA-RR");

        // set AA, RA,RD flags explicitly because they might differ
        stubResponseSOA.setAa(true);
        recursiveResponseSOA.setAa(true);
        recursiveResponseSOA.setRa(true);
        recursiveResponseSOA.setRd(true);
        //assert whole package response is same from stub and recursive
        assertEquals(stubResponseSOA.toString(), recursiveResponseSOA.toString(), "stub and recursive should both return same response (SOA)");

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

        DNSPacket resolved = answerPacket(
                record(new String[]{"target", "example", "net"}, new RDataA(new byte[]{1, 2, 3, 4})));

        DNSPacket merged = RecursiveResolver.prependCnames(cnameResponse, resolved);
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

        RecursiveResolver resolver = new RecursiveResolver(cache, 2000, 500);

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
        RecursiveResolver resolver = new RecursiveResolver(cache,  2000, 500);

        DNSPacket packet = resolver.cachedResponse(new String[]{"target", "cnamecache", "example"}, RRType.A);

        assertNotNull(packet, "cached records should produce a response");
        assertEquals(RCode.NOERROR, packet.getRcode());
        assertEquals(1, packet.getAnswerSections().length);
        assertEquals(RRType.A, packet.getAnswerSections()[0].getType());
        assertEquals("8.8.4.4", packet.getAnswerSections()[0].getRdata().toString());
    }

    @Test
    void cachedResponseReturnsNullOnMiss() {
        assumeTrue(cache != null, "needs a running database");

        RecursiveResolver resolver = new RecursiveResolver(cache, 2000, 500);
        DNSPacket resp = resolver.cachedResponse(new String[]{"absent", "cnamecache", "example"}, RRType.A);
        assertNull(resp, "a cache miss should return null so the loop falls back to iterating");
    }

    @Test
    void resolveCnameTargetFollowsCachedChain() throws Exception {
        assumeTrue(cache != null, "needs a running database");

        // a full chain in the cache: www -> mid -> end -> 9.9.9.9
        cache.insertAll(new ResourceRecord[]{
                record(new String[]{"www", "chain", "example"}, new RDataCNAME(new String[]{"mid", "chain", "example"})),
                record(new String[]{"mid", "chain", "example"}, new RDataCNAME(new String[]{"end", "chain", "example"})),
                record(new String[]{"end", "chain", "example"}, new RDataA(new byte[]{9, 9, 9, 9}))
        });
        RecursiveResolver resolver = new RecursiveResolver(cache, 2000, 500);

        DNSPacket packet = resolver.resolveCnameTarget(new String[]{"www", "chain", "example"}, RRType.A,
                RecursiveResolver.ResolveContext.start());

        // the whole chain is served from the cache: two CNAMEs then the final A
        ResourceRecord[] answer = packet.getAnswerSections();
        assertEquals(3, answer.length, "two cached CNAMEs then the final A");
        assertEquals(RRType.CNAME, answer[0].getType());
        assertEquals(RRType.CNAME, answer[1].getType());
        assertEquals(RRType.A, answer[2].getType());
        assertEquals("9.9.9.9", answer[2].getRdata().toString());
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
        RecursiveResolver resolver = new RecursiveResolver(cache, 2000, 500);

        var aaaa = resolver.resolveAddress(new String[]{"ns6", "v6test", "example"}, RRType.AAAA,
                RecursiveResolver.ResolveContext.start());

        assertNotNull(aaaa, "cached AAAA should be found");
        assertEquals(RRType.AAAA, aaaa.getType());
    }

    @Test
    void resolveAddressFindsCachedA() throws Exception {
        assumeTrue(cache != null, "needs a running database");

        cache.insertAll(new ResourceRecord[]{
                record(new String[]{"ns4", "v4test", "example"}, new RDataA(new byte[]{1, 1, 1, 1}))
        });
        RecursiveResolver resolver = new RecursiveResolver(cache,  2000, 500);

        var a = resolver.resolveAddress(new String[]{"ns4", "v4test", "example"}, RRType.A,
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
