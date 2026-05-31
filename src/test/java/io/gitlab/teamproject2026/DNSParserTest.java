package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.Test;
import java.util.HexFormat;

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

    void testDecompressionWholeMessage() {
        //tests:
        // decompression for all Recource Record types having RData that can be compressed (fromBytes function)
        // decompression in ResourceRecord name of A record
        // decompression of message with multiple RRs
        // pointers to full domain names
        // pointers to labels+pointer as domain name
        // pointers to question section
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

        ResourceRecord cname = new ResourceRecord();
        cname.setName(new String[]{"kn-mattermost","cs", "uni-tuebingen", "de"});
        cname.setType(RRType.CNAME);
        cname.setRrclass(RRClass.IN);
        cname.setTtl(86400);
        RDataCNAME cnameData = new RDataCNAME();
        cnameData.setCname(new String[]{"atlas","cs", "uni-tuebingen", "de"});
        cname.setRdata(cnameData);
        cname.setRdlength(27);
        String cnameBytes = "c00c000500010001518000080561746c6173c01a";

        ResourceRecord a = new ResourceRecord();
        a.setName(new String[]{"atlas","cs", "uni-tuebingen", "de"});
        a.setType(RRType.A);
        a.setRrclass(RRClass.IN);
        a.setTtl(1200);
        RDataA cnameDataARR = new RDataA();
        cnameDataARR.setAddress(new byte[]{(byte) 134, (byte) 2, (byte) 10, (byte) 224});
        a.setRdata(cnameDataARR);
        a.setRdlength(4);
        String aBytes = "c03f00010001000004b0000486020ae0" ;

        RDataSOA rData = new RDataSOA();
        rData.setMname(new String[] {"uni-tuebingen", "de"});
        rData.setRname(new String[] {"uni-tuebingen", "de"});
        rData.setSerial(2);
        rData.setRefresh(2);
        rData.setRetry(2);
        rData.setExpire(2);
        rData.setMinimum(2);
        ResourceRecord soa = new ResourceRecord();
        soa.setName(new String[] {"uni-tuebingen", "de"});
        soa.setType(RRType.SOA);
        soa.setRrclass(RRClass.IN);
        soa.setTtl(2);
        soa.setRdlength(rData.toBytes().length);
        soa.setRdata(rData);
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

        RDataPTR ptrData = new RDataPTR();
        ptrData.setPtrdname(new String[]{"atlas", "cs", "uni-tuebingen", "de"});
        ResourceRecord ptr = new ResourceRecord();
        ptr.setName(new String[]{"224", "10", "2", "134", "in-addr", "arpa"});
        ptr.setType(RRType.PTR);
        ptr.setRrclass(RRClass.IN);
        ptr.setTtl(21600);
        ptr.setRdata(ptrData);
        ptr.setRdlength(ptrData.toBytes().length);
        String ptrBytes = "0332323402313001320331333407696e2d61646472046172706100000c000100005460001bc03f"; //pointer to atlas.... in cname at the end

        RDataNS nsData = new RDataNS();
        nsData.setNsdname(new String[]{"dns4", "uni-tuebingen", "de"});
        ResourceRecord ns = new ResourceRecord();
        ns.setName(new String[]{"uni-tuebingen", "de"});
        ns.setType(RRType.NS);
        ns.setRrclass(RRClass.IN);
        ns.setTtl(3050);
        ns.setRdata(nsData);
        ns.setRdlength(nsData.toBytes().length);
        String nsBytes = "c01d0002000100000bea000704646e7334c01d";

        RDataMX mxData = new RDataMX();
        mxData.setPreference(500);
        mxData.setExchange(new String[]{"a1541", "mx", "srv", "dfn", "de"});
        ResourceRecord mx = new ResourceRecord();
        mx.setName(new String[]{"uni-tuebingen", "de"});
        mx.setType(RRType.MX);
        mx.setRrclass(RRClass.IN);
        mx.setTtl(9057);
        mx.setRdata(mxData);
        mx.setRdlength(mxData.toBytes().length);
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
        System.out.println(DNSPacket.fromBytes(bytes));
        
        assertArrayEquals(bytes, DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes)).toBytes());
        //test that also multiple parsings toBytes/fromBytes change nothing
        assertArrayEquals(DNSPacket.fromBytes(DNSPacket.fromBytes(bytes).toBytes()).toBytes(), bytes);
        System.out.println(DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes)).toString());
        
        assertEquals(packet.toString(), DNSPacket.fromBytes(HexFormat.of().parseHex(headerBytes + questionBytes + cnameBytes + aBytes + mxBytes + soaBytes + nsBytes + cnameBytes + ptrBytes)).toString());
        }

}
