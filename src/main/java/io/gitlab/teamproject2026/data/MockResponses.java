package io.gitlab.teamproject2026.data;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;


public class MockResponses {

    public static DNSPacket a(DNSPacket requestPacket) {
        RDataA rdata = new RDataA();
        rdata.setAddress(new byte[]{ (byte)134, (byte)2, (byte)5, (byte)1 });

        return buildResponse(requestPacket, RRType.A, rdata);
    }

    public static DNSPacket aaaa(DNSPacket requestPacket) {
        RDataAAAA rdata = new RDataAAAA();
        rdata.setAddress(new byte[]{
                (byte)0x20, (byte)0x01, (byte)0x07, (byte)0xc0,  // 2001:07c0
                (byte)0x30, (byte)0x0c, (byte)0x30, (byte)0xf0,  // 300c:30f0
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,  // 0000:0000 (::)
                (byte)0x86, (byte)0x02, (byte)0x05, (byte)0x01   // 8602:0501
        }); // 2001:7c0:300c:30f0::8602:501

        return buildResponse(requestPacket, RRType.AAAA, rdata);
    }

    public static DNSPacket cname(DNSPacket requestPacket) {
        RDataCNAME rdata = new RDataCNAME();
        rdata.setCname(new String[]{ "uni-tuebingen-2", "de" });

        return buildResponse(requestPacket, RRType.CNAME, rdata);
    }

    public static DNSPacket mx(DNSPacket requestPacket) {
        RDataMX rdata = new RDataMX();
        rdata.setPreference(10);
        rdata.setExchange(new String[]{ "mailserver", "uni-tuebingen", "de" });

        return buildResponse(requestPacket, RRType.MX, rdata);
    }

    public static DNSPacket ns(DNSPacket requestPacket) {
        RDataNS rdata = new RDataNS();
        rdata.setNsdname(new String[]{ "nameserver", "uni-tuebingen", "de" });

        return buildResponse(requestPacket, RRType.NS, rdata);
    }

    public static DNSPacket ptr(DNSPacket requestPacket) {
        RDataPTR rdata = new RDataPTR();
        rdata.setPtrdname(new String[]{ "uni-tuebingen", "de" });

        return buildResponse(requestPacket, RRType.PTR, rdata);
    }

    public static DNSPacket soa(DNSPacket requestPacket) {
        RDataSOA rdata = new RDataSOA();
        rdata.setMname(new String[]{ "nameserver", "uni-tuebingen", "de" });
        rdata.setRname(new String[]{ "admin", "uni-tuebingen", "de" });
        rdata.setSerial(2026010101);
        rdata.setRefresh(3600);
        rdata.setRetry(900);
        rdata.setExpire(604800);
        rdata.setMinimum(300);

        return buildResponse(requestPacket, RRType.SOA, rdata);
    }

    public static DNSPacket txt(DNSPacket requestPacket) {
        RDataTXT rdata = new RDataTXT();
        rdata.setTxtdata("hello world");

        return buildResponse(requestPacket, RRType.TXT, rdata);
    }


    public static DNSPacket respond(DNSPacket requestPacket) {
        RRType type = requestPacket.getQuestionSections()[0].getQtype();

        return switch (type) {
            case A     -> a(requestPacket);
            case AAAA  -> aaaa(requestPacket);
            case CNAME -> cname(requestPacket);
            case MX    -> mx(requestPacket);
            case NS    -> ns(requestPacket);
            case PTR   -> ptr(requestPacket);
            case SOA   -> soa(requestPacket);
            case TXT   -> txt(requestPacket);
        };
    }


    private static DNSPacket buildResponse(DNSPacket requestPacket, RRType type, RData rdata) {
        ResourceRecord answer = new ResourceRecord();
        answer.setName(requestPacket.getQuestionSections()[0].getName());
        answer.setType(type);
        answer.setRrclass(RRClass.IN);
        answer.setTtl(300);
        answer.setRdata(rdata);

        DNSPacket response = new DNSPacket();
        response.setId(requestPacket.getId());
        response.setQr(true);
        response.setOpcode(requestPacket.getOpcode());
        response.setAa(true);
        response.setTc(false);
        response.setRd(requestPacket.isRd());
        response.setRa(true);
        response.setQdcount(requestPacket.getQdcount());
        response.setAncount(1);

        response.setQuestionSections(requestPacket.getQuestionSections());
        response.setAnswerSections(new ResourceRecord[]{ answer });
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);

        return response;
    }
}
