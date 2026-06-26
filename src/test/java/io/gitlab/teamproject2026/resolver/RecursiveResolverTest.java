package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecursiveResolverTest {

    private static StubResolver stubResolver;
    private static RecursiveResolver recursiveResolver;

    @BeforeAll
    public static void setUp() {
        try {
            int timeoutMs = 4000;
            int retryDelayMs = 1000;
            int maxRetries = 3;
            StubResolverStrategy strategy = StubResolverStrategy.ROUND_ROBIN;
            List<NameServer> forwarders = List.of(new NameServer("8.8.8.8", 53, "8.8.8.8")        );
            CacheHandler cacheHandler = new CacheHandler(new DatabaseConnectionProvider());
            stubResolver = new StubResolver(forwarders, timeoutMs, maxRetries, retryDelayMs, strategy);
            recursiveResolver = new RecursiveResolver(cacheHandler, timeoutMs, retryDelayMs);
            } catch (Exception e) {
            System.out.println("[TEST-SETUP] failed database conn, resolve with null handlers");
        }
    }

    @Test
    public void testCompareStubAndRecursiveResolutionAnswerSection() {
        //--------------------A-RR test------------------------------------------------
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

    @Test
    public void testCompareStubAndRecursiveResolutionResponse() {
        // ---------------SOA test---------------------------------------------------------------
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

}