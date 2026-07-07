package io.gitlab.teamproject2026.model.rdata;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

/** Represents a DNS NS (Name Server) record. Specifies the authoritative name server for a domain. */
public final class RDataNS implements RData {
    private String[] nsdname;

    public RDataNS() {}

    public RDataNS(String[] nsdname) {
        this.nsdname = nsdname;
    }

    @Override
    public int getType() {
        return RRType.NS;
    }

    public String[] getNsdname() {
        return nsdname;
    }

    public void setNsdname(String[] nsdname) {
        this.nsdname = nsdname;
    }

    @Override
    public String toString() {
        return String.join(".", nsdname) + ".";
    }

    @Override
    public byte[] toBytes() {
        return encodeName(nsdname);
    }

    public static RDataNS fromBytes(byte[] bytes, int byteIndex) {
        return new RDataNS(DNSParser.parseName(bytes, byteIndex));
    }
}
