package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;

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
    public ResourceRecord getAdditionalSection(int index) {return additionalSections[index];}

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
     * and parses them individually
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new DnsPacket populated with all parsed fields
     */
    public static DNSPacket fromBytes(byte[] bytes) {
        if(bytes.length <= 12){
            throw new IllegalArgumentException("Invalid form body. DNS Packets require at least 12 bytes for the Header");
        }
        DNSPacket dnsPacket = new DNSPacket();
        dnsPacket.setId(new byte[]{bytes[0], bytes[1]});

        dnsPacket.setQr(DNSParser.extractBit(bytes[2], 0));
        dnsPacket.setOpcode(OpCode.fromValue(DNSParser.subBits(bytes[2], 1, 5)));
        dnsPacket.setAa(DNSParser.extractBit(bytes[2], 5));
        dnsPacket.setTc(DNSParser.extractBit(bytes[2], 6));
        dnsPacket.setRd(DNSParser.extractBit(bytes[2], 7));

        dnsPacket.setRa(DNSParser.extractBit(bytes[3], 0));
        dnsPacket.setZ(DNSParser.subBits(bytes[3], 1, 4));
        dnsPacket.setRcode(RCode.fromValue(DNSParser.subBits(bytes[3], 4, 7)));

        dnsPacket.setQdcount(DNSParser.as16BitInt(bytes[4], bytes[5]));
        dnsPacket.setAncount(DNSParser.as16BitInt(bytes[6], bytes[7]));
        dnsPacket.setNscount(DNSParser.as16BitInt(bytes[8], bytes[9]));
        dnsPacket.setArcount(DNSParser.as16BitInt(bytes[10], bytes[11]));

        int byte_index = 12;
        dnsPacket.setQuestionSections(new QuestionSection[dnsPacket.getQdcount()]);
        for (int i = 0; i < dnsPacket.getQdcount(); i++) {
            dnsPacket.getQuestionSections()[i] = QuestionSection.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getQuestionSection(i).getLastProcessedByte() + 1;
        }
        dnsPacket.setAnswerSections(new ResourceRecord[dnsPacket.getQdcount()]);
        for (int i = 0; i < dnsPacket.getAncount(); i++) {
            dnsPacket.getAnswerSections()[i] = ResourceRecord.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getAnswerSection(i).getLastProcessedByte() + 1;
        }
        dnsPacket.setAuthoritySections(new ResourceRecord[dnsPacket.getNscount()]);
        for (int i = 0; i < dnsPacket.getNscount(); i++) {
            dnsPacket.getAuthoritySections()[i] = ResourceRecord.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getAuthoritySection(i).getLastProcessedByte() + 1;
        }

        dnsPacket.setAdditionalSections(new ResourceRecord[dnsPacket.getArcount()]);
        for (int i = 0; i < dnsPacket.getArcount(); i++) {
            dnsPacket.getAdditionalSections()[i] = ResourceRecord.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getAdditionalSection(i).getLastProcessedByte() + 1;
        }

        return dnsPacket;
    }
}
