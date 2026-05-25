package io.gitlab.teamproject2026.model.rdata;

import java.util.Arrays;

/**
 * Represents a DNS A record.
 * Maps a domain name to an IPv4 address (32-bit).
 */
public class RDataA implements RData {
    public static final int TYPE = 1;

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

    public static RDataA fromBytes(byte[] bytes) {
        RDataA rDataA = new RDataA();
        rDataA.setAddress(bytes);
        return rDataA;
    }
}