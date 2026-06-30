package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.model.*;

public class DNSResponseBuilder {
    private final boolean recursionAllowed;

    public DNSResponseBuilder(boolean recursionAllowed) {
        this.recursionAllowed = recursionAllowed;
    }

    public DNSPacket noError(DNSPayload payload, DNSPacket query, boolean authoritative) {
        DNSPacket response = baseResponse(query);
        response.setAa(authoritative);
        response.setRcode(RCode.NOERROR);
        response.setAnswerSections(payload.answer().toArray(new ResourceRecord[0]));
        response.setAuthoritySections(payload.authority().toArray(new ResourceRecord[0]));
        response.setAdditionalSections(payload.additional().toArray(new ResourceRecord[0]));
        return response;
    }

    public DNSPacket refused(DNSPayload payload, DNSPacket query) {
        DNSPacket response = baseResponse(query);
        response.setAa(false);
        response.setRa(false);
        response.setRcode(RCode.REFUSED);
        response.setAnswerSections(payload.answer().toArray(new ResourceRecord[0]));
        response.setAuthoritySections(payload.authority().toArray(new ResourceRecord[0]));
        response.setAdditionalSections(payload.additional().toArray(new ResourceRecord[0]));
        return response;
    }

    public DNSPacket nxDomain(DNSPacket query) {
        DNSPacket response = baseResponse(query);
        response.setAa(true);
        response.setRcode(RCode.NXDOMAIN);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);
        return response;
    }

    public DNSPacket truncatedUdpResponse(DNSPacket query, DNSPacket response) {
        response.setTc(true);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);
        response.setQuestionSections(query.getQuestionSections());
        return response;
    }

    private DNSPacket baseResponse(DNSPacket query) {
        DNSPacket response = new DNSPacket();
        response.setId(query.getId());
        response.setOpcode(query.getOpcode());
        response.setRd(query.isRd());
        response.setRa(recursionAllowed);
        response.setQuestionSections(query.getQuestionSections());
        return response;
    }
}