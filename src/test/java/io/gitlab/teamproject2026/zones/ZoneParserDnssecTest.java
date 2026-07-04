package io.gitlab.teamproject2026.zones;

import static org.junit.jupiter.api.Assertions.*;

import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ZoneParserDnssecTest {

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
    class ValidDnssecRecords {

        @Test
        void parsesDnskeyDsRrsigNsecNsec3AndNsec3param() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    $TTL 3600

                    @ IN DNSKEY 256 3 8 AQIDBA==
                    @ IN DS 12345 8 2 A1B2C3D4
                    @ IN RRSIG A 8 2 3600 20260516000000 20260515000000 12345 example.com. AQIDBA==
                    @ IN NSEC next.example.com. A NS SOA MX RRSIG NSEC DNSKEY
                    abcde IN NSEC3 1 0 12 - ABCDEF A NS SOA RRSIG DNSKEY NSEC3PARAM
                    @ IN NSEC3PARAM 1 0 12 -
                    """, false);

            assertFalse(parser.hasParserError());

            List<ResourceRecord> records = parser.getZoneRecords();
            assertEquals(6, records.size());

            ResourceRecord dnskeyRecord = records.get(0);
            assertArrayEquals(new String[] {"example", "com"}, dnskeyRecord.getName());
            assertEquals(RRType.DNSKEY, dnskeyRecord.getType());

            RDataDNSKEY dnskey = (RDataDNSKEY) dnskeyRecord.getRdata();
            assertEquals(256, dnskey.getFlags());
            assertEquals(3, dnskey.getProtocol());
            assertEquals(8, dnskey.getAlgorithm());
            assertArrayEquals(new byte[] {1, 2, 3, 4}, dnskey.getPublickey());

            ResourceRecord dsRecord = records.get(1);
            assertArrayEquals(new String[] {"example", "com"}, dsRecord.getName());
            assertEquals(RRType.DS, dsRecord.getType());

            RDataDS ds = (RDataDS) dsRecord.getRdata();
            assertEquals(12345, ds.getKeyTag());
            assertEquals(8, ds.getAlgorithm());
            assertEquals(2, ds.getDigestType());
            assertArrayEquals(new byte[] {(byte) 0xA1, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4}, ds.getDigest());

            ResourceRecord rrsigRecord = records.get(2);
            assertArrayEquals(new String[] {"example", "com"}, rrsigRecord.getName());
            assertEquals(RRType.RRSIG, rrsigRecord.getType());

            RDataRRSIG rrsig = (RDataRRSIG) rrsigRecord.getRdata();
            assertEquals(RRType.A, rrsig.getTypeCovered());
            assertEquals(8, rrsig.getAlgorithm());
            assertEquals(2, rrsig.getLabels());
            assertEquals(3600, rrsig.getOriginalTtl());
            assertEquals(12345, rrsig.getKeyTag());
            assertArrayEquals(new String[] {"example", "com"}, rrsig.getSignerName());
            assertArrayEquals(new byte[] {1, 2, 3, 4}, rrsig.getSignature());

            ResourceRecord nsecRecord = records.get(3);
            assertArrayEquals(new String[] {"example", "com"}, nsecRecord.getName());
            assertEquals(RRType.NSEC, nsecRecord.getType());

            RDataNSEC nsec = (RDataNSEC) nsecRecord.getRdata();
            assertArrayEquals(new String[] {"next", "example", "com"}, nsec.getNextDomainName());
            assertNotNull(nsec.getTypeBitMaps());
            assertTrue(nsec.getTypeBitMaps().length > 0);

            ResourceRecord nsec3Record = records.get(4);
            assertArrayEquals(new String[] {"abcde", "example", "com"}, nsec3Record.getName());
            assertEquals(RRType.NSEC3, nsec3Record.getType());

            RDataNSEC3 nsec3 = (RDataNSEC3) nsec3Record.getRdata();
            assertEquals(1, nsec3.getHashAlgorithm());
            assertEquals(0, nsec3.getFlags());
            assertEquals(12, nsec3.getIterations());
            assertArrayEquals(new byte[0], nsec3.getSalt());
            assertArrayEquals("ABCDEF".getBytes(), nsec3.getNextHashedOwnerName());
            assertNotNull(nsec3.getTypeBitMaps());
            assertTrue(nsec3.getTypeBitMaps().length > 0);

            ResourceRecord nsec3paramRecord = records.get(5);
            assertArrayEquals(new String[] {"example", "com"}, nsec3paramRecord.getName());
            assertEquals(RRType.NSEC3PARAM, nsec3paramRecord.getType());

            RDataNSEC3PARAM nsec3param = (RDataNSEC3PARAM) nsec3paramRecord.getRdata();
            assertEquals(1, nsec3param.getHashAlgorithm());
            assertEquals(0, nsec3param.getFlags());
            assertEquals(12, nsec3param.getIterations());
            assertArrayEquals(new byte[0], nsec3param.getSalt());
        }

        @Test
        void parsesMultilineBase64Fields() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    $TTL 3600

                    @ IN DNSKEY 256 3 8 (
                        AQID
                        BA==
                    )

                    @ IN RRSIG DNSKEY 8 2 3600 (
                        20260516000000
                        20260515000000
                        12345
                        example.com.
                        AQID
                        BA==
                    )
                    """, false);

            assertFalse(parser.hasParserError());

            RDataDNSKEY dnskey = (RDataDNSKEY) parser.getZoneRecords().get(0).getRdata();
            assertArrayEquals(new byte[] {1, 2, 3, 4}, dnskey.getPublickey());

            RDataRRSIG rrsig = (RDataRRSIG) parser.getZoneRecords().get(1).getRdata();
            assertEquals(RRType.DNSKEY, rrsig.getTypeCovered());
            assertArrayEquals(new byte[] {1, 2, 3, 4}, rrsig.getSignature());
        }

        @Test
        void parsesNsec3paramWithSalt() {
            ZoneParser parser = new ZoneParser("""
                    $ORIGIN example.com.
                    $TTL 3600
                    @ IN NSEC3PARAM 1 0 12 A1B2C3D4
                    """, false);

            assertFalse(parser.hasParserError());

            RDataNSEC3PARAM nsec3param =
                    (RDataNSEC3PARAM) parser.getZoneRecords().getFirst().getRdata();

            assertEquals(1, nsec3param.getHashAlgorithm());
            assertEquals(0, nsec3param.getFlags());
            assertEquals(12, nsec3param.getIterations());
            assertArrayEquals(new byte[] {(byte) 0xA1, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4}, nsec3param.getSalt());
        }
    }

    @Nested
    class InvalidDnssecRdata {

        @Test
        void dnskeyProtocolMustBeThree() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN DNSKEY 256 2 8 AQIDBA==
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("DNSKEY protocol must be 3"));
        }

        @Test
        void dnskeyPublicKeyMustBeBase64() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN DNSKEY 256 3 8 not-base64!
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid DNSKEY public key base64"));
        }

        @Test
        void dsDigestMustBeHex() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN DS 12345 8 2 XYZ
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid DS digest hex"));
        }

        @Test
        void dsDigestMustHaveEvenHexLength() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN DS 12345 8 2 A1B
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid DS digest hex"));
        }

        @Test
        void rrsigTypeCoveredMustBeKnownType() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN RRSIG NOTATYPE 8 2 3600 20260516000000 20260515000000 12345 example.com. AQIDBA==
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("RRSIG type covered must be an RR type"));
        }

        @Test
        void rrsigSignatureMustBeBase64() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN RRSIG A 8 2 3600 20260516000000 20260515000000 12345 example.com. definitely-not-base64!
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid RRSIG signature base64"));
        }

        @Test
        void nsecTypeBitmapMustOnlyContainKnownTypes() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN NSEC next.example.com. A NS NOTATYPE DNSKEY
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid NSEC RR type"));
        }

        @Test
        void nsec3TypeBitmapMustOnlyContainKnownTypes() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        abcde IN NSEC3 1 0 12 - ABCDEF A NS NOTATYPE DNSKEY
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid NSEC3 RR type"));
        }

        @Test
        void nsec3SaltMustBeHexOrDash() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        abcde IN NSEC3 1 0 12 not-hex ABCDEF A NS DNSKEY
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid NSEC3 salt hex"));
        }

        @Test
        void nsec3paramSaltMustBeHexOrDash() {
            String err = captureStderr(() -> {
                ZoneParser parser = new ZoneParser("""
                        $ORIGIN example.com.
                        @ IN NSEC3PARAM 1 0 12 not-hex
                        """, false);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Invalid NSEC3PARAM salt hex"));
        }
    }

    @Nested
    class IntegrationTest {
        @Test
        void parsesSignedExampleZoneFileFromDisk() {
            try {
                ConfigManager.setZoneDir("src/test/java/io/gitlab/teamproject2026/zones/");

                ZoneParser parser = new ZoneParser(
                        Files.readString(Path.of("src/test/java/io/gitlab/teamproject2026/zones/db.dnssec.com")));

                List<ResourceRecord> records = parser.getZoneRecords();

                assertFalse(parser.hasParserError());
                assertEquals(22, records.size());

                assertEquals(RRType.SOA, records.get(0).getType());
                assertArrayEquals(
                        new String[] {"example", "com"}, records.get(0).getName());

                assertEquals(RRType.RRSIG, records.get(1).getType());
                assertArrayEquals(
                        new String[] {"example", "com"}, records.get(1).getName());
                RDataRRSIG soaSig = (RDataRRSIG) records.get(1).getRdata();
                assertEquals(RRType.SOA, soaSig.getTypeCovered());
                assertEquals(13, soaSig.getAlgorithm());
                assertEquals(2, soaSig.getLabels());
                assertEquals(3600, soaSig.getOriginalTtl());
                assertEquals(59077, soaSig.getKeyTag());

                assertEquals(RRType.NS, records.get(2).getType());
                assertArrayEquals(
                        new String[] {"example", "com"}, records.get(2).getName());

                assertEquals(RRType.NSEC, records.get(4).getType());
                RDataNSEC apexNsec = (RDataNSEC) records.get(4).getRdata();
                assertArrayEquals(new String[] {"blog", "example", "com"}, apexNsec.getNextDomainName());
                assertTrue(apexNsec.getTypeBitMaps().length > 0);

                assertEquals(RRType.DNSKEY, records.get(6).getType());
                RDataDNSKEY zsk = (RDataDNSKEY) records.get(6).getRdata();
                assertEquals(256, zsk.getFlags());
                assertEquals(3, zsk.getProtocol());
                assertEquals(13, zsk.getAlgorithm());
                assertTrue(zsk.getPublickey().length > 0);

                assertEquals(RRType.DNSKEY, records.get(7).getType());
                RDataDNSKEY ksk = (RDataDNSKEY) records.get(7).getRdata();
                assertEquals(257, ksk.getFlags());
                assertEquals(3, ksk.getProtocol());
                assertEquals(13, ksk.getAlgorithm());
                assertTrue(ksk.getPublickey().length > 0);

                assertEquals(RRType.CNAME, records.get(10).getType());
                assertArrayEquals(
                        new String[] {"blog", "example", "com"}, records.get(10).getName());
                RDataCNAME blogCname = (RDataCNAME) records.get(10).getRdata();
                assertArrayEquals(new String[] {"www", "example", "com"}, blogCname.getCname());

                assertEquals(RRType.A, records.get(14).getType());
                assertArrayEquals(
                        new String[] {"ns1", "example", "com"}, records.get(14).getName());
                RDataA ns1A = (RDataA) records.get(14).getRdata();
                assertArrayEquals(InetAddress.getByName("192.0.2.10").getAddress(), ns1A.getAddress());

                assertEquals(RRType.A, records.get(18).getType());
                assertArrayEquals(
                        new String[] {"www", "example", "com"}, records.get(18).getName());
                RDataA wwwA = (RDataA) records.get(18).getRdata();
                assertArrayEquals(InetAddress.getByName("192.0.2.20").getAddress(), wwwA.getAddress());

                assertEquals(RRType.NSEC, records.get(20).getType());
                RDataNSEC wwwNsec = (RDataNSEC) records.get(20).getRdata();
                assertArrayEquals(new String[] {"example", "com"}, wwwNsec.getNextDomainName());

            } catch (IOException e) {
                System.err.println("ERROR: File not found");
                fail();
            }
        }
    }
}
