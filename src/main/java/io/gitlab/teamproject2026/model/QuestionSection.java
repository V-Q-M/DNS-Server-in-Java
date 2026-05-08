package io.gitlab.teamproject2026.model;

/**
 * Represents the Question Section of a DNS packet.
 * Contains the domain name being queried, the query type and the query class. As we learned from my presentation xD
 */
public class QuestionSection {

    private String[] name;
    private RRType qtype;
    private RRClass qclass;

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    public RRType getQtype() { return qtype; }
    public void setQtype(RRType qtype) { this.qtype = qtype; }

    public RRClass getQclass() { return qclass; }
    public void setQclass(RRClass qclass) { this.qclass = qclass; }

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
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
