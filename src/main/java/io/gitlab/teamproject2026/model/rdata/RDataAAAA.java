package io.gitlab.teamproject2026.model.rdata;

import java.util.Arrays;

/**
 * Represents a DNS AAAA record.
 * Maps a domain name to an IPv6 address (128-bit).
 */
public class RDataAAAA implements RData {
    public static final int TYPE = 28;

    private byte[] address;

    public byte[] getAddress() { return address; }
    public void setAddress(byte[] address) { this.address = address; }

    @Override
    public String toString(){
        return Arrays.toString(address);
    }

    @Override
    public byte[] toBytes() {
        return address;
    }

    public static RDataAAAA fromBytes(byte[] bytes) {
        RDataAAAA rDataAAAA = new RDataAAAA();
        rDataAAAA.setAddress(bytes);
        return rDataAAAA;
    }
}