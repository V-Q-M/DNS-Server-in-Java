package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DNSSerializerTest {
    @Test
    void RDataTest(){
        RDataA rdataA = new RDataA();
        byte[] addressA = new byte[]{ (byte)134, (byte)2, (byte)5, (byte)1 };
        rdataA.setAddress(addressA);
        assertArrayEquals(addressA, rdataA.toBytes());

        RDataAAAA rdataAAAA = new RDataAAAA();
        byte[] addressAAAA = new byte[]{
                (byte)0x20, (byte)0x01, (byte)0x07, (byte)0xc0,  // 2001:07c0
                (byte)0x30, (byte)0x0c, (byte)0x30, (byte)0xf0,  // 300c:30f0
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,  // 0000:0000 (::)
                (byte)0x86, (byte)0x02, (byte)0x05, (byte)0x01   // 8602:0501
        }; // 2001:7c0:300c:30f0::8602:501
        rdataAAAA.setAddress(addressAAAA);
        assertArrayEquals(addressAAAA, rdataAAAA.toBytes());

        RDataCNAME rdataCname = new RDataCNAME();
        rdataCname.setCname(new String[] {"uni-tuebingen", "de"});
        assertArrayEquals(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"), rdataCname.toBytes());

        RDataMX rdataMx = new RDataMX();
        rdataMx.setExchange(new String[] {"uni-tuebingen", "de"});
        rdataMx.setPreference(2);
        assertArrayEquals(HexFormat.of().parseHex("00020d756e692d74756562696e67656e02646500"), rdataMx.toBytes());

        RDataNS rdataNs = new RDataNS();
        rdataNs.setNsdname(new String[] {"uni-tuebingen", "de"});
        assertArrayEquals(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"), rdataNs.toBytes());

        RDataPTR rdataPtr = new RDataPTR();
        rdataPtr.setPtrdname(new String[] {"uni-tuebingen", "de"});
        assertArrayEquals(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"), rdataPtr.toBytes());

        RDataSOA rDataSoa = new RDataSOA();
        rDataSoa.setMname(new String[] {"uni-tuebingen", "de"});
        rDataSoa.setRname(new String[] {"uni-tuebingen", "de"});
        rDataSoa.setSerial(2);
        rDataSoa.setRefresh(2);
        rDataSoa.setRetry(2);
        rDataSoa.setExpire(2);
        rDataSoa.setMinimum(2);
        assertArrayEquals(HexFormat.of().parseHex(
                "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (Mname)
                + "0d756e692d74756562696e67656e02646500" // uni-tuebingen.de (Rname)
                + "00000002" // 2 (serial)
                + "00000002" // 2 (refresh)
                + "00000002" // 2 (retry)
                + "00000002" // 2 (expire)
                + "00000002" // 2 (minimum)
        ), rDataSoa.toBytes());

        RDataTXT rDataTxt = new RDataTXT();
        rDataTxt.setTxtdata("uni-tuebingen.de");
        assertArrayEquals(HexFormat.of().parseHex("10756e692d74756562696e67656e2e6465"), rDataTxt.toBytes());

        RDataTXT rDataTxtMultiLabel = new RDataTXT();
        rDataTxtMultiLabel.setTxtdata("hello", "world", "!");
        assertArrayEquals(HexFormat.of().parseHex("0568656c6c6F05776F726C640121"), rDataTxtMultiLabel.toBytes());
    }
    @Test
    void RRTest(){
        RDataSOA rData = new RDataSOA();
        rData.setMname(new String[] {"uni-tuebingen", "de"});
        rData.setRname(new String[] {"uni-tuebingen", "de"});
        rData.setSerial(2);
        rData.setRefresh(2);
        rData.setRetry(2);
        rData.setExpire(2);
        rData.setMinimum(2);

        ResourceRecord resourceRecord = new ResourceRecord();
        resourceRecord.setName(new String[] {"uni-tuebingen", "de"});
        //resourceRecord.setType(RRType.SOA);
        resourceRecord.setRrclass(RRClass.IN);
        resourceRecord.setTtl(2);
        //resourceRecord.setRdlength(rData.toBytes().length);
        resourceRecord.setRdata(rData);

        assertArrayEquals(HexFormat.of().parseHex(
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
        ), resourceRecord.toBytes());
    }
}
