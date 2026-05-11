package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;

/**
 * Represents a DNS MX (Mail Exchange) record.
 * Specifies the mail server responsible for a domain.
 */
public class RDataMX implements RData {
    public static final int TYPE = 15;

    private int preference;
    private String[] exchange;

    public int getPreference() { return preference; }
    public void setPreference(int preference) { this.preference = preference; }

    public String[] getExchange() { return exchange; }
    public void setExchange(String[] exchange) { this.exchange = exchange; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static RDataMX fromBytes(byte[] bytes) {
        RDataMX rDataMX = new RDataMX();
        rDataMX.setPreference(DNSParser.as16BitInt(bytes[0], bytes[1]));
        rDataMX.setExchange(DNSParser.parseName(bytes, 2));
        return rDataMX;
    }
}