package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.OPTPseudoResourceRecord;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataOPTOption;
import java.util.Arrays;

public class DNSResponseBuilder {
    private final int ednsUdpSizeLimit;
    private final boolean dnssecEnabled;
    private final boolean dnssecValidation;
    private final boolean recursionAllowed;

    public DNSResponseBuilder(
            int ednsUdpSizeLimit, boolean dnssecEnabled, boolean dnssecValidation, boolean recursionAllowed) {
        this.recursionAllowed = recursionAllowed;
        this.ednsUdpSizeLimit = ednsUdpSizeLimit;
        this.dnssecEnabled = dnssecEnabled;
        this.dnssecValidation = dnssecValidation;
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

    public byte[] formError(byte[] query) throws IllegalArgumentException {
        if (query.length < 12) throw new IllegalArgumentException("query too short to build a valid DNS response");
        byte[] r = Arrays.copyOf(query, query.length);
        r[2] = (byte) ((r[2] & 0x01) | 0x80);
        // r[2]: QR=1 (response), Opcode=0, AA=0 (not authoritative), TC=0 (no upstream response to copy from),
        // RD=preserved from query
        r[3] = (byte)
                0x81; // RA preserved from query, Z=0 (reserved 0), AD=0 (no DNSSEC validation), CD=0 (DNSSEC client
        // flag so irrelevant for response), RCODE=1 (FORM ERROR)
        // since this is a failure response there are no records to return, so the rest is 0
        r[6] = 0;
        r[7] = 0;
        r[8] = 0;
        r[9] = 0;
        r[10] = 0;
        r[11] = 0;
        return r;
    }
}
