package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;

public class ReferralAnalyzer {
    public enum Classification {
        ANSWER,
        REFERRAL,
        CNAME_CHAIN,
        NXDOMAIN,
        NODATA,
        SERVFAIL,
        FORMERR
    }

    public static Classification classify(DNSPacket packet, RRType querytype) {
        RCode rcode= packet.getRcode();

        if (rcode== RCode.SERVFAIL)
            return Classification.SERVFAIL;
        if (rcode== RCode.FORMERR)
            return Classification.FORMERR;
        if (rcode==RCode.NXDOMAIN)
            return Classification.NXDOMAIN;

        ResourceRecord[] answers= packet.getAnswerSections();
        ResourceRecord[] authorities = packet.getAuthoritySections();

        boolean hasAnswer= answers!=null && answers.length>0;
        boolean hasAuthority = authorities != null && authorities.length>0;

        if (hasAnswer && querytype != RRType.CNAME){
            for (ResourceRecord rr : answers){
                if(rr.getType()==RRType.CNAME){
                    return Classification.CNAME_CHAIN;
                }
            }
        }
        if (hasAnswer)
            return Classification.ANSWER;
        if (hasAuthority){
            for (ResourceRecord rr: authorities){
                if (rr.getType()==RRType.NS)
                    return Classification.REFERRAL;
            }
        }
        if(rcode== RCode.NOERROR)
            return Classification.NODATA;
        return Classification.FORMERR;

    }
}