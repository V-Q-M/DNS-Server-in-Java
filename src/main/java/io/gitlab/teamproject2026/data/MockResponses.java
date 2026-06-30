package io.gitlab.teamproject2026.data;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import io.gitlab.teamproject2026.model.rdata.dnssec.*;


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

    public static DNSPacket ds(DNSPacket requestPacket) {
        RDataDS rdata = new RDataDS( 12345, 8, 2, new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f, (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13, (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1a, (byte) 0x1b, (byte) 0x1c, (byte) 0x1d, (byte) 0x1e, (byte) 0x1f, (byte) 0x20 } );
        return buildResponse(requestPacket, RRType.DS, rdata);
    }

    public static DNSPacket rrsig(DNSPacket requestPacket) {
        RDataRRSIG rdata = new RDataRRSIG( RRType.A, 8, 2, 300, 1893456000L, 1735689600L, 12345, new String[]{"uni-tuebingen", "de"}, new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 } );
        return buildResponse(requestPacket, RRType.RRSIG, rdata);
    }

    public static DNSPacket nsec(DNSPacket requestPacket) {
        RDataNSEC rdata = new RDataNSEC( new String[]{"next", "uni-tuebingen", "de"}, new byte[]{ (byte) 0x00, (byte) 0x06, (byte) 0x22, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03 } );
        return buildResponse(requestPacket, RRType.NSEC, rdata);
    }

    public static DNSPacket dnskey(DNSPacket requestPacket) {
        RDataDNSKEY rdata = new RDataDNSKEY( 256, 3, 8, new byte[]{ (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd } );
        return buildResponse(requestPacket, RRType.DNSKEY, rdata);
    }

    public static DNSPacket nsec3(DNSPacket requestPacket) {
        RDataNSEC3 rdata = new RDataNSEC3( 1, 0, 2, new byte[]{ (byte) 0xaa, (byte) 0xbb }, new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f, (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13, (byte) 0x14 }, new byte[]{ (byte) 0x00, (byte) 0x06, (byte) 0x22, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03 } );
        ResourceRecord answer = new ResourceRecord( new String[]{ "B4UM86EGHHDS6NEA196SMVMLO4ORS995", "uni-tuebingen", "de" }, RRClass.IN, 300, rdata );
        return new DNSPacket(
                requestPacket.getId(),
                true,
                requestPacket.getOpcode(),
                true,
                false,
                requestPacket.isRd(),
                true,
                (byte) 0,
                RCode.NOERROR,
                requestPacket.getQuestionSections(),
                new ResourceRecord[]{ answer },
                new ResourceRecord[0],
                new ResourceRecord[0]
        );
    }
    public static DNSPacket nsec3param(DNSPacket requestPacket) {
        RDataNSEC3PARAM rdata = new RDataNSEC3PARAM( 1, 0, 2, new byte[]{ (byte) 0xaa, (byte) 0xbb } );
        return buildResponse(requestPacket, RRType.NSEC3PARAM, rdata);
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
            case DS          -> ds(requestPacket);
            case RRSIG       -> rrsig(requestPacket);
            case NSEC        -> nsec(requestPacket);
            case DNSKEY      -> dnskey(requestPacket);
            case NSEC3       -> nsec3(requestPacket);
            case NSEC3PARAM  -> nsec3param(requestPacket);
        };
    }


    private static DNSPacket buildResponse(DNSPacket requestPacket, RRType type, RData rdata) {
        ResourceRecord answer = new ResourceRecord();
        answer.setName(requestPacket.getQuestionSections()[0].getName());
        //answer.setType(type);
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
