package io.gitlab.teamproject2026.zones;

import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ZoneParserTest {

    // Helper for checking stderr and silencing it
    private String captureStderr(Runnable runnable) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            runnable.run();
        } finally {
            System.setErr(originalErr);
        }
        return errContent.toString();
    }


    @Test
    void testParser() throws UnknownHostException {
        String input =""" 
            $ORIGIN example.com.
            $TTL 3200
            @ IN SOA ns1.testing.com. admin\\.foo.testing.com. (
                2026051201
                3600
                1800
                1209600
                3600
            )
            www     IN A      192.0.2.2
            ipv6    IN AAAA   2001:db8::1
            ns2     IN A      192.0.2.54
            alias   IN CNAME  www.testing.com.
            @       IN NS     ns1.testing.com.
                   IN MX     10 mail1.testing.com.
            @       IN TXT    "hello world"
            split   IN TXT    "one" "two" "three"

            multitxt IN TXT (
                "this is "
                "multiple txt chunks"
            )
         """;
        ZoneParser testParser = new ZoneParser(input);
        List<ResourceRecord> ourRecords =  testParser.getZoneRecords();
        List<ResourceRecord> testRecords =  new ArrayList<>();

        String[] mname = new String[]{"ns1", "testing", "com"};
        String[] rname = new String[]{"admin.foo", "testing", "com"};
        long serial = 2026051201L;
        long refresh = 3600;
        long retry = 1800;
        long expire = 1209600;
        long minimum = 3600;
        RDataSOA firstRdata = new RDataSOA(mname, rname, serial, refresh, retry, expire, minimum);
        ResourceRecord firstRec = new ResourceRecord( new String[]{"example", "com"}, RRClass.IN, 3200, firstRdata );

        testRecords.add(firstRec);

        RDataA aData = new RDataA((InetAddress.getByName("192.0.2.2").getAddress()));
        ResourceRecord aRec = new ResourceRecord( new String[]{"www", "example", "com"}, RRClass.IN, 3200, aData );
        testRecords.add(aRec);


        RDataAAAA aaaaData = new RDataAAAA(
                InetAddress.getByName("2001:db8::1").getAddress()
        );

        ResourceRecord aaaaRec = new ResourceRecord( new String[]{"ipv6", "example", "com"}, RRClass.IN, 3200, aaaaData );

        testRecords.add(aaaaRec);


        RDataA ns2Data = new RDataA( InetAddress.getByName("192.0.2.54").getAddress() );

        ResourceRecord ns2Rec = new ResourceRecord( new String[]{"ns2", "example", "com"}, RRClass.IN, 3200, ns2Data );
        testRecords.add(ns2Rec);


        RDataCNAME cnameData = new RDataCNAME(
                new String[]{"www", "testing", "com"}
        );

        ResourceRecord cnameRec = new ResourceRecord( new String[]{"alias", "example", "com"}, RRClass.IN, 3200, cnameData );

        testRecords.add(cnameRec);


        RDataNS nsData = new RDataNS(
                new String[]{"ns1", "testing", "com"}
        );

        ResourceRecord nsRec = new ResourceRecord( new String[]{"example", "com"}, RRClass.IN, 3200, nsData );

        testRecords.add(nsRec);


        RDataMX mxData = new RDataMX( 10, new String[]{"mail1", "testing", "com"} );

        ResourceRecord mxRec = new ResourceRecord( new String[]{"example", "com"}, RRClass.IN, 3200, mxData );

        testRecords.add(mxRec);

        /*

        RDataTXT txtData = new RDataTXT(
                new String[]{"hello world"}
        );

        ResourceRecord txtRec = new ResourceRecord( new String[]{"example", "com"}, RRType.TXT, RRClass.IN, 3200, txtData );

        testRecords.add(txtRec);
         */


        /*
        RDataTXT splitTxtData = new RDataTXT( new String[]{"one", "two", "three"} );

        ResourceRecord splitTxtRec = new ResourceRecord( new String[]{"split", "example", "com"}, RRType.TXT, RRClass.IN, 3200, splitTxtData );

        testRecords.add(splitTxtRec);
         */

        /*
        RDataTXT multiTxtData = new RDataTXT( new String[]{"this is ", "multiple txt chunks"} );

        ResourceRecord multiTxtRec = new ResourceRecord( new String[]{"multitxt", "example", "com"}, RRType.TXT, RRClass.IN, 3200, multiTxtData );

        testRecords.add(multiTxtRec);
         */

        // @TODO assertEquals(testRecords.size(), ourRecords.size());
        assertFalse(testParser.hasParserError());

        for (int i = 0; i < testRecords.size(); i++) {
            ResourceRecord ourRecord = ourRecords.get(i);
            ResourceRecord testRecord = testRecords.get(i);

            assertArrayEquals(testRecord.getName(), ourRecord.getName());
            assertEquals(testRecord.getTtl(), ourRecord.getTtl());
            assertEquals(testRecord.getRrclass(), ourRecord.getRrclass());
            assertEquals(testRecord.getType(), ourRecord.getType());

            if (ourRecord.getRdata() instanceof RDataSOA ourSOARData && testRecord.getRdata() instanceof RDataSOA testSOARData) {
                assertArrayEquals(testSOARData.getMname(), ourSOARData.getMname());
                IO.println(Arrays.toString(ourSOARData.getRname()));
                assertArrayEquals(testSOARData.getRname(), ourSOARData.getRname());

                assertEquals(testSOARData.getSerial(), ourSOARData.getSerial());
                assertEquals(testSOARData.getRefresh(), ourSOARData.getRefresh());
                assertEquals(testSOARData.getRetry(), ourSOARData.getRetry());
                assertEquals(testSOARData.getExpire(), ourSOARData.getExpire());
                assertEquals(testSOARData.getMinimum(), ourSOARData.getMinimum());
            }

            if (ourRecord.getRdata() instanceof RDataA ourA &&
                    testRecord.getRdata() instanceof RDataA testA) {
                assertArrayEquals(testA.getAddress(), ourA.getAddress());
            }

            if (ourRecord.getRdata() instanceof RDataAAAA ourAAAA &&
                    testRecord.getRdata() instanceof RDataAAAA testAAAA) {
                assertArrayEquals(testAAAA.getAddress(), ourAAAA.getAddress());
            }

            if (ourRecord.getRdata() instanceof RDataCNAME ourCNAME &&
                    testRecord.getRdata() instanceof RDataCNAME testCNAME) {
                assertArrayEquals(testCNAME.getCname(), ourCNAME.getCname());
            }

            if (ourRecord.getRdata() instanceof RDataNS ourNS &&
                    testRecord.getRdata() instanceof RDataNS testNS) {
                assertArrayEquals(testNS.getNsdname(), ourNS.getNsdname());
            }

            if (ourRecord.getRdata() instanceof RDataMX ourMX &&
                    testRecord.getRdata() instanceof RDataMX testMX) {
                assertEquals(testMX.getPreference(), ourMX.getPreference());
                assertArrayEquals(testMX.getExchange(), ourMX.getExchange());
            }

            if (ourRecord.getRdata() instanceof RDataTXT ourTXT &&
                    testRecord.getRdata() instanceof RDataTXT testTXT) {
//@TODO                assertArrayEquals(testTXT.getTxtdata(), ourTXT.getTxtdata());
            }
        }
    }

    @Test
    void testFileParser() {
        try {
            //Path file = Path.of("zones/small.zone");
            Path file = Path.of("dnszones/dnssec.zone");
            //IO.println("Zone file: " + file.getFileName());
            ZoneParser testParser = new ZoneParser(Files.readString(file));
            IO.println("Record amount: " + testParser.getZoneRecords().size());
            for (ResourceRecord r : testParser.getZoneRecords()) {
               IO.println(r);
            }
            assertFalse(testParser.hasParserError());
        } catch (IOException e) {
            System.err.println("ERROR: File not found");
        }
    }


    @Test
    void testLeadingTtlUsesPreviousName() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.
        www IN A 192.0.2.1
        3600 IN A 192.0.2.2
        """);

        assertFalse(parser.hasParserError());

        List<ResourceRecord> records = parser.getZoneRecords();
        assertEquals(2, records.size());

        assertArrayEquals(new String[]{"www", "example", "com"}, records.get(1).getName());
        assertEquals(3600, records.get(1).getTtl());
    }

    @Test
    void testNumericOwnerWithExplicitTtl() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.
        123 3600 IN A 192.0.2.1
        """);

        assertFalse(parser.hasParserError());

        ResourceRecord record = parser.getZoneRecords().getFirst();
        assertArrayEquals(new String[]{"123", "example", "com"}, record.getName());
        assertEquals(3600, record.getTtl());
    }

    @Test
    void testInvalidMXOrderFails() {
        String err = captureStderr(() -> {
            ZoneParser parser = new ZoneParser("""
            $ORIGIN example.com.
            @ IN MX mail.example.com. 10
            """);
            assertTrue(parser.hasParserError());
        });

        assertTrue(err.contains("MX preference must be an integer"));
    }

    @Test
    void testMxPreferenceZeroIsValid() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.
        @ IN MX 0 mail.example.com.
        """);

        assertFalse(parser.hasParserError());

        RDataMX mx = (RDataMX) parser.getZoneRecords().getFirst().getRdata();
        assertEquals(0, mx.getPreference());
    }

    @Test
    void testInvalidEmptyLabelFails() {
        String err = captureStderr(() -> {
            ZoneParser parser = new ZoneParser("""
            $ORIGIN example.com.
            bad IN CNAME a..b.example.com.
            """);
            assertTrue(parser.hasParserError());
        });

        assertTrue(err.contains("CNAME domain name contains empty label"));
    }

    @Test
    void testDirectiveInsideRecordFails() {
        String err = captureStderr(() -> {
            ZoneParser parser = new ZoneParser("""
            $ORIGIN example.com.
            txt IN TXT (
                "hello"
                $TTL 300
            )
            """);

            assertTrue(parser.hasParserError());
        });

        assertTrue(err.contains("$TTL not allowed inside record"));
    }
    @Test
    void testTtlSuffixes() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.
        $TTL 1h
        www 30m IN A 192.0.2.1
        """);

        assertFalse(parser.hasParserError());

        assertEquals(3600, parser.getTTL());
        assertEquals(1800, parser.getZoneRecords().getFirst().getTtl());
    }
    @Test
    void testSoaDurationSuffixes() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.
        @ IN SOA ns.example.com. admin.example.com. (
            2026051201
            1h
            30m
            2w
            1h
        )
        """);

        assertFalse(parser.hasParserError());

        RDataSOA soa = (RDataSOA) parser.getZoneRecords().getFirst().getRdata();
        assertEquals(3600, soa.getRefresh());
        assertEquals(1800, soa.getRetry());
        assertEquals(1209600, soa.getExpire());
        assertEquals(3600, soa.getMinimum());
    }
    @Test
    void testInvalidTtlSuffixFails() {
        String err = captureStderr(() -> {
            ZoneParser parser = new ZoneParser("""
            $ORIGIN example.com.
            $TTL 1x
            www IN A 192.0.2.1
            """);
            assertTrue(parser.hasParserError());
        });

        assertTrue(err.contains("Invalid TTL: 1x"));
    }

    @Test
    void testEscapedDomainLabels() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.

        foo\\.bar      IN A     192.0.2.1
        literal\\@     IN TXT   "hello"
        price\\$tag    IN TXT   "world"
        semi\\;colon   IN TXT   "test"
        space\\ label  IN TXT   "space"
        """);

        assertFalse(parser.hasParserError());
        List<ResourceRecord> records = parser.getZoneRecords();
        for (var record : records) {
            System.out.println(Arrays.toString(record.getName()));
        }

        assertEquals( "[foo.bar, example, com]", Arrays.toString(records.get(0).getName()) );
        assertEquals( "[literal@, example, com]", Arrays.toString(records.get(1).getName()) );
        assertEquals( "[price$tag, example, com]", Arrays.toString(records.get(2).getName()) );
        assertEquals( "[semi;colon, example, com]", Arrays.toString(records.get(3).getName()) );
        assertEquals( "[space label, example, com]", Arrays.toString(records.get(4).getName()) );
    }
    @Test
    void testDecimalEscapedDomainLabels() {
        ZoneParser parser = new ZoneParser("""
    $ORIGIN example.com.

    foo\\046bar        IN A     192.0.2.1
    hello\\032world    IN TXT   "space"
    literal\\064sign   IN TXT   "at"
    backslash\\092test IN TXT   "slash"
    """);
        assertFalse(parser.hasParserError());
        var records = parser.getZoneRecords();
        for (var record : records) {
            System.out.println(Arrays.toString(record.getName()));
        }
        assertEquals("[foo.bar, example, com]", Arrays.toString(records.get(0).getName()));
        assertEquals("[hello world, example, com]", Arrays.toString(records.get(1).getName()));
        assertEquals("[literal@sign, example, com]", Arrays.toString(records.get(2).getName()));
        assertEquals("[backslash\\test, example, com]", Arrays.toString(records.get(3).getName()));
    }

    @Test
    void testTxtDecimalEscapes() {
        ZoneParser parser = new ZoneParser("""
    $ORIGIN example.com.

    @ IN TXT "hello\\032world"
    @ IN TXT "price\\036tag"
    @ IN TXT "quote\\034test\\034"
    @ IN TXT "backslash\\092test"
    """);

        assertFalse(parser.hasParserError());

        var records = parser.getZoneRecords();

        RDataTXT txt1 = (RDataTXT) records.get(0).getRdata();
        RDataTXT txt2 = (RDataTXT) records.get(1).getRdata();
        RDataTXT txt3 = (RDataTXT) records.get(2).getRdata();
        RDataTXT txt4 = (RDataTXT) records.get(3).getRdata();

        /* @TODO
        System.out.println(txt1.getTxtdata()[0]);
        System.out.println(txt2.getTxtdata()[0]);
        System.out.println(txt3.getTxtdata()[0]);
        System.out.println(txt4.getTxtdata()[0]);

        assertEquals("hello world", txt1.getTxtdata()[0]);
        assertEquals("price$tag", txt2.getTxtdata()[0]);
        assertEquals("quote\"test\"", txt3.getTxtdata()[0]);
        assertEquals("backslash\\test", txt4.getTxtdata()[0]);
         */
        assertEquals("hello world", txt1.getTxtdata());
        assertEquals("price$tag", txt2.getTxtdata());
        assertEquals("quote\"test\"", txt3.getTxtdata());
        assertEquals("backslash\\test", txt4.getTxtdata());
    }


    @Test
    void testRecordFinishesOnNewlineAfterClosingParen() {
        ZoneParser parser = new ZoneParser("""
        $ORIGIN example.com.
        @ IN TXT (
            "hello"
            "world"
        )
        www IN A 192.0.2.1
        """);
        assertFalse(parser.hasParserError());

        List<ResourceRecord> records = parser.getZoneRecords();
        assertEquals(2, records.size());

        assertEquals(RRType.TXT, records.get(0).getType());
        assertEquals(RRType.A, records.get(1).getType());

        assertArrayEquals(new String[]{"example", "com"}, records.get(0).getName());
        assertArrayEquals(new String[]{"www", "example", "com"}, records.get(1).getName());

        RDataTXT txt = (RDataTXT) records.get(0).getRdata();
        //assertArrayEquals(new String[]{"hello", "world"}, txt.getTxtdata());
    }
    @Test
    void testTokensAfterClosingParenOnSameLineAreTrailingGarbage() {
        String err = captureStderr(() -> {
            ZoneParser parser = new ZoneParser("""
            $ORIGIN example.com.
            @ IN TXT (
                "hello"
            ) www IN A 192.0.2.1
            """);
            List<ResourceRecord> records = parser.getZoneRecords();
            for (ResourceRecord r : records){
                IO.println(r);
            }
            assertTrue(parser.hasParserError());
        });

        assertTrue(err.contains("invalid"));
    }

    @Test
    void testIncludeFile() throws IOException {
        ZoneParser parser = new ZoneParser( Files.readString(Path.of("src/test/java/io/gitlab/teamproject2026/zones/db.example.com")) );

        List<ResourceRecord> records = parser.getZoneRecords();
        for (ResourceRecord r: records){
            IO.println(r);
        }
        assertEquals(6, records.size());

        assertEquals(RRType.SOA, records.get(0).getType());
        assertArrayEquals(new String[]{"example", "com"}, records.get(0).getName());

        assertEquals(RRType.NS, records.get(1).getType());
        assertArrayEquals(new String[]{"example", "com"}, records.get(1).getName());

        assertEquals(RRType.A, records.get(2).getType());
        assertArrayEquals(new String[]{"ns1", "example", "com"}, records.get(2).getName());

        assertEquals(RRType.A, records.get(3).getType());
        assertArrayEquals(new String[]{"api", "sub", "example", "com"}, records.get(3).getName());

        assertEquals(RRType.A, records.get(4).getType());
        assertArrayEquals(new String[]{"mail", "sub", "example", "com"}, records.get(4).getName());

        assertEquals(RRType.A, records.get(5).getType());
        assertArrayEquals(new String[]{"www", "example", "com"}, records.get(5).getName());
    }}
