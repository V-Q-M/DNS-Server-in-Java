package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

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

    /**
     * Only curated if parsed from bytes and used by the surrounding packet to adjust the parsing offset
     * @return amount of bytes used to parse this QuestionSection
     */
    public int getLastProcessedByte(){return lastProcessedByte; }
    @Override
    public String toString() {
        return "\n    name: " + String.join(".", getName()) + "." + "\n    qtype: " + getQtype() + "\n    qclass: " + getQclass();
    }
    /**
     * Serializes this question section into DNS wire format.
     *
     * @return byte array representing this question section
     */
    public byte[] toBytes() {
        byte[] name = encodeName(String.join(".", this.getName())+ ".");
        byte[] result = new byte[name.length + 4];

        System.arraycopy(name, 0, result, 0, name.length);

        int pos = name.length;
        result[pos]     = (byte) (this.getQtype().getValue() >> 8);
        result[pos + 1] = (byte) (this.getQtype().getValue());
        result[pos + 2] = (byte) (this.getQclass().getValue() >> 8);
        result[pos + 3] = (byte) (this.getQclass().getValue());

        return result;

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
        byteIndex += DNSParser.countNameLength(bytes, byteIndex);
        questionSection.setName(labels);
        questionSection.setQtype(RRType.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++])));
        questionSection.setQclass(RRClass.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++])));
        questionSection.lastProcessedByte = byteIndex;
        return questionSection;
    }
    
    /**
     * returns only name of question section (used by stubresolver for logging)
     */
    public String nameToString() { return 
        String.join(".", this.name) + "." + "\n";
    }
}
