package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataRRSIG;

public class ReferralAnalyzer {
    public enum Classification {
        ANSWER,
        REFERRAL,
        CNAME_CHAIN,
        NXDOMAIN,
        NODATA,
        SERVFAIL,
        FORMERR,
        REFUSED
    }

    public static Classification classify(DNSPacket packet, int querytype) {
        int rcode = packet.getRcode();

        if (rcode == RCode.SERVFAIL) return Classification.SERVFAIL;
        if (rcode == RCode.FORMERR) return Classification.FORMERR;
        if (rcode == RCode.NXDOMAIN) return Classification.NXDOMAIN;

        ResourceRecord[] answers = packet.getAnswerSections();
        ResourceRecord[] authorities = packet.getAuthoritySections();

        boolean hasAnswer = answers != null && answers.length > 0;
        boolean hasAuthority = authorities != null && authorities.length > 0;

        if (hasAnswer) {
            for (ResourceRecord rr : answers) {
                if (rr.getType() == querytype) return Classification.ANSWER;
                if (querytype == RRType.RRSIG && rr.getRdata() instanceof RDataRRSIG) {
                    return Classification.ANSWER;
                }
            }
            if (querytype != RRType.CNAME)
                for (ResourceRecord rr : answers) if (rr.getType() == RRType.CNAME) return Classification.CNAME_CHAIN;
            return Classification.SERVFAIL;
        }
        if (hasAuthority) {
            for (ResourceRecord rr : authorities) {
                if (rr.getType() == RRType.NS) return Classification.REFERRAL;
            }
        }
        if (rcode == RCode.NOERROR) return Classification.NODATA;
        return Classification.FORMERR;
    }
}
