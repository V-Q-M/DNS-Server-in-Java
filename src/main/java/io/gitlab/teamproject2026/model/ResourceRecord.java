package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.rdata.RData;

import java.util.Arrays;
import java.util.Map;

/**
 * Represents the Answer Section of a DNS packet.
 * Contains the domain name, class, TTL, data length and the actual resource record data.
 */
public class ResourceRecord {

    private String[] name;
    private RRType type;
    private RRClass rrclass;
    private int ttl;
    private int rdlength;
    private RData rdata;
    private int lastProcessedByte;

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    public RRType getType() { return type; }
    public void setType(RRType type) { this.type = type; }

    public RRClass getRrclass() { return rrclass; }
    public void setRrclass(RRClass rrclass) { this.rrclass = rrclass; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public int getRdlength() { return rdlength; }
    public void setRdlength(int rdlength) { this.rdlength = rdlength; }

    public RData getRdata() { return rdata; }
    public void setRdata(RData rdata) { this.rdata = rdata; }

    public int getLastProcessedByte() { return lastProcessedByte; }
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
    public static ResourceRecord fromBytes(byte[] bytes, int byteIndex) {
        ResourceRecord resourceRecord = new ResourceRecord();
        String[] labels = DNSParser.parseName(bytes, byteIndex);
        byteIndex = DNSParser.countNameLength(bytes, byteIndex);
        resourceRecord.setName(labels);
        resourceRecord.setType(RRType.fromValue(DNSParser.as16BitInt(bytes[byteIndex], bytes[byteIndex+1])));
        resourceRecord.setRrclass(RRClass.fromValue(DNSParser.as16BitInt(bytes[byteIndex+2], bytes[byteIndex+3])));
        resourceRecord.setTtl(DNSParser.as32BitInt(bytes[byteIndex+4], bytes[byteIndex+5], bytes[byteIndex+6], bytes[byteIndex+7]));
        resourceRecord.setRdlength(DNSParser.as16BitInt(bytes[byteIndex+8], bytes[byteIndex+9]));
        resourceRecord.setRdata(RData.fromBytes(Arrays.copyOfRange(bytes, byteIndex + 10, byteIndex + 10 + resourceRecord.getRdlength()), resourceRecord.getType()));
        return resourceRecord;
    }
}