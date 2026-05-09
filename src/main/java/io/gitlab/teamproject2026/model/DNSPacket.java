package io.gitlab.teamproject2026.model;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Represents a complete DNS packet, including header fields and all sections.
 * The header fields are stored directly in this class as they are fixed in size.
 * Question, Answer, Authority and Additional sections are stored as arrays
 * to allow multiple entries per packet.
 */
public class DNSPacket {

    // Header fields
    private byte[] id;
    private boolean qr; // false=question, true=answer
    private OpCode opcode;
    private boolean aa;
    private boolean tc;
    private boolean rd;
    private boolean ra;
    private byte z = 0;
    private RCode rcode;

    // Section counts
    private int qdcount;
    private int ancount;
    private int nscount;
    private int arcount;

    // Sections
    private QuestionSection[] questionSections;
    private ResourceRecord[] answerSections;
    private ResourceRecord[] authoritySections;
    private ResourceRecord[] additionalSections;

    public byte[] getId() { return id; }
    public void setId(byte[] id) { this.id = id; }

    public boolean isQr() { return qr; }
    public void setQr(boolean qr) { this.qr = qr; }

    public OpCode getOpcode() { return opcode; }
    public void setOpcode(OpCode opcode) { this.opcode = opcode; }

    public boolean isAa() { return aa; }
    public void setAa(boolean aa) { this.aa = aa; }

    public boolean isTc() { return tc; }
    public void setTc(boolean tc) { this.tc = tc; }

    public boolean isRd() { return rd; }
    public void setRd(boolean rd) { this.rd = rd; }

    public boolean isRa() { return ra; }
    public void setRa(boolean ra) { this.ra = ra; }

    public byte getZ() { return z; }
    public void setZ(byte z) { this.z = z; }

    public RCode getRcode() { return rcode; }
    public void setRcode(RCode rcode) { this.rcode = rcode; }

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
    public QuestionSection getQuestionSection(int index) {return questionSections[index];}

    public ResourceRecord[] getAnswerSections() { return answerSections; }
    public void setAnswerSections(ResourceRecord[] answerSections) { this.answerSections = answerSections; }
    public ResourceRecord getAnswerSection(int index) {return answerSections[index];}

    public ResourceRecord[] getAuthoritySections() { return authoritySections; }
    public void setAuthoritySections(ResourceRecord[] authoritySections) { this.authoritySections = authoritySections; }
    public ResourceRecord getAuthoritySection(int index) {return authoritySections[index];}

    public ResourceRecord[] getAdditionalSections() { return additionalSections; }
    public void setAdditionalSections(ResourceRecord[] additionalSections) { this.additionalSections = additionalSections; }
    public ResourceRecord getAdditionalSections(int index) {return additionalSections[index];}

    @Override
    public String toString() {
        return "id: " + Integer.toBinaryString(id[0] & 0xFF) + Integer.toBinaryString(id[1] & 0xFF) + "\n"
        + "qr: " + qr + "\n"
        + "opcode: " + opcode + "\n"
        + "aa: " + aa + "\n"
        + "tc: " + tc + "\n"
        + "rd: " + rd + "\n"
        + "ra: " + ra + "\n"
        + "z: " + Integer.toBinaryString(z & 0xFF) + "\n"
        + "rcode: " + rcode + "\n"
        + "qdcount: " + qdcount + "\n"
        + "ancount: " + ancount + "\n"
        + "nscount: " + nscount + "\n"
        + "arcount: " + arcount + "\n"
        + "questionSections: " + Arrays.toString(questionSections)+ "\n";
    }
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
        /*
        for (byte bte : bytes){
            System.out.println(Integer.toBinaryString(bte & 0xFF));
        }*/
        DNSPacket dnsPacket = new DNSPacket();
        dnsPacket.setId(new byte[]{bytes[0], bytes[1]});

        dnsPacket.setQr((bytes[2] >> 7 & 1) == 1); // relevant bit shifted all the way to the right, masked, so only it remains => 0=false, 1=true
        dnsPacket.setOpcode(OpCode.fromValue((bytes[2] >> 3) & 0x0F)); // relevant 4 bits shifted to the right, masked and converted to int
        dnsPacket.setAa((bytes[2] >> 2 & 1) == 1);
        dnsPacket.setTc((bytes[2] >> 1 & 1) == 1);
        dnsPacket.setRd((bytes[2] & 1) == 1);

        dnsPacket.setRa((bytes[3] >> 7 & 1) == 1);
        dnsPacket.setZ((byte) ((bytes[3] >> 4) & 8));
        dnsPacket.setRcode(RCode.fromValue(bytes[3] & 0x0F));

        dnsPacket.setQdcount((((bytes[4] & 0xFF) << 8) + (bytes[5] & 0xFF)) & 0xFFFF);
        dnsPacket.setAncount((((bytes[6] & 0xFF) << 8) + (bytes[7] & 0xFF)) & 0xFFFF);
        dnsPacket.setNscount((((bytes[8] & 0xFF) << 8) + (bytes[9] & 0xFF)) & 0xFFFF);
        dnsPacket.setArcount((((bytes[10] & 0xFF) << 8) + (bytes[11] & 0xFF)) & 0xFFFF);

        dnsPacket.setQuestionSections(new QuestionSection[]{QuestionSection.from_bytes(Arrays.copyOfRange(bytes, 12, bytes.length))});
        System.out.println(dnsPacket.getQuestionSection(0).getFinalParsedByte());
        System.out.println(bytes[dnsPacket.getQuestionSection(0).getFinalParsedByte()]);
        /*int byte_index = 12;
        dnsPacket.setQuestionSections(new QuestionSection[dnsPacket.getQdcount()]);
        Arrays.fill(dnsPacket.getQuestionSections(), new QuestionSection());*/
        return dnsPacket;
    }
}
