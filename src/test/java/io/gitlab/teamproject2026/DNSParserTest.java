package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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

        RDataTXT rDataTxt = RDataTXT.fromBytes(HexFormat.of().parseHex("10756e692d74756562696e67656e2e6465"),0, 17);
        assertArrayEquals(new String[]{"uni-tuebingen.de"}, rDataTxt.getTxtdata());

        RDataTXT rDataTxtMultiLabel = RDataTXT.fromBytes(HexFormat.of().parseHex("0568656c6c6F05776F726C640121"),0, 14);
        assertArrayEquals(new String[]{"hello", "world", "!"}, rDataTxtMultiLabel.getTxtdata());
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



    //========================DECOMPRESSION TESTS====================================================================================


    //---------------------TEST WELL-FORMED MESSAGES--------------------------------------------------------------------------------
    // decompression for all Resource Record types having RData that can be compressed and A RRs (fromBytes function)

    // notes
    // on modifying the dns packet:
    // definitely adjust header fields (qdcount, ancount,...) in constructed RR and headerBytes
    // depending on changes: adjust pointers in (all) RRs, QuestionSections
    // in general: the an-, ar-, adsections are not necessarily connected to the question and the types in those sections don't necessarily make sense



    // captured strings via wireshark, sometimes pointers changed
    private static final String questionBytes = "0d6b6e2d6d61747465726d6f73740263730d756e692d74756562696e67656e0264650000010001";
    private static final String cnameBytes = "c00c000500010001518000080561746c6173c01a"; //c01a: points to .cs.uni-... in questionBytes if headerBytes,questoinBytes,cnameBytes
    private static final String aBytes = "c03f00010001000004b0000486020ae0"; //if headerBytes,questionBytes,cnameBytes,aBytes: c03f points to atlas... in cnameBytes
    private static final String soaBytes = "0d756e692d74756562696e67656e0264650000060001000000020018" +
                                            "c01d" + //mname pointer to question section uni-tuebingen.de
                                             "c04f"+ //"0d756e692d74756562696e67656ec02b" + //rname
                                            "0000000200000002000000020000000200000002";
    private static final String ptrBytes = "0332323402313001320331333407696e2d61646472046172706100000c0001000054600002c03f"; //c03f at the end is pointer: points to atlas... in cnameBytes
    private static final String nsBytes = "c01d0002000100000bea000704646e7334c01d"; //c01d: pointer to uni-tuebingen.de. in question section
    private static final String mxBytes = "c01d000f000100002361001501f4056131353431026d78037372760364666ec02b"; //c02b: pointer to ".de." in question section

    /**
     * interface used for assertions
     */
    @FunctionalInterface
    interface DecompressionAssertion {
        void run(DNSPacket packet, byte[] hexBytes);
    }

    // executes tests for the 6 SubTests
    @ParameterizedTest()
    @MethodSource("decompressionSubTests")
    void testDnsDecompression(String testName, DNSPacket packet, String completeBytes, DecompressionAssertion assertion) {
         try {
        byte[] hexBytes = HexFormat.of().parseHex(completeBytes);
        assertion.run(packet, hexBytes);
    } catch (AssertionError e) {
        throw new AssertionError("Failed test: " + testName, e);
    }
    }

    // standard assertions used by SubTests 2,3,4,5,6
    private static final DecompressionAssertion standardAssertions = (packet, hexBytes) -> {
        DNSPacket parsed = DNSPacket.fromBytes(hexBytes);
        // asserts that message including cname and A RR are parsed correctly by fromBytes
        // due to different hashes it's not possible to compare the actual packet and the parsed but instead stringCompare
        assertEquals(packet.toString(), parsed.toString());
        // asserts that the byte string gets parsed by fromBytes and then converted to toBytes as expected:
        assertArrayEquals(packet.toBytes(), parsed.toBytes());
    };

    private static Stream<Arguments> decompressionSubTests() {
        return Stream.of(

            //-------SubTest[1]: whole message, see if big message with multiple RRs works (combination of specific tests below)------------------------------------------

            Arguments.of(
                "Decompression whole message test",
                createWholeMessagePacket(),
                "740685800001000300020002" + //header bytes, slightly different in every TestCase (different qdcount, ancount,...)
                questionBytes + cnameBytes + aBytes + mxBytes +
                "0d756e692d74756562696e67656e0264650000060001000000020018" +
                                            "c01d" + //mname pointer to question section uni-tuebingen.de
                                             "c01d"+  //rname
                                            "0000000200000002000000020000000200000002" //soaBytes (4 lines), other rname pointer compared to Test4 necessary due to other RRs in between
                                             + nsBytes + cnameBytes + ptrBytes,
                (DecompressionAssertion) (packet, hexBytes) -> {
                    byte[] generatedBytes = packet.toBytes();
                    // asserts that message with multiple sections including most types of RRs is parsed correctly by fromBytes (due to different hashes it's not possible to compare the actual packet and the paresed bytes)
                    assertEquals(packet.toString(), DNSPacket.fromBytes(hexBytes).toString());
                    // asserts that the byte string gets parsed by fromBytes and then converted to toBytes as expected:
                    assertArrayEquals(generatedBytes, DNSPacket.fromBytes(hexBytes).toBytes());
                    //asserts that multiple calls to fromBytes and toBytes don't corrupt parsing:
                    assertArrayEquals(DNSPacket.fromBytes(DNSPacket.fromBytes(generatedBytes).toBytes()).toBytes(), generatedBytes);
                }
            ),

            //--------------SubTest[2]: test resolution of pointer pointing on pointer, only questionSections------------------------------------------

            Arguments.of(
                "Decompression multiple questions, pointer on pointer test",
                createPointerOnPointerPacket(),
                "740685800003000000000000" + questionBytes + "c00c00010001" + "c03300010001", //c00c: pointer on domain name in questionBytes, c033: pointer on pointer in question "c00c..."
                                                                            //3 times question section with same contents but different pointers
                standardAssertions
            ),

            //-------------------SubTest[3]: tests if domain name consisting of labels+pointer gets parsed correctly ----------------------------------------
            // A RR name, cname decompression ((c01a pointer in cname))
            //decompression with a pointer to answer section (so not only pointers to question section get tested)

            Arguments.of(
                "Decompression pointer from additional section to answer section(CNAME, A), pointer to part of domain name test",
                createPartialNamePointerPacket(),
                "740685800001000100000001" + questionBytes + cnameBytes + aBytes, // qd=1, an=1, ns=0, ar=1
                                                                                //partial pointer from cname to question section, pointer from a to cname
                standardAssertions
            ),

            //---------------------SubTest[4]: test if names in authority section get parsed correctly---------------------------------------
            // soa and ns RR
            //pointer to section other than question section (authority section): from rname to mname
            Arguments.of(
                "Decompression authority section test (SOA and NS)",
                createAuthoritySectionPacket(),
                "740685800001000000020000" + questionBytes + soaBytes + nsBytes, // qd=1, an=0, ns=2, ar=0
                                                                                // pointer as rname points into OWN RR to mname
                standardAssertions
            ),

            //----------------------SubTest[5]: test if ptr RR in additional section gets decompressed and parsed correctly------------------------------------------------------
            Arguments.of(
                "Decompression PTR",
                createPtrSectionPacket(),
                "740685800001000100000001" + questionBytes + cnameBytes + ptrBytes, //qd=1,an=1(cname),ar=0,ad=1(ptr)
                standardAssertions
            ),

            //---------------------------SubTest[6]: test if mx RR in answer section gets decompressed and parsed correctly----------------------------------------

            Arguments.of(
                "Decompression MX record test",
                createMxSectionPacket(),
                "740685800001000100000000" + questionBytes + mxBytes, //qd=1,an=1(mx),ar=0,ad=0
                standardAssertions
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
        //rr.setType(qtype);
        rr.setRrclass(RRClass.IN);
        rr.setTtl(ttl);
        rr.setRdata(rdata);
        //rr.setRdlength(rdlength);
        return rr;

    }




//----------------------------TEST MALFORMED BYTES (decompression functions)---------------------------------------------------------------------------

// ---------------------------------parseName exception tests---------------------------------------------------------------

    @Test
    void parseNameTruncatedData() {
        // 036162: expects 3 char label but only 2 bytes label
        byte[] truncatedBytes = HexFormat.of().parseHex("036162");
        //asserts right type of exception
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            DNSParser.parseName(truncatedBytes, 0);
        });
        //asserts right exception message -> exact exception
        assertEquals("false label length or false pointer", exception.getMessage());
    }

    @Test
    void parseNameCompressionLoop() {
        // c000 is pointer to index 0 so it points to itself (compression loop)
        byte[] loopBytes = HexFormat.of().parseHex("c000");
        //asserts right type of exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            DNSParser.parseName(loopBytes, 0);
        });
        //asserts right exception message -> exact exception
        assertEquals("Compression loop detected", exception.getMessage());
    }

    // ----------------------countNameLength, countNameLengthDecomp exception tests--------------------------------------

    @Test
    void countNameLengthTruncatedLabel() {
        // labellength bigger than actual label
        byte[] truncatedLabel = HexFormat.of().parseHex("05616263");
        //asserts right type of exception
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            DNSParser.countNameLength(truncatedLabel, 0);
        });
        //asserts right exception message -> exact exception
        assertEquals("false label length or false pointer", exception.getMessage());
    }

    @Test
    void countNameLengthDecompTruncatedLabel() {
        // labellength bigger than actual label
        byte[] truncatedLabel = HexFormat.of().parseHex("0A61");
        //asserts right type of exception
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            DNSParser.countNameLengthDecomp(truncatedLabel, 0);
        });
        //asserts right exception message -> exact exception
        assertEquals("false label length or false pointer", exception.getMessage());
    }



    //-----------getPointerTarget Exception Tests------------------------------------------------------------------

    @Test
    void getPointerTargetPointerTruncated() {
        // only first half of pointer offset
        byte[] pointer = new byte[]{(byte) 0xC0};
        //asserts right type of exception
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            DNSParser.getPointerTarget(pointer, 0);
        });
        //asserts right exception message -> exact exception
        assertEquals("Index 1 out of bounds for length 1", exception.getMessage()); //java bulit-in exception
    }

    @Test
    void getPointerTargetPointerTargetOutOfBounds() {
        // 0xff offset, array length is only 4
        byte[] pointerTargetOutOfBounds = HexFormat.of().parseHex("c0ff1234");
        //asserts right type of exception
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            DNSParser.getPointerTarget(pointerTargetOutOfBounds, 0);
        });
        //asserts right exception message -> exact exception
        assertEquals("pointer points out of bounds", exception.getMessage());
    }

    @Test
    void getPointerTargetStartIndexOutOfBounds() {
        // array length is 4, set startIndex for getPointerTarget to 4 (out of boundaries of array)
        byte[] bytes = HexFormat.of().parseHex("c0ff1234");
        //asserts right type of exception
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            DNSParser.getPointerTarget(bytes, 4);
        });
        //asserts right exception message -> exact exception
        assertEquals("startIndex for pointer out of bounds", exception.getMessage());
    }




}