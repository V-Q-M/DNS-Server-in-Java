package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.Test;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DNSParserTest {
    @Test
    void subBitTest(){
        assertEquals(0b110, DNSParser.subBits((byte) 0b10110110, 2, 5));
        assertEquals(0b10110, DNSParser.subBits((byte) 0b10110110, 0, 5));
        assertEquals(0b110, DNSParser.subBits((byte) 0b10110110, 5, 8));
        assertEquals(0b00000, DNSParser.subBits((byte) 0b00000000, 2, 7));
    }
    @Test
    void extractBitTest(){
        assertEquals(true, DNSParser.extractBit((byte) 0b10110110, 2));
        assertEquals(false, DNSParser.extractBit((byte) 0b10110110, 1));
        assertEquals(true, DNSParser.extractBit((byte) 0b10110110, 5));
        assertEquals(false, DNSParser.extractBit((byte) 0b10110110, 7));

    }
    @Test
    void as16BitIntTest(){
        assertEquals(5, DNSParser.as16BitInt((byte) 0x00, (byte) 0x05));
        assertEquals(16197, DNSParser.as16BitInt((byte) 0x3F, (byte) 0x45));
        assertEquals(65535, DNSParser.as16BitInt((byte) 0xFF, (byte) 0xFF));
    }
    @Test
    void as32BitIntTest(){
        assertEquals(328960, DNSParser.as32BitInt((byte) 0x00, (byte) 0x05, (byte) 0x05, (byte) 0x00));
        assertEquals(4294967295L, DNSParser.as32BitInt((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF));
        assertEquals(4278519043L, DNSParser.as32BitInt((byte) 0xFF, (byte) 0x05, (byte) 0x05, (byte) 0x03));
    }
    @Test
    void parseNameTest(){
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, DNSParser.parseName(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"), 0));
    }
    @Test
    void RDataTest(){
        RDataA rdataA = RDataA.fromBytes(new byte[]{ (byte)134, (byte)2, (byte)5, (byte)1 },0);
        assertArrayEquals(new byte[]{ (byte)134, (byte)2, (byte)5, (byte)1 }, rdataA.getAddress());

        byte[] addressAAAA = new byte[]{
                (byte)0x20, (byte)0x01, (byte)0x07, (byte)0xc0,  // 2001:07c0
                (byte)0x30, (byte)0x0c, (byte)0x30, (byte)0xf0,  // 300c:30f0
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,  // 0000:0000 no compression possible
                (byte)0x86, (byte)0x02, (byte)0x05, (byte)0x01   // 8602:0501
        }; // 2001:07c0:300c:30f0:0000:0000:8602:0501
        RDataAAAA rdataAAAA = RDataAAAA.fromBytes(addressAAAA,0);
        assertArrayEquals(addressAAAA, rdataAAAA.getAddress());
        RDataCNAME rDataCNAME = RDataCNAME.fromBytes(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"),0);
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, rDataCNAME.getCname());

        RDataMX rdataMx = RDataMX.fromBytes(HexFormat.of().parseHex("00020d756e692d74756562696e67656e02646500"),0);
        rdataMx.setExchange(new String[] {"uni-tuebingen", "de"});
        rdataMx.setPreference(2);
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, rdataMx.getExchange());
        assertEquals(2, rdataMx.getPreference());

        RDataNS rdataNs = RDataNS.fromBytes(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"),0);
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, rdataNs.getNsdname());

        RDataPTR rdataPtr = RDataPTR.fromBytes(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"),0);
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, rdataPtr.getPtrdname());

        RDataSOA rDataSOA =  RDataSOA.fromBytes(HexFormat.of().parseHex(
                "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (Mname)
                + "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (Rname)
                + "00000002" // 2 (serial)
                + "00000002" // 2 (refresh)
                + "00000002" // 2 (retry)
                + "00000002" // 2 (expire)
                + "00000002" // 2 (minimum)
        ),0);
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, rDataSOA.getMname());
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, rDataSOA.getRname());
        assertEquals(2, rDataSOA.getSerial());
        assertEquals(2, rDataSOA.getRefresh());
        assertEquals(2, rDataSOA.getRetry());
        assertEquals(2, rDataSOA.getExpire());
        assertEquals(2, rDataSOA.getMinimum());

        RDataTXT rDataTxt = RDataTXT.fromBytes(HexFormat.of().parseHex("10756e692d74756562696e67656e2e6465"),0);
        assertEquals("uni-tuebingen.de", rDataTxt.getTxtdata());
    }
    @Test
    void fullPacketTest(){
        final DNSPacket testQueryA = new DNSPacket();
        testQueryA.setId(new byte[]{(byte) 0b11111111, (byte) 0b10100110});
        testQueryA.setQr(false);
        testQueryA.setOpcode(OpCode.QUERY);
        testQueryA.setAa(false);
        testQueryA.setTc(false);
        testQueryA.setRd(false);
        testQueryA.setRa(false);
        testQueryA.setRcode(RCode.NOERROR);
        testQueryA.setQdcount(1);

        QuestionSection questionSectionA = new QuestionSection();
        questionSectionA.setName(new String[]{"test", "kn", "uni-tuebingen", "de"});
        questionSectionA.setQtype(RRType.A);
        questionSectionA.setQclass(RRClass.IN);

        testQueryA.setQuestionSections(new QuestionSection[] {questionSectionA});
        DNSPacket mockResponse = MockResponses.respond(testQueryA);
        byte[] mockResponseBytes = mockResponse.toBytes();
        assertArrayEquals(mockResponseBytes, DNSPacket.fromBytes(mockResponseBytes).toBytes());

        mockResponse.setNscount(2);
        mockResponse.setAuthoritySections(new ResourceRecord[]{mockResponse.getAnswerSection(0), mockResponse.getAnswerSection(0)});
        mockResponse.setArcount(2);
        mockResponse.setAdditionalSections(mockResponse.getAuthoritySections());
        mockResponseBytes = mockResponse.toBytes();
        assertArrayEquals(mockResponseBytes, DNSPacket.fromBytes(mockResponseBytes).toBytes());
    }
    @Test
    void RRTest(){
        ResourceRecord resourceRecord = ResourceRecord.fromBytes(HexFormat.of().parseHex(
                "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (name)
                + "0006" // SOA (type)
                + "0001" // IN (class)
                + "00000002" // 2s (ttl)
                + "0038" // 56 (rdlenght)
                + "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (Mname)
                + "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (Rname)
                + "00000002" // 2 (serial)
                + "00000002" // 2 (refresh)
                + "00000002" // 2 (retry)
                + "00000002" // 2 (expire)
                + "00000002" // 2 (minimum)
        ), 0);
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, resourceRecord.getName());
        assertEquals(RRType.SOA, resourceRecord.getType());
        assertEquals(RRClass.IN, resourceRecord.getRrclass());
        assertEquals(2, resourceRecord.getTtl());
        assertEquals(56, resourceRecord.getRdlength());
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, ((RDataSOA) resourceRecord.getRdata()).getMname());
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, ((RDataSOA) resourceRecord.getRdata()).getRname());
        assertEquals(2, ((RDataSOA) resourceRecord.getRdata()).getSerial());
        assertEquals(2, ((RDataSOA) resourceRecord.getRdata()).getRefresh());
        assertEquals(2, ((RDataSOA) resourceRecord.getRdata()).getRetry());
        assertEquals(2, ((RDataSOA) resourceRecord.getRdata()).getExpire());
        assertEquals(2, ((RDataSOA) resourceRecord.getRdata()).getMinimum());
    }

    

    

    @Test

    void decompressionWholeMessageTest() {

    
        //tests:
        // decompression for all Recource Record types having RData that can be compressed (fromBytes function)
        // decompression in ResourceRecord name of A record
        // decompression of message with multiple RRs
        // pointers to full domain names XXXXXXXXXXXXX
        // pointers to labels
        // //+pointer as domain name XXXXXXXX
        // pointers to question section XXXXXXXXXXX
        // pointers to answer section
        // tests that more than 1 call to fromBytes and toBytes on one packet doesn't change anything

        //notes
        // on modifying the dns packet: 
        //definitely adjust header fields (qdcount, ancount,...) in constructed RR and headerBytes
        //depending on changes: adjust pointers in (all) RRs, QuestionSections
        // in general: the an-, ar-, adsections are not necessarily connected to the question and the types in those sections don't necessarily make sense
    
        QuestionSection question = new QuestionSection();
        question.setName(new String[]{"kn-mattermost","cs","uni-tuebingen", "de"});
        question.setQtype(RRType.A);
        question.setQclass(RRClass.IN);
        String questionBytes = "0d6b6e2d6d61747465726d6f73740263730d756e692d74756562696e67656e0264650000010001";

        //cname RR:
        RDataCNAME cnameData = new RDataCNAME();
        cnameData.setCname(new String[]{"atlas","cs", "uni-tuebingen", "de"});

        ResourceRecord cname = createRr(
        new String[]{"kn-mattermost", "cs", "uni-tuebingen", "de"}, 
        RRType.CNAME, 
        86400,
        cnameData,
        27);

        String cnameBytes = "c00c000500010001518000080561746c6173c01a"; //captured bytes with pointers


        // A RR:
        RDataA cnameDataARR = new RDataA();
        cnameDataARR.setAddress(new byte[]{(byte) 134, (byte) 2, (byte) 10, (byte) 224});

        ResourceRecord a = createRr(new String[]{"atlas","cs", "uni-tuebingen", "de"},
        RRType.A,
        1200,
        cnameDataARR,
        4);

        String aBytes = "c03f00010001000004b0000486020ae0" ; //captured bytes


        //soa RR:
        RDataSOA soaData = new RDataSOA();
        soaData.setMname(new String[] {"uni-tuebingen", "de"});
        soaData.setRname(new String[] {"uni-tuebingen", "de"});
        soaData.setSerial(2);
        soaData.setRefresh(2);
        soaData.setRetry(2);
        soaData.setExpire(2);
        soaData.setMinimum(2);
        ResourceRecord soa = createRr(new String[]{"uni-tuebingen", "de"},
        RRType.SOA,
        2,
        soaData,
        soaData.toBytes().length);
        //hex version of soa record
        String soaBytes = "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (name)
            + "0006" // SOA (type)
            + "0001" // IN (class)
            + "00000002" // 2s (ttl)
            + "0026" // 38 (rdlength) 
            + "c01d" // uni-tuebingen.de (Mname), completely compressed
            + "0d756e692d74756562696e67656ec02b" // uni-tuebingen.de (Rname), only ".de" compressed
            + "00000002" // 2 (serial)
            + "00000002" // 2 (refresh)
            + "00000002" // 2 (retry)
            + "00000002" // 2 (expire)
            + "00000002"; // 2 (minimum)


        // ptr RR
        RDataPTR ptrData = new RDataPTR();
        ptrData.setPtrdname(new String[]{"atlas", "cs", "uni-tuebingen", "de"});

        ResourceRecord ptr = createRr(
        new String[]{"224", "10", "2", "134", "in-addr", "arpa"},
        RRType.PTR,
        21600,
        ptrData,
        ptrData.toBytes().length);

        String ptrBytes = "0332323402313001320331333407696e2d61646472046172706100000c000100005460001bc03f"; //pointer to atlas.... in cname at the end


        //NS RR
        RDataNS nsData = new RDataNS();
        nsData.setNsdname(new String[]{"dns4", "uni-tuebingen", "de"});
        ResourceRecord ns = createRr(
        new String[]{"uni-tuebingen", "de"},
        RRType.NS,
        3050,
        nsData,
        nsData.toBytes().length);
        String nsBytes = "c01d0002000100000bea000704646e7334c01d";

        RDataMX mxData = new RDataMX();
        mxData.setPreference(500);
        mxData.setExchange(new String[]{"a1541", "mx", "srv", "dfn", "de"});
        ResourceRecord mx = createRr(
        new String[]{"uni-tuebingen", "de"},
        RRType.MX,
        9057,
        mxData,
        mxData.toBytes().length);
        String mxBytes = "c01d000f000100002361001501f4056131353431026d78037372760364666ec02b";


        // Build packet
        DNSPacket packet = new DNSPacket();

        packet.setId(new byte[]{0x74, 0x06});
        packet.setQr(true);
        packet.setOpcode(OpCode.QUERY);
        packet.setAa(true);
        packet.setTc(false);
        packet.setRd(true);
        packet.setRa(true);
        packet.setRcode(RCode.NOERROR);
        packet.setQdcount(1);
        packet.setAncount(3);
        packet.setNscount(2);
        packet.setArcount(2);
        String headerBytes = "740685800001000300020002";
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[]{cname, a, mx});

        packet.setAuthoritySections(new ResourceRecord[]{soa, ns});
        packet.setAdditionalSections(new ResourceRecord[]{cname, ptr});

        byte[] bytes = packet.toBytes();
        //System.out.println(DNSPacket.fromBytes(bytes));


        assertArrayEquals(bytes, DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes)).toBytes());
        //test that also multiple parsings toBytes/fromBytes change nothing
        assertArrayEquals(DNSPacket.fromBytes(DNSPacket.fromBytes(bytes).toBytes()).toBytes(), bytes);
        //System.out.println(DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes)).toString());
        
        assertEquals(packet.toString(), DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes)).toString());
    }


    @Test
    void decompressionMultipleQuestionsPointerOnPointer() {
        QuestionSection question = new QuestionSection();
        question.setName(new String[]{"kn-mattermost","cs","uni-tuebingen", "de"});
        question.setQtype(RRType.A);
        question.setQclass(RRClass.IN);
        String questionBytes1 = "0d6b6e2d6d61747465726d6f73740263730d756e692d74756562696e67656e02646500" + //kn-mattermost... qname
                                "00010001";   // qtype, qclass
                                // this Byte string was aptured via wireshark
        String questionBytes2 = "c00c00010001"; //points to domain name in questionBytes1, manually created byte string
        String questionBytes3 = "c03300010001"; //Points to pointer in questoinBytes2 (pointer pointing to another pointer pointing to the domain name)
                                                // manually created byte string
        DNSPacket packet = new DNSPacket();

        packet.setId(new byte[]{0x74, 0x06});
        packet.setQr(true);
        packet.setOpcode(OpCode.QUERY);
        packet.setAa(true);
        packet.setTc(false);
        packet.setRd(true);
        packet.setRa(true);
        packet.setRcode(RCode.NOERROR);
        packet.setQdcount(3);
        packet.setAncount(0);
        packet.setNscount(0);
        packet.setArcount(0);
        String headerBytes = "74068580" + 
                            "0003000000000000"; //qdcount, ancont, nscount, arcount: each 2 bytes
        packet.setQuestionSections(new QuestionSection[]{question, question, question});
        packet.setAnswerSections(new ResourceRecord[0]); // set on empty array because null pointer exception otherwise in toBytes
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[0]);

        byte[] bytes = packet.toBytes();
        //asserts that the byte string gets parsed by fromBytes and then converted to toBytes as expected:
        // 3 times same question section
        // pointer on pointer resolution (pointer to the full domain name)
        assertArrayEquals(bytes, DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes1 + questionBytes2 + questionBytes3)).toBytes());
       ////////////////////////string convert nur from bytes
    }



    


    // captured strings via wireshark, sometimes pointers changed
    private static final String questionBytes = "0d6b6e2d6d61747465726d6f73740263730d756e692d74756562696e67656e0264650000010001";
    private static final String cnameBytes = "c00c000500010001518000080561746c6173c01a"; //c01a: points to .cs.uni-... in questionBytes if headerBytes,questoinBytes,cnameBytes
    private static final String aBytes = "c03f00010001000004b0000486020ae0"; //if headerBytes,questionBytes,cnameBytes,aBytes: c03f points to atlas... in cnameBytes
    private static final String soaBytes = "0d756e692d74756562696e67656e0264650000060001000000020018" +
                                            "c01d" + //mname pointer to question section uni-tuebingen.de
                                             "c04f"+ //"0d756e692d74756562696e67656ec02b" + //rname
                                            "0000000200000002000000020000000200000002";
    private static final String ptrBytes = "0332323402313001320331333407696e2d61646472046172706100000c0001000054600002c03f";
    private static final String nsBytes = "c01d0002000100000bea000704646e7334c01d";
    private static final String mxBytes = "c01d000f000100002361001501f4056131353431026d78037372760364666ec02b";

    /**
     * interface used for assertions
     */
    @FunctionalInterface
    interface DecompressionAssertion {
        void run(DNSPacket packet, byte[] hexBytes);
    }
    
    // test execution, isoltae possible errors / failures
    @ParameterizedTest(name = "{0}")
    @MethodSource("decompressionTestCases")
    void testDnsDecompression(String testName, DNSPacket packet, String completeBytes, DecompressionAssertion assertion) {
        byte[] hexBytes = HexFormat.of().parseHex(completeBytes);
        assertion.run(packet, hexBytes);
    }

    private static Stream<Arguments> decompressionTestCases() {
        return Stream.of(

            //Test1: whole message, see if all parts of parser integrate well

            /**Arguments.of(
                "Decompression whole message test",
                createWholeMessagePacket(),
                "740685800001000300020002" + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes,
                (DecompressionAssertion) (packet, hexBytes) -> {
                    // tests:
                    // decompression for all Resource Record types having RData that can be compressed (fromBytes function)
                    // decompression in ResourceRecord name of A record XXXXXXXXXXXXXXX
                    // decompression of message with multiple RRs XXXXXXXXXXX
                    // pointers to full domain names XXXXXXXXXXXXX
                    // pointers to labels XXXXXXXXXXXXXXXXXXXXXX
                    // //+pointer as domain name XXXXXXXX
                    // pointers to question section XXXXXXXXXXX
                    // pointers to answer section XXXXXXXXXXXXXXXXXXX
                    // tests that more than 1 call to fromBytes and toBytes on one packet doesn't change anything

                    // notes
                    // on modifying the dns packet: 
                    // definitely adjust header fields (qdcount, ancount,...) in constructed RR and headerBytes
                    // depending on changes: adjust pointers in (all) RRs, QuestionSections
                    // in general: the an-, ar-, adsections are not necessarily connected to the question and the types in those sections don't necessarily make sense

                    byte[] generatedBytes = packet.toBytes();

                    // Run structural assertions
                    assertArrayEquals(generatedBytes, DNSPacket.fromBytes(hexBytes).toBytes());
                    assertArrayEquals(DNSPacket.fromBytes(DNSPacket.fromBytes(generatedBytes).toBytes()).toBytes(), generatedBytes);
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                }
            ),*/ //adjust c04f pointer!!!!!

            //Test2: test resolution of pointer pointing on pointer, only questionSections

            Arguments.of(
                "Decompression multiple questions, pointer on pointer test",
                createPointerOnPointerPacket(),
                "740685800003000000000000" + questionBytes + "c00c00010001" + "c03300010001", //c00c: pointer on domain name in questionBytes, c033: pointer on pointer in questionBytes2
                (DecompressionAssertion) (packet, hexBytes) -> {
                    // asserts that the byte string gets parsed by fromBytes and then converted to toBytes as expected:
                    // 3 times same question section
                    // pointer on pointer resolution (pointer to the full domain name)
                    assertArrayEquals(packet.toBytes(), DNSPacket.fromBytes(hexBytes).toBytes());
                    // asserts that message including question sections is parsed correctly by fromBytes (due to different hashes it's not possible to compare the actual packet and the paresed bytes)
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                }
            ),

            //Test 3: tests if domain name consisting of labels+pointer gets parsed correctly (c01a pointer in cname), 
            // A RR name, cname decompression
            //decompression with a pointer to answer section (so not only pointer to question section get tested)

            Arguments.of(
                "Decompression pointer from additional section to answer section(CNAME, A), pointer to part of domain name test",
                createPartialNamePointerPacket(),
                "740685800001000100000001" + questionBytes + cnameBytes + aBytes,
                (DecompressionAssertion) (packet, hexBytes) -> {
                    // asserts that message including cname and A RR are parsed correctly by fromBytes (due to different hashes it's not possible to compare the actual packet and the paresed bytes)
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                    // asserts that the byte string gets parsed by fromBytes and then converted to toBytes as expected:
                    assertArrayEquals(packet.toBytes(), DNSPacket.fromBytes(hexBytes).toBytes());
                }
            ),
        
            //Test 4: test if names in authority section get parsed correctly
            // soa and ns RR
            // pointer as rname points into OWN RR to mname
            //pointer to section other than question section (authority section)
            Arguments.of(
                "Decompression Authority Section Test (SOA and NS)",
                createAuthoritySectionPacket(),
                "740685800001000000020000" + questionBytes + soaBytes + nsBytes, // qd=1, an=0, ns=2, ar=0
                (DecompressionAssertion) (packet, hexBytes) -> {
                    byte[] generatedBytes = packet.toBytes();
                    // asserts that the authority records are parsed correctly by fromBytes and then converted to toBytes as expected:
                    assertArrayEquals(generatedBytes, DNSPacket.fromBytes(hexBytes).toBytes());
                    // asserts that message including soa and ns RR is parsed correctly by fromBytes (due to different hashes it's not possible to compare the actual packet and the paresed bytes)
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                }
            ),

            // Test 5: test if ptr RR in additional section gets decompressed and parsed correctly
            Arguments.of(
                "Decompression PTR",
                createPtrSectionPacket(),
                "740685800001000100000001" + questionBytes + cnameBytes + ptrBytes, //qd=1,an=1(cname),ar=0,ad=1(ptr)
                (DecompressionAssertion) (packet, hexBytes) -> {
                    byte[] generatedBytes = packet.toBytes();
                    // asserts that message including ptr RR is parsed correctly by fromBytes (due to different hashes it's not possible to compare the actual packet and the paresed bytes)
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                    // asserts that message including ptr RR is parsed correctly by fromBytes and then converted to toBytes as expected:
                     assertArrayEquals(generatedBytes, DNSPacket.fromBytes(hexBytes).toBytes());
                }
            ),

            // Test 6: test if mx RR in answer section gets decompressed and parsed correctly
            Arguments.of(
                "Decompression MX Record Target Suffix Test",
                createMxSectionPacket(),
                // Header (qd=1, an=1, ns=0, ar=0) + Question + MX Record
                "740685800001000100000000" + questionBytes + mxBytes,
                (DecompressionAssertion) (packet, hexBytes) -> {
                    byte[] generatedBytes = packet.toBytes();
                    // asserts that message including mx RR is parsed correctly by fromBytes and then converted to toBytes as expected:
                    assertArrayEquals(generatedBytes, DNSPacket.fromBytes(hexBytes).toBytes());
                    // asserts that message including mx RR is parsed correctly by fromBytes (due to different hashes it's not possible to compare the actual packet and the paresed bytes)
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                }
            )
        );
    }

    //functions creating the packets used in tests for better readability

    private static DNSPacket createWholeMessagePacket() {
        QuestionSection question = createQuestion();
        ResourceRecord cname = createCnameRecord();
        ResourceRecord aRecord = createARecord();
        ResourceRecord soa = createSoaRecord();
        ResourceRecord ns = createNsRecord();
        ResourceRecord mx = createMxRecord();
        ResourceRecord ptr = createPtrRecord();

        DNSPacket packet = createPacket(1, 3, 2, 2); // qd=1, an=3, ns=2, ar=2
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[]{cname, aRecord, mx});
        packet.setAuthoritySections(new ResourceRecord[]{soa, ns});
        packet.setAdditionalSections(new ResourceRecord[]{cname, ptr});
        return packet;
    }

    private static DNSPacket createPointerOnPointerPacket() {
        QuestionSection question = createQuestion();
        DNSPacket packet = createPacket(3, 0, 0, 0); // qd=3, an=0, ns=0, ar=0
        packet.setQuestionSections(new QuestionSection[]{question, question, question});
        packet.setAnswerSections(new ResourceRecord[0]); // set on empty array because null pointer exception otherwise in toBytes
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[0]);
        return packet;
    }

    private static DNSPacket createPartialNamePointerPacket() {
        QuestionSection question = createQuestion();
        ResourceRecord cname = createCnameRecord();
        ResourceRecord aRecord = createARecord();

        DNSPacket packet = createPacket(1, 1, 0, 1); // qd=1, an=2, ns=0, ar=0
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[]{cname});
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[]{aRecord});
        return packet;
    }

    private static DNSPacket createAuthoritySectionPacket() {
        QuestionSection question = createQuestion();
        ResourceRecord soa = createSoaRecord();
        ResourceRecord ns = createNsRecord();

        DNSPacket packet = createPacket(1, 0, 2, 0); // qd=1, an=0, ns=2, ar=0
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[0]);
        packet.setAuthoritySections(new ResourceRecord[]{soa, ns});
        packet.setAdditionalSections(new ResourceRecord[0]);
        return packet;
    }

    private static DNSPacket createPtrSectionPacket() {
        QuestionSection question = createQuestion();
        ResourceRecord ptr = createPtrRecord();
        ResourceRecord cname = createCnameRecord();

        DNSPacket packet = createPacket(1, 1, 0, 1); // qd=1, an=1, ns=0, ar=1
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[]{cname});
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[]{ptr});
        return packet;
    }

    private static DNSPacket createMxSectionPacket() {
    QuestionSection question = createQuestion();
    ResourceRecord mx = createMxRecord();

    DNSPacket packet = createPacket(1, 1, 0, 0); // qd=1, an=1, ns=0, ar=0
    packet.setQuestionSections(new QuestionSection[]{question});
    packet.setAnswerSections(new ResourceRecord[]{mx});
    packet.setAuthoritySections(new ResourceRecord[0]);
    packet.setAdditionalSections(new ResourceRecord[0]);
    return packet;
    }

        //helper mehtods to avoid repetitive creation of same RRs and of packet creation

    private static DNSPacket createPacket(int qd, int an, int ns, int ar) {
        DNSPacket packet = new DNSPacket();
        packet.setId(new byte[]{0x74, 0x06});
        packet.setQr(true);
        packet.setOpcode(OpCode.QUERY);
        packet.setAa(true);
        packet.setTc(false);
        packet.setRd(true);
        packet.setRa(true);
        packet.setRcode(RCode.NOERROR);
        packet.setQdcount(qd);
        packet.setAncount(an);
        packet.setNscount(ns);
        packet.setArcount(ar);
        return packet;
    }

    private static QuestionSection createQuestion() {
        QuestionSection question = new QuestionSection();
        question.setName(new String[]{"kn-mattermost", "cs", "uni-tuebingen", "de"});
        question.setQtype(RRType.A);
        question.setQclass(RRClass.IN);
        return question;
    }

    private static ResourceRecord createCnameRecord() {
        RDataCNAME cnameData = new RDataCNAME();
        cnameData.setCname(new String[]{"atlas", "cs", "uni-tuebingen", "de"});
        return createRr(new String[]{"kn-mattermost", "cs", "uni-tuebingen", "de"}, RRType.CNAME, 86400, cnameData, 27);
    }

    private static ResourceRecord createARecord() {
        RDataA aData = new RDataA();
        aData.setAddress(new byte[]{(byte) 134, (byte) 2, (byte) 10, (byte) 224});
        return createRr(new String[]{"atlas", "cs", "uni-tuebingen", "de"}, RRType.A, 1200, aData, 4);
    }

    private static ResourceRecord createSoaRecord() {
        RDataSOA soaData = new RDataSOA();
        soaData.setMname(new String[]{"uni-tuebingen", "de"});
        soaData.setRname(new String[]{"uni-tuebingen", "de"});
        soaData.setSerial(2); soaData.setRefresh(2); soaData.setRetry(2); soaData.setExpire(2); soaData.setMinimum(2);
        return createRr(new String[]{"uni-tuebingen", "de"}, RRType.SOA, 2, soaData, soaData.toBytes().length);
    }

    private static ResourceRecord createPtrRecord() {
        RDataPTR ptrData = new RDataPTR();
        ptrData.setPtrdname(new String[]{"atlas", "cs", "uni-tuebingen", "de"});
        return createRr(new String[]{"224", "10", "2", "134", "in-addr", "arpa"}, RRType.PTR, 21600, ptrData, ptrData.toBytes().length);
    }

    private static ResourceRecord createNsRecord() {
        RDataNS nsData = new RDataNS();
        nsData.setNsdname(new String[]{"dns4", "uni-tuebingen", "de"});
        return createRr(new String[]{"uni-tuebingen", "de"}, RRType.NS, 3050, nsData, nsData.toBytes().length);
    }

    private static ResourceRecord createMxRecord() {
        RDataMX mxData = new RDataMX();
        mxData.setPreference(500);
        mxData.setExchange(new String[]{"a1541", "mx", "srv", "dfn", "de"});
        return createRr(new String[]{"uni-tuebingen", "de"}, RRType.MX, 9057, mxData, mxData.toBytes().length);
    }

    /**
     * genrerates RR with values 
     * @param qname query domain name
     * @param qtype queried type of RR
     * @param ttl
     * @param rdata RData of specific RR type e.g. RDataMX
     * @param rdlength Rdlength of rdata
     * @return new ResourceRecord
     */
    public static ResourceRecord createRr(String[] qname, RRType qtype, int ttl, RData rdata, int rdlength) {
        ResourceRecord rr = new ResourceRecord();
        rr.setName(qname);
        rr.setType(qtype);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(ttl);
        rr.setRdata(rdata);
        rr.setRdlength(rdlength);
        return rr;
    
    }
}