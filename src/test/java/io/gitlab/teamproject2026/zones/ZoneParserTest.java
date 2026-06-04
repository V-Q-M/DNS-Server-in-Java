package io.gitlab.teamproject2026.zones;

import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.Nested;
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

    @Nested
    class ValidZoneParsing {

        @Test
        void parseFullZoneWithCommonRecordTypes() throws UnknownHostException {
            String input = """
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
                    2      IN PTR www.example.com.
                    54     IN PTR ns2.example.com.
                    1      IN PTR ipv6.examp.e.com.
                    """;

            ZoneParser testParser = new ZoneParser(input);
            List<ResourceRecord> ourRecords = testParser.getZoneRecords();
            List<ResourceRecord> testRecords = new ArrayList<>();

            RDataSOA firstRdata = new RDataSOA(
                    new String[]{"ns1", "testing", "com"},
                    new String[]{"admin.foo", "testing", "com"},
                    2026051201L,
                    3600,
                    1800,
                    1209600,
                    3600
            );
            testRecords.add(new ResourceRecord(
                    new String[]{"example", "com"}, RRClass.IN, 3200, firstRdata));

            testRecords.add(new ResourceRecord(
                    new String[]{"www", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataA(InetAddress.getByName("192.0.2.2").getAddress())
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"ipv6", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataAAAA(InetAddress.getByName("2001:db8::1").getAddress())
            ));

            ResourceRecord ns2Rec = new ResourceRecord(
                    new String[]{"ns2", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataA(InetAddress.getByName("192.0.2.54").getAddress())
            );
            testRecords.add(ns2Rec);

            testRecords.add(new ResourceRecord(
                    new String[]{"alias", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataCNAME(new String[]{"www", "testing", "com"})
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataNS(new String[]{"ns1", "testing", "com"})
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataMX(10, new String[]{"mail1", "testing", "com"})
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataTXT("hello world")
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"split", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataTXT("one", "two", "three")
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"multitxt", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataTXT("this is ", "multiple txt chunks")
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"2", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataPTR(new String[]{"www", "example", "com"})
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"54", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataPTR(new String[]{"ns2", "example", "com"})
            ));

            testRecords.add(new ResourceRecord(
                    new String[]{"1", "example", "com"},
                    RRClass.IN,
                    3200,
                    new RDataPTR(new String[]{"ipv6", "examp", "e", "com"})
            ));

            assertEquals(testRecords.size(), ourRecords.size());
            assertFalse(testParser.hasParserError());

            for (int i = 0; i < testRecords.size(); i++) {
                ResourceRecord ourRecord = ourRecords.get(i);
                ResourceRecord testRecord = testRecords.get(i);

                assertArrayEquals(testRecord.getName(), ourRecord.getName());
                assertEquals(testRecord.getTtl(), ourRecord.getTtl());
                assertEquals(testRecord.getRrclass(), ourRecord.getRrclass());
                assertEquals(testRecord.getType(), ourRecord.getType());

                if (ourRecord.getRdata() instanceof RDataSOA ourSOA
                        && testRecord.getRdata() instanceof RDataSOA testSOA) {
                    assertArrayEquals(testSOA.getMname(), ourSOA.getMname());
                    assertArrayEquals(testSOA.getRname(), ourSOA.getRname());
                    assertEquals(testSOA.getSerial(), ourSOA.getSerial());
                    assertEquals(testSOA.getRefresh(), ourSOA.getRefresh());
                    assertEquals(testSOA.getRetry(), ourSOA.getRetry());
                    assertEquals(testSOA.getExpire(), ourSOA.getExpire());
                    assertEquals(testSOA.getMinimum(), ourSOA.getMinimum());
                }

                if (ourRecord.getRdata() instanceof RDataA ourA
                        && testRecord.getRdata() instanceof RDataA testA) {
                    assertArrayEquals(testA.getAddress(), ourA.getAddress());
                }

                if (ourRecord.getRdata() instanceof RDataAAAA ourAAAA
                        && testRecord.getRdata() instanceof RDataAAAA testAAAA) {
                    assertArrayEquals(testAAAA.getAddress(), ourAAAA.getAddress());
                }

                if (ourRecord.getRdata() instanceof RDataCNAME ourCNAME
                        && testRecord.getRdata() instanceof RDataCNAME testCNAME) {
                    assertArrayEquals(testCNAME.getCname(), ourCNAME.getCname());
                }

                if (ourRecord.getRdata() instanceof RDataNS ourNS
                        && testRecord.getRdata() instanceof RDataNS testNS) {
                    assertArrayEquals(testNS.getNsdname(), ourNS.getNsdname());
                }

                if (ourRecord.getRdata() instanceof RDataMX ourMX
                        && testRecord.getRdata() instanceof RDataMX testMX) {
                    assertEquals(testMX.getPreference(), ourMX.getPreference());
                    assertArrayEquals(testMX.getExchange(), ourMX.getExchange());
                }

                if (ourRecord.getRdata() instanceof RDataTXT ourTXT
                        && testRecord.getRdata() instanceof RDataTXT testTXT) {
                    assertArrayEquals(testTXT.getTxtdata(), ourTXT.getTxtdata());
                }

                if (ourRecord.getRdata() instanceof RDataPTR ourPTR
                        && testRecord.getRdata() instanceof RDataPTR testPTR) {
                    assertArrayEquals(testPTR.getPtrdname(), ourPTR.getPtrdname());
                }
            }
        }

        @Test
        void parsesZoneFileFromDisk() {
            try {
                ConfigManager.setZoneDir("src/test/java/io/gitlab/teamproject2026/zones/");
                ZoneParser testParser = new ZoneParser(
                        Files.readString(Path.of("src/test/java/io/gitlab/teamproject2026/zones/db.129.0.0"))
                );

                IO.println("Record amount: " + testParser.getZoneRecords().size());
                for (ResourceRecord r : testParser.getZoneRecords()) {
                    IO.println(r);
                }

                assertFalse(testParser.hasParserError());
            } catch (IOException e) {
                System.err.println("ERROR: File not found");
                fail();
            }
        }

        @Test
        void ignoresComments() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com. ; origin comment
                    @ IN SOA ns.example.com. admin.example.com. (
                        2026051201 ; serial
                        3600
                        1800
                        1209600
                        3600
                    )
                    @ IN NS ns.example.com ; name server
                    www IN A 192.0.2.1 ; host address
                    """);

            assertFalse(parser.hasParserError());
            assertEquals(3, parser.getZoneRecords().size());
        }
    }

    @Nested
    class OwnerAndWhitespace {

        @Test
        void leadingTtlUsesPreviousName() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    www IN A 192.0.2.1
                        3600 IN A 192.0.2.2
                    """, false);

            assertFalse(parser.hasParserError());

            List<ResourceRecord> records = parser.getZoneRecords();
            assertEquals(2, records.size());

            assertArrayEquals(new String[]{"www", "example", "com"}, records.get(1).getName());
            assertEquals(3600, records.get(1).getTtl());
        }

        @Test
        void numericOwnerWithExplicitTtl() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    123 3600 IN A 192.0.2.1
                    """, false);

            assertFalse(parser.hasParserError());

            ResourceRecord record = parser.getZoneRecords().getFirst();
            assertArrayEquals(new String[]{"123", "example", "com"}, record.getName());
            assertEquals(3600, record.getTtl());
        }

        @Test
        void numericOwnerWithoutLeadingSpace() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    1 IN PTR host.example.com.
                    """, false);

            assertFalse(parser.hasParserError());

            ResourceRecord record = parser.getZoneRecords().getFirst();
            assertArrayEquals(new String[]{"1", "example", "com"}, record.getName());
            assertEquals(RRType.PTR, record.getType());
        }

        @Test
        void leadingSpaceWithoutPreviousOwnerFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                            IN A 192.0.2.1
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("no previous owner"));
        }
    }

    @Nested
    class TtlParsing {

        @Test
        void ttlSuffixes() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    $TTL 1h
                    www 30m IN A 192.0.2.1
                    """, false);

            assertFalse(parser.hasParserError());
            assertEquals(3600, parser.getTTL());
            assertEquals(1800, parser.getZoneRecords().getFirst().getTtl());
        }

        @Test
        void soaDurationSuffixes() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    @ IN SOA ns.example.com. admin.example.com. (
                        2026051201
                        1h
                        30m
                        2w
                        1h
                    )
                    @ IN NS ns.example.com
                    """);

            assertFalse(parser.hasParserError());

            RDataSOA soa = (RDataSOA) parser.getZoneRecords().getFirst().getRdata();
            assertEquals(3600, soa.getRefresh());
            assertEquals(1800, soa.getRetry());
            assertEquals(1209600, soa.getExpire());
            assertEquals(3600, soa.getMinimum());
        }

        @Test
        void invalidTtlSuffixFails() {
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
        void duplicateTtlInRecordFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                www 300 400 IN A 192.0.2.1
                """, false);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("Duplicate TTL"));
        }

        @Test
        void ttlOverflowFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                $TTL 4294967296
                www IN A 192.0.2.1
                """, false);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("out of range"));
        }
    }

    @Nested
    class RDataParsing {

        @Test
        void mxPreferenceZeroIsValid() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    @ IN MX 0 mail.example.com.
                    """, false);

            assertFalse(parser.hasParserError());

            RDataMX mx = (RDataMX) parser.getZoneRecords().getFirst().getRdata();
            assertEquals(0, mx.getPreference());
        }

        @Test
        void invalidMxOrderFails() {
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
        void escapedDomainLabels() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.

                    foo\\.bar      IN A     192.0.2.1
                    literal\\@     IN TXT   "hello"
                    price\\$tag    IN TXT   "world"
                    semi\\;colon   IN TXT   "test"
                    space\\ label  IN TXT   "space"
                    """, false);

            assertFalse(parser.hasParserError());

            List<ResourceRecord> records = parser.getZoneRecords();
            for (var record : records) {
                System.out.println(Arrays.toString(record.getName()));
            }

            assertEquals("[foo.bar, example, com]", Arrays.toString(records.get(0).getName()));
            assertEquals("[literal@, example, com]", Arrays.toString(records.get(1).getName()));
            assertEquals("[price$tag, example, com]", Arrays.toString(records.get(2).getName()));
            assertEquals("[semi;colon, example, com]", Arrays.toString(records.get(3).getName()));
            assertEquals("[space label, example, com]", Arrays.toString(records.get(4).getName()));
        }

        @Test
        void decimalEscapedDomainLabels() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.

                    foo\\046bar        IN A     192.0.2.1
                    hello\\032world    IN TXT   "space"
                    literal\\064sign   IN TXT   "at"
                    backslash\\092test IN TXT   "slash"
                    """, false);

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
        void txtDecimalEscapes() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.

                    @ IN TXT "hello\\032world"
                    @ IN TXT "price\\036tag"
                    @ IN TXT "quote\\034test\\034"
                    @ IN TXT "backslash\\092test"
                    """, false);

            assertFalse(parser.hasParserError());

            var records = parser.getZoneRecords();

            RDataTXT txt1 = (RDataTXT) records.get(0).getRdata();
            RDataTXT txt2 = (RDataTXT) records.get(1).getRdata();
            RDataTXT txt3 = (RDataTXT) records.get(2).getRdata();
            RDataTXT txt4 = (RDataTXT) records.get(3).getRdata();

            assertEquals("hello world", txt1.getTxtdata()[0]);
            assertEquals("price$tag", txt2.getTxtdata()[0]);
            assertEquals("quote\"test\"", txt3.getTxtdata()[0]);
            assertEquals("backslash\\test", txt4.getTxtdata()[0]);
        }

        @Test
        void invalidIpv4Fails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                www IN A 999.0.2.1
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("A record requires an IPv4 address"));
        }

        @Test
        void invalidIpv6Fails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                ipv6 IN AAAA not-an-ipv6-address
                """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("AAAA record requires an IPv6 address"));
        }

        @Test
        void missingARecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                www IN A
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for A"));
        }

        @Test
        void missingAAAARecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                ipv6 IN AAAA
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for AAAA"));
        }

        @Test
        void missingMXRecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN MX
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for MX"));
        }

        @Test
        void missingNSRecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN NS
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for NS"));
        }

        @Test
        void missingCNAMERecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                alias IN CNAME
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for CNAME"));
        }

        @Test
        void missingPTRRecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                1 IN PTR
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for PTR"));
        }

        @Test
        void missingTXTRecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN TXT
                """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Missing RDATA for TXT"));
        }

        @Test
        void missingSOARecordRdataFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                )
                """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("SOA record requires mname, rname, serial, refresh, retry, expire, and minimum"));
        }

        @Test
        void mxPreferenceOutOfRangeFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                @ IN MX 70000 mail.example.com.
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("MX preference out of range"));
        }

        @Test
        void soaSerialOutOfRangeFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    4294967296
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("serial"));
        }

        @Test
        void txtStringTooLongFails() {
            String longTxt = "a".repeat(256);

            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser(
                        "$ORIGIN example.com.\n" +
                                "@ IN SOA ns.example.com. admin.example.com. (\n" +
                                "2026051201\n" +
                                "3600\n" +
                                "1800\n" +
                                "1209600\n" +
                                "3600\n" +
                                ")\n" +
                                "@ IN NS ns.example.com.\n" +
                                "@ IN TXT \"" + longTxt + "\"\n"
                );

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("TXT string too long"));
        }

        @Test
        void txtUnfinishedEscapeFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                @ IN TXT "hello\\"
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("Unterminated TXT string"));
        }
    }

    @Nested
    class MultilineRecords {

        @Test
        void recordFinishesOnNewlineAfterClosingParen() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    @ IN TXT (
                        "hello"
                        "world"
                    )
                    www IN A 192.0.2.1
                    """, false);

            assertFalse(parser.hasParserError());

            List<ResourceRecord> records = parser.getZoneRecords();
            assertEquals(2, records.size());

            assertEquals(RRType.TXT, records.get(0).getType());
            assertEquals(RRType.A, records.get(1).getType());

            assertArrayEquals(new String[]{"example", "com"}, records.get(0).getName());
            assertArrayEquals(new String[]{"www", "example", "com"}, records.get(1).getName());

            RDataTXT txt = (RDataTXT) records.get(0).getRdata();
            assertArrayEquals(new String[]{"hello", "world"}, txt.getTxtdata());
        }

        @Test
        void tokensAfterClosingParenOnSameLineAreTrailingGarbage() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN TXT (
                            "hello"
                        ) www IN A 192.0.2.1
                        """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.toLowerCase().contains("invalid"));
        }

        @Test
        void directiveInsideRecordFails() {
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
        void unclosedParenthesesFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN TXT (
                    "hello"
                    "world"
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("Unclosed parentheses"));
        }

        @Test
        void closedParenthesisWithoutOpeningFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN TXT "hello"
                )
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("Closed parenthesis without opening"));
        }
        @Test
        void nestedParenthesesFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN TXT (
                    (
                    "hello"
                    )
                )
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("Nested parentheses are not allowed"));
        }
    }

    @Nested
    class IncludeDirective {

        @Test
        void includeFile() throws IOException {
            ConfigManager.setZoneDir("src/test/java/io/gitlab/teamproject2026/zones/");
            ZoneParser parser = new ZoneParser(
                    Files.readString(Path.of("src/test/java/io/gitlab/teamproject2026/zones/db.example.com"))
            );

            List<ResourceRecord> records = parser.getZoneRecords();
            /*
            for (ResourceRecord r : records) {
                IO.println(r);
            }
             */

            assertFalse(parser.hasParserError());
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
        }

        @Test
        void includeFileWithoutSoaIsAllowed() throws IOException {
            Path tempDir = Files.createTempDirectory("zone-test");
            Path include = tempDir.resolve("included.zone");

            Files.writeString(include, "api IN A 192.0.2.10\n");

            ConfigManager.setZoneDir(tempDir.toString());

            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    @ IN SOA ns.example.com. admin.example.com. (
                        2026051201
                        3600
                        1800
                        1209600
                        3600
                    )
                    @ IN NS ns.example.com
                    $INCLUDE included.zone
                    """);

            assertFalse(parser.hasParserError());
            assertEquals(3, parser.getZoneRecords().size());
        }

        @Test
        void includeFileWithSoaFails() {
            String err = captureStderr(() -> {
                Path tempDir;
                try {
                    tempDir = Files.createTempDirectory("zone-test");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                Path include = tempDir.resolve("included.zone");

                try {
                    Files.writeString(include, """
                            @ IN SOA ns.example.com. admin.example.com. (
                                2026051201
                                3600
                                1800
                                1209600
                                3600
                            )
                            """);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                ConfigManager.setZoneDir(tempDir.toString());

                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN SOA ns.example.com. admin.example.com. (
                            2026051201
                            3600
                            1800
                            1209600
                            3600
                        )
                        $INCLUDE included.zone
                        """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("must not contain more than one SOA"));
            assertTrue(err.contains("Failed to parse"));
        }
    }

    @Nested
    class ZoneValidation {

        @Test
        void duplicateSoaFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN SOA ns1.example.com. admin.example.com. (
                            2026051201
                            3600
                            1800
                            1209600
                            3600
                        )
                        @ IN SOA ns2.example.com. admin.example.com. (
                            2026051202
                            3600
                            1800
                            1209600
                            3600
                        )
                        """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("more than one SOA"));
        }

        @Test
        void missingSoaFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    $TTL 3200
                    www IN A 192.0.2.1
                    """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("must contain exactly one SOA"));
        }

        @Test
        void missingNsFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN SOA ns1.example.com. admin.example.com. (
                            2026051201
                            3600
                            1800
                            1209600
                            3600
                        )
                        www IN A 192.0.2.1
                        """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("must contain at least one NS"));
        }

        @Test
        void unsupportedClassFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                www CH A 192.0.2.1
                """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Unexpected token before RR type"));
        }

        @Test
        void recordWithoutTypeFails() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                $ORIGIN example.com.
                @ IN SOA ns.example.com. admin.example.com. (
                    2026051201
                    3600
                    1800
                    1209600
                    3600
                )
                @ IN NS ns.example.com.
                www 3600 IN
                """);

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("Record has no RR type"));
        }
    }

    @Nested
    class DomainValidation {

        @Test
        void invalidEmptyLabelFails() {
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
        void domainLabelTooLongFails() {
            String longLabel = "a".repeat(64);

            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser(
                        "$ORIGIN example.com.\n" +
                                "@ IN SOA ns.example.com. admin.example.com. (\n" +
                                "2026051201\n" +
                                "3600\n" +
                                "1800\n" +
                                "1209600\n" +
                                "3600\n" +
                                ")\n" +
                                "@ IN NS ns.example.com.\n" +
                                longLabel + " IN A 192.0.2.1\n"
                );

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("label too long"));
        }

        @Test
        void domainNameTooLongFails() {
            String label = "a".repeat(63);

            String longName =
                    label + "." +
                            label + "." +
                            label + "." +
                            label + ".com";

            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser(
                        "$ORIGIN example.com.\n" +
                                "@ IN SOA ns.example.com. admin.example.com. (\n" +
                                "2026051201\n" +
                                "3600\n" +
                                "1800\n" +
                                "1209600\n" +
                                "3600\n" +
                                ")\n" +
                                "@ IN NS ns.example.com.\n" +
                                longName + " IN A 192.0.2.1\n"
                );

                assertTrue(parser.hasParserError());
            });
            assertTrue(err.contains("is too long"));
        }
    }
}