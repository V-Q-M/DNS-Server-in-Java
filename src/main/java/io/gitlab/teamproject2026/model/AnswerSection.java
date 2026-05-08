package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.model.records.ResourceRecord;

/**
 * Represents the Answer Section of a DNS packet.
 * Contains the domain name, class, TTL, data length and the actual resource record data.
 */
public class AnswerSection {

    private String[] name;
    private int rrclass;
    private int ttl;
    private int rdlength;
    private ResourceRecord rdata;

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    public int getRrclass() { return rrclass; }
    public void setRrclass(int rrclass) { this.rrclass = rrclass; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public int getRdlength() { return rdlength; }
    public void setRdlength(int rdlength) { this.rdlength = rdlength; }

    public ResourceRecord getRdata() { return rdata; }
    public void setRdata(ResourceRecord rdata) { this.rdata = rdata; }

    /**
     * Serializes this answer section into DNS wire format.
     *
     * @return byte array representing this answer section
     */
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Deserializes an answer section from DNS wire format.
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new AnswerSection populated with the parsed data
     */
    public static AnswerSection from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
