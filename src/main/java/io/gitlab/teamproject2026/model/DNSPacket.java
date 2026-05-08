package io.gitlab.teamproject2026.model;

/**
 * Represents a complete DNS packet, including header fields and all sections.
 * The header fields are stored directly in this class as they are fixed in size.
 * Question, Answer, Authority and Additional sections are stored as arrays
 * to allow multiple entries per packet.
 */
public class DNSPacket {

    // Header fields
    private byte[] id;
    private boolean qr;
    private int opcode;
    private boolean aa;
    private boolean tc;
    private boolean rd;
    private boolean ra;
    private final byte[] z = new byte[0];
    private int rcode;

    // Section counts
    private int qdcount;
    private int ancount;
    private int nscount;
    private int arcount;

    // Sections
    private QuestionSection[] questionSections;
    private AnswerSection[] answerSections;
    private AnswerSection[] authoritySections;
    private AnswerSection[] additionalSections;

    public byte[] getId() { return id; }
    public void setId(byte[] id) { this.id = id; }

    public boolean isQr() { return qr; }
    public void setQr(boolean qr) { this.qr = qr; }

    public int getOpcode() { return opcode; }
    public void setOpcode(int opcode) { this.opcode = opcode; }

    public boolean isAa() { return aa; }
    public void setAa(boolean aa) { this.aa = aa; }

    public boolean isTc() { return tc; }
    public void setTc(boolean tc) { this.tc = tc; }

    public boolean isRd() { return rd; }
    public void setRd(boolean rd) { this.rd = rd; }

    public boolean isRa() { return ra; }
    public void setRa(boolean ra) { this.ra = ra; }

    public int getRcode() { return rcode; }
    public void setRcode(int rcode) { this.rcode = rcode; }

    public int getQdcount() { return qdcount; }
    public void setQdcount(int qdcount) { this.qdcount = qdcount; }

    public int getAncount() { return ancount; }
    public void setAncount(int ancount) { this.ancount = ancount; }

    public int getNscount() { return nscount; }
    public void setNscount(int nscount) { this.nscount = nscount; }

    public int getArcount() { return arcount; }
    public void setArcount(int arcount) { this.arcount = arcount; }

    public QuestionSection[] getQuestionSections() { return questionSections; }
    public void setQuestionSections(QuestionSection[] questionSections) { this.questionSections = questionSections; }

    public AnswerSection[] getAnswerSections() { return answerSections; }
    public void setAnswerSections(AnswerSection[] answerSections) { this.answerSections = answerSections; }

    public AnswerSection[] getAuthoritySections() { return authoritySections; }
    public void setAuthoritySections(AnswerSection[] authoritySections) { this.authoritySections = authoritySections; }

    public AnswerSection[] getAdditionalSections() { return additionalSections; }
    public void setAdditionalSections(AnswerSection[] additionalSections) { this.additionalSections = additionalSections; }

    /**
     * Serializes this DNS packet into DNS wire format.
     * Assembles header and all sections into a single byte array
     * that can be sent as a UDP payload.
     *
     * @return byte array representing the complete DNS packet
     */
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Deserializes a complete DNS packet from DNS wire format.
     * Splits the byte array into header and individual sections
     * and passes the relevant bytes to each section's from_bytes method.
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new DnsPacket populated with all parsed fields
     */
    public static DNSPacket from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
