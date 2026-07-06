package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.OPTPseudoResourceRecord;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataOPTOption;

public class DNSResponseBuilder {
    private final boolean recursionAllowed;
    private final int ednsUdpSizeLimit;

    public DNSResponseBuilder(boolean recursionAllowed, int ednsUdpSizeLimit) {
        this.recursionAllowed = recursionAllowed;
        this.ednsUdpSizeLimit = ednsUdpSizeLimit;
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

    public DNSPacket nxDomain(DNSPayload payload, DNSPacket query, boolean authoritative) {
        DNSPacket response = baseResponse(query);
        response.setAa(authoritative);
        response.setRcode(RCode.NXDOMAIN);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(payload.authority().toArray(new ResourceRecord[0]));
        response.setAdditionalSections(payload.additional().toArray(new ResourceRecord[0]));
        return response;
    }

    public DNSPacket servFail(DNSPayload payload, DNSPacket query) {
        DNSPacket response = baseResponse(query);
        response.setAa(false);
        response.setRcode(RCode.SERVFAIL);
        response.setAnswerSections(payload.answer().toArray(new ResourceRecord[0]));
        response.setAuthoritySections(payload.authority().toArray(new ResourceRecord[0]));
        response.setAdditionalSections(payload.additional().toArray(new ResourceRecord[0]));
        return response;
    }

    public DNSPacket truncatedUDPResponse(DNSPacket query, boolean authoritative) {
        DNSPacket response = baseResponse(query);
        response.setAa(authoritative);
        response.setTc(true);
        response.setRcode(RCode.NOERROR);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);
        return response;
    }

    private DNSPacket baseResponse(DNSPacket query) {
        DNSPacket response = new DNSPacket();
        response.setId(query.getId());
        response.setOpcode(query.getOpcode());
        response.setRd(query.isRd());
        response.setRa(recursionAllowed);
        response.setQuestionSections(query.getQuestionSections());

        OPTPseudoResourceRecord queryEdnsRecord = query.getEdnsRecord();
        if (queryEdnsRecord != null) {
            // Send back new OPT RR that includes server udp size limit
            OPTPseudoResourceRecord ednsResponse = new OPTPseudoResourceRecord(
                    ednsUdpSizeLimit, 0, 0, queryEdnsRecord.getDNSSecOK(), new RDataOPTOption());
            response.setEdnsRecord(ednsResponse);
        }

        return response;
    }
}
