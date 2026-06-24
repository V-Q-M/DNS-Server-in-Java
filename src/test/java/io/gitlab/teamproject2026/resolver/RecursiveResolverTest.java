package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecursiveResolverTest {

    private static StubResolver stubResolver;
    private static RecursiveResolver recursiveResolver;

    @BeforeAll
    public static void setUp() throws Exception {
        int timeoutMs = 4000;
        int retryDelayMs = 1000;
        int maxRetries = 3;
        StubResolverStrategy strategy = StubResolverStrategy.ROUND_ROBIN;
        List<NameServer> forwarders = List.of(new NameServer("8.8.8.8", 53, "8.8.8.8")        );
        CacheHandler cacheHandler = new CacheHandler(new DatabaseConnectionProvider());
        ZoneHandler zoneHandler = null;

        try {
            cacheHandler = new CacheHandler(new DatabaseConnectionProvider());
            zoneHandler = new ZoneHandler(new DatabaseConnectionProvider());
        } catch (Exception e) {
            System.out.println("[TEST-SETUP] failed database conn, resolve with null handlers");
        }

        stubResolver = new StubResolver(forwarders, timeoutMs, maxRetries, retryDelayMs, strategy);
        recursiveResolver = new RecursiveResolver(cacheHandler, zoneHandler, timeoutMs, retryDelayMs);
    }

    @Test
    public void testCompareStubAndRecursiveResolution() {
        // build querypacket for example.com,A
        DNSPacket queryPacket = new DNSPacket();
        queryPacket.setId(new byte[]{0x12, 0x34});
        queryPacket.setQr(false);
        queryPacket.setOpcode(OpCode.QUERY);
        queryPacket.setRd(true);

        QuestionSection question = new QuestionSection();
        question.setName(new String[]{"example", "com"});
        question.setQtype(RRType.A);
        question.setQclass(RRClass.IN);
        queryPacket.setQuestionSections(new QuestionSection[]{question});
        queryPacket.setQdcount(1);
        queryPacket.setAnswerSections(new ResourceRecord[0]);
        queryPacket.setAuthoritySections(new ResourceRecord[0]);
        queryPacket.setAdditionalSections(new ResourceRecord[0]);


        // raw bytes for stub (rd=true)
        byte[] rawQueryBytes = queryPacket.toBytes();

        // raw bytes for recursive resolver: rd=false
        queryPacket.setRd(false);
        byte[] rawQueryBytesRecursive = queryPacket.toBytes();


        // stub resolve
        byte[] stubRawResponse = null;
        try {
            stubRawResponse = stubResolver.resolve(rawQueryBytes);
        } catch (Exception e) {
            fail("StubResolver crashed or timed out: " + e.getMessage());
        }

        // recursive resolve
        byte[] recursiveRawResponse = null;
        try {
            recursiveRawResponse = recursiveResolver.resolve(rawQueryBytesRecursive);
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

        //assert same A-RR is returned
        assertEquals(stubResponse.getAnswerSections()[0], recursiveResponse.getAnswerSections()[0], "stub and recursive should both return same IP (A-RR)");
    }

}