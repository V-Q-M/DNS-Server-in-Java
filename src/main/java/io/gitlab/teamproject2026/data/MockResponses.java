package io.gitlab.teamproject2026.data;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.RDataA;


public class MockResponses extends DNSPacket {
    public static DNSPacket a(DNSPacket requestPacket){
        ResourceRecord answer = new ResourceRecord();
        answer.setName(new String[]{"uni-tuebingen.de"});
        answer.setType(RRType.A);
        answer.setRrclass(RRClass.IN);
        answer.setTtl(500);
        RDataA a = new RDataA();
        a.setAddress(new byte[]{ (byte)93, (byte)184, (byte)216, (byte)34 });
        answer.setRdata(a);

        return buildResponse(requestPacket, answer);
    }


    private static DNSPacket buildResponse(DNSPacket requestPacket, ResourceRecord answer) {
        DNSPacket response = new DNSPacket();
        response.setId(requestPacket.getId());
        response.setQr(true); // this is a response
        response.setOpcode(requestPacket.getOpcode());
        response.setAa(true);
        response.setTc(false);
        response.setRd(response.isRd());
        response.setRa(true); // recursion available
        response.setQdcount(requestPacket.getQdcount());
        response.setAncount(1);

        response.setQuestionSections(requestPacket.getQuestionSections());
        response.setAnswerSections(new ResourceRecord[]{answer});
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);

        return response;
    }
}
