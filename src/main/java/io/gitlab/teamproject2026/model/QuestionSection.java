package io.gitlab.teamproject2026.model;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Represents the Question Section of a DNS packet.
 * Contains the domain name being queried, the query type and the query class. As we learned from my presentation xD
 */
public class QuestionSection {

    private String[] name;
    private RRType qtype;
    private RRClass qclass;
    private int finalParsedByte = -1;

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    public RRType getQtype() { return qtype; }
    public void setQtype(RRType qtype) { this.qtype = qtype; }

    public RRClass getQclass() { return qclass; }
    public void setQclass(RRClass qclass) { this.qclass = qclass; }

    public int getFinalParsedByte() { return finalParsedByte; }
    public void setFinalParsedByte(int finalParsedByte) { this.finalParsedByte = finalParsedByte; }

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
     * Deserializes a question section from DNS wire format.
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new QuestionSection populated with the parsed data
     */
    public static QuestionSection from_bytes(byte[] bytes) {
        QuestionSection questionSection = new QuestionSection();
        LinkedList<String> labels = new LinkedList<>();
        int byteIndex = 0;
        while (bytes[byteIndex] != 0) {
            int labelLength = bytes[byteIndex++] & 0xFF;
            labels.add(new String(Arrays.copyOfRange(bytes, byteIndex, byteIndex + labelLength)));
            byteIndex += labelLength;
        }
        byteIndex++;
        questionSection.setName(labels.toArray(new String[0]));
        questionSection.setQtype(RRType.fromValue((((bytes[byteIndex] & 0xFF) << 8) + (bytes[byteIndex+1] & 0xFF)) & 0xFFFF));
        questionSection.setQclass(RRClass.fromValue((((bytes[byteIndex+2] & 0xFF) << 8) + (bytes[byteIndex+3] & 0xFF)) & 0xFFFF));

        questionSection.setFinalParsedByte(byteIndex);
        return questionSection;
    }
}
