package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Represents the Question Section of a DNS packet.
 * Contains the domain name being queried, the query type and the query class. As we learned from my presentation xD
 */
public class QuestionSection {

    private String[] name;
    private RRType qtype;
    private RRClass qclass;
    private int lastProcessedByte;

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    public RRType getQtype() { return qtype; }
    public void setQtype(RRType qtype) { this.qtype = qtype; }

    public RRClass getQclass() { return qclass; }
    public void setQclass(RRClass qclass) { this.qclass = qclass; }

    public int getLastProcessedByte(){return lastProcessedByte; }
    @Override
    public String toString() {
        return "\n    name: " + String.join(".", getName()) + "\n    qtype: " + getQtype() + "\n    qclass: " + getQclass();
    }
    /**
     * Serializes this question section into DNS wire format.
     *
     * @return byte array representing this question section
     */
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Parses the bytes array from the given index into a QuestionSection object and stores its end in the lastProcessedByte field
     * @param bytes the full byte array of the packet
     * @param byteIndex the index of the byte at which this question section starts
     * @return the parsed question section
     */
    public static QuestionSection fromBytes(byte[] bytes, int byteIndex) {
        QuestionSection questionSection = new QuestionSection();
        String[] labels = DNSParser.parseName(bytes, byteIndex);
        byteIndex = DNSParser.countNameLength(bytes, byteIndex);
        byteIndex++;
        questionSection.setName(labels);
        questionSection.setQtype(RRType.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++])));
        questionSection.setQclass(RRClass.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++])));
        questionSection.lastProcessedByte = byteIndex - 1;
        return questionSection;
    }
}
