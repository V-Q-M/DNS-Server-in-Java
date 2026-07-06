package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Represents a complete DNS packet, including header fields and all sections. The header fields are stored directly in
 * this class as they are fixed in size. Question, Answer, Authority and Additional sections are stored as arrays to
 * allow multiple entries per packet.
 */
public class DNSPacket {

    // Header fields
    private byte[] id;
    private boolean qr = true; // false=question, true=answer
    private int opcode;
    private boolean aa = false;
    private boolean tc = false;
    private boolean rd;
    private boolean ra = true;
    private byte z = 0;

    // EDNS Specific fields, only curated on edns
    private Boolean DNSSecCheckingDisabled = null;
    private Boolean DNSSecAuthenticatedData = null;
    private int rcode;

    // Section counts
    private int qdcount = 0;
    private int ancount = 0;
    private int nscount = 0;
    private int arcount = 0;

    // Sections
    private QuestionSection[] questionSections;
    private ResourceRecord[] answerSections;
    private ResourceRecord[] authoritySections;
    private ResourceRecord[] additionalSections;

    private OPTPseudoResourceRecord ednsRecord;

    public DNSPacket() {}

    public DNSPacket(
            byte[] id,
            boolean qr,
            int opcode,
            boolean aa,
            boolean tc,
            boolean rd,
            boolean ra,
            byte z,
            int rcode,
            QuestionSection[] questionSections,
            ResourceRecord[] answerSections,
            ResourceRecord[] authoritySections,
            ResourceRecord[] additionalSections) {
        this.id = id;
        this.qr = qr;
        this.opcode = opcode;
        this.aa = aa;
        this.tc = tc;
        this.rd = rd;
        this.ra = ra;
        this.z = z;
        this.rcode = rcode;

        this.questionSections = questionSections != null ? questionSections : new QuestionSection[0];
        this.answerSections = answerSections != null ? answerSections : new ResourceRecord[0];
        this.authoritySections = authoritySections != null ? authoritySections : new ResourceRecord[0];
        this.additionalSections = additionalSections != null ? additionalSections : new ResourceRecord[0];

        this.qdcount = this.questionSections.length;
        this.ancount = this.answerSections.length;
        this.nscount = this.authoritySections.length;
        this.arcount = this.additionalSections.length;
    }

    public byte[] getId() {
        return id;
    }

    public String getIdString() {
        return "0x" + HexFormat.of().formatHex(id);
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public boolean isQr() {
        return qr;
    }

    public void setQr(boolean qr) {
        this.qr = qr;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public boolean isAa() {
        return aa;
    }

    public void setAa(boolean aa) {
        this.aa = aa;
    }

    public boolean isTc() {
        return tc;
    }

    public void setTc(boolean tc) {
        this.tc = tc;
    }

    public boolean isRd() {
        return rd;
    }

    public void setRd(boolean rd) {
        this.rd = rd;
    }

    public boolean isRa() {
        return ra;
    }

    public void setRa(boolean ra) {
        this.ra = ra;
    }

    public byte getZ() {
        return z;
    }

    public void setZ(byte z) {
        this.z = z;
    }

    public int getRcode() {
        return rcode;
    }

    public void setRcode(int rcode) {
        this.rcode = rcode;
    }

    public int getQdcount() {
        return qdcount;
    }

    public void setQdcount(int qdcount) {
        this.qdcount = qdcount;
    }

    public int getAncount() {
        return ancount;
    }

    public void setAncount(int ancount) {
        this.ancount = ancount;
    }

    public int getNscount() {
        return nscount;
    }

    public void setNscount(int nscount) {
        this.nscount = nscount;
    }

    public int getArcount() {
        return arcount;
    }

    public void setArcount(int arcount) {
        this.arcount = arcount;
    }

    public QuestionSection[] getQuestionSections() {
        return questionSections;
    }

    public void setQuestionSections(QuestionSection[] questionSections) {
        this.qdcount = questionSections.length;
        this.questionSections = questionSections;
    }

    public QuestionSection getQuestionSection(int index) {
        return questionSections[index];
    }

    public ResourceRecord[] getAnswerSections() {
        return answerSections;
    }

    public void setAnswerSections(ResourceRecord[] answerSections) {
        this.ancount = answerSections.length;
        this.answerSections = answerSections;
    }

    public ResourceRecord getAnswerSection(int index) {
        return answerSections[index];
    }

    public ResourceRecord[] getAuthoritySections() {
        return authoritySections;
    }

    public void setAuthoritySections(ResourceRecord[] authoritySections) {
        this.nscount = authoritySections.length;
        this.authoritySections = authoritySections;
    }

    public ResourceRecord getAuthoritySection(int index) {
        return authoritySections[index];
    }

    public ResourceRecord[] getAdditionalSections() {
        return additionalSections;
    }

    public void setAdditionalSections(ResourceRecord[] additionalSections) {
        this.arcount = additionalSections.length;
        this.additionalSections = additionalSections;
    }

    public OPTPseudoResourceRecord getEdnsRecord() {
        return ednsRecord;
    }
    /**
     * Addition of EDNS OPTPseudo record changes the meaning of headers and other behaviour, so setting this will
     * recalculate the Packet as EDNS Packet
     *
     * @param ednsRecord A Pseudo record enabling edns with additional info for edns
     */
    public void setEdnsRecord(OPTPseudoResourceRecord ednsRecord) {

        // parsing z into human-readable data
        this.setDNSSecCheckingDisabled(
                DNSParser.extractBit(this.z, 5)); // Parser shift them to the right so Z = 0x00000abc
        this.setDNSSecAuthenticatedData(DNSParser.extractBit(this.z, 6));

        // extending Rcode to 12 bits
        if (this.ednsRecord != null) { // restore previous, simple RCode in case of an OPTRR change
            this.rcode -= (this.ednsRecord.getExtendedRcode() << 4);
        }
        this.rcode += (ednsRecord.getExtendedRcode()
                << 4); // EDNS Rcodes are aaaaaaaabbbb with a from the ednsrecord and b from the original rcode
        this.ednsRecord = ednsRecord;
    }

    public Boolean getDNSSecCheckingDisabled() {
        return DNSSecCheckingDisabled;
    }

    public void setDNSSecCheckingDisabled(Boolean DNSSecCheckingDisabled) {
        this.DNSSecCheckingDisabled = DNSSecCheckingDisabled;
        this.z = (byte) (((DNSSecCheckingDisabled ? 1 : 0) << 2)
                | ((DNSParser.extractBit(z, 6) ? 1 : 0) << 1)
                | (DNSParser.extractBit(z, 7) ? 1 : 0));
    }

    public Boolean getDNSSecAuthenticatedData() {
        return DNSSecAuthenticatedData;
    }

    public void setDNSSecAuthenticatedData(Boolean DNSSecAuthenticatedData) {
        this.DNSSecAuthenticatedData = DNSSecAuthenticatedData;
        this.z = (byte) (((DNSParser.extractBit(z, 5) ? 1 : 0) << 2)
                | ((DNSSecAuthenticatedData ? 1 : 0) << 1)
                | (DNSParser.extractBit(z, 7) ? 1 : 0));
    }

    public ResourceRecord getAdditionalSection(int index) {
        return additionalSections[index];
    }

    @Override
    public String toString() {
        return "id:"
                + String.format("%16s", Integer.toBinaryString(((id[0] & 0xFF) << 8) | (id[1] & 0xFF)))
                        .replace(" ", "0")
                + "\n"
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
                + "questionSections: " + Arrays.toString(questionSections) + "\n"
                + "answerSections: " + Arrays.toString(answerSections) + "\n"
                + "authoritySections: " + Arrays.toString(authoritySections) + "\n"
                + "additionalSections: " + Arrays.toString(additionalSections) + "\n"
                + "ednsPseudoRR: " + ednsRecord + "\n";
    }

    /**
     * Serializes this DNS packet into DNS wire format. Assembles header and all sections into a single byte array that
     * can be sent as a UDP payload.
     *
     * @return byte array representing the complete DNS packet
     */
    public byte[] toBytes() {
        // Header has a length of 12 bytes
        byte[] header = new byte[12];

        // put id
        header[0] = this.getId()[0];
        header[1] = this.getId()[1];

        // first flag byte
        byte flagsByte1 = 0;
        flagsByte1 |= (byte) ((this.isQr() ? 1 : 0) << 7); // QR
        flagsByte1 |= (byte) ((this.getOpcode()) << 3); // Opcode
        flagsByte1 |= (byte) ((this.isAa() ? 1 : 0) << 2); // AA
        flagsByte1 |= (byte) ((this.isTc() ? 1 : 0) << 1); // TC
        flagsByte1 |= (byte) (this.isRd() ? 1 : 0); // RD
        header[2] = flagsByte1;

        // second flag byte
        byte flagsByte2 = 0;
        flagsByte2 |= (byte) ((this.isRa() ? 1 : 0) << 7); // Ra
        if (this.getEdnsRecord() != null) { // Means Edns is enabled and some z bits are used, otherwise it stays on 0
            flagsByte2 |= (byte) (this.z << 4); // 3 z-btis are all the way to the right but need to be at index 9-11
        }

        flagsByte2 |= (byte) (this.getRcode() & 0x0F); // rcode
        header[3] = flagsByte2;

        header[4] = (byte) ((this.getQdcount() >> 8) & 0xFF);
        header[5] = (byte) (this.getQdcount() & 0xFF);
        header[6] = (byte) ((this.getAncount() >> 8) & 0xFF);
        header[7] = (byte) (this.getAncount() & 0xFF);
        header[8] = (byte) ((this.getNscount() >> 8) & 0xFF);
        header[9] = (byte) (this.getNscount() & 0xFF);
        // increase the Arcount if an OPT record exists
        int arcount = this.getArcount() + (this.getEdnsRecord() != null ? 1 : 0);
        header[10] = (byte) ((arcount >> 8) & 0xFF);
        header[11] = (byte) (arcount & 0xFF);

        // serialize each section
        byte[][] questions = new byte[this.getQuestionSections().length][];
        for (int i = 0; i < this.getQuestionSections().length; i++) {
            questions[i] = this.getQuestionSections()[i].toBytes();
        }

        byte[][] answers = new byte[this.getAnswerSections().length][];
        for (int i = 0; i < this.getAnswerSections().length; i++) {
            answers[i] = this.getAnswerSections()[i].toBytes();
        }

        byte[][] authorities = new byte[this.getAuthoritySections().length][];
        for (int i = 0; i < authorities.length; i++) {
            authorities[i] = this.getAuthoritySections()[i].toBytes();
        }

        byte[][] additionals = new byte[this.getAdditionalSections().length][];
        for (int i = 0; i < additionals.length; i++) {
            additionals[i] = this.getAdditionalSections()[i].toBytes();
        }

        byte[] ednsRecordBytes =
                this.getEdnsRecord() != null ? this.getEdnsRecord().toBytes() : null;

        // calculate total size
        int totalSize = header.length;
        for (byte[] q : questions) totalSize += q.length;
        for (byte[] a : answers) totalSize += a.length;
        for (byte[] ns : authorities) totalSize += ns.length;
        for (byte[] ar : additionals) totalSize += ar.length;
        if (ednsRecordBytes != null) totalSize += ednsRecordBytes.length;

        // copy everything into result
        byte[] result = new byte[totalSize];
        int pos = 0;

        System.arraycopy(header, 0, result, pos, header.length);
        pos += header.length;

        for (byte[] q : questions) {
            System.arraycopy(q, 0, result, pos, q.length);
            pos += q.length;
        }

        for (byte[] a : answers) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        for (byte[] ns : authorities) {
            System.arraycopy(ns, 0, result, pos, ns.length);
            pos += ns.length;
        }
        for (byte[] ar : additionals) {
            System.arraycopy(ar, 0, result, pos, ar.length);
            pos += ar.length;
        }
        if (ednsRecordBytes != null) {
            System.arraycopy(ednsRecordBytes, 0, result, pos, ednsRecordBytes.length);
            pos += ednsRecordBytes.length;
        }
        return result;
    }

    /**
     * Deserializes a complete DNS packet from DNS wire format. Splits the byte array into header and individual
     * sections and parses them individually
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new DnsPacket populated with all parsed fields
     */
    public static DNSPacket fromBytes(byte[] bytes) {
        if (bytes.length <= 12) {
            throw new IllegalArgumentException(
                    "Invalid form body. DNS Packets require at least 12 bytes for the Header");
        }
        DNSPacket dnsPacket = new DNSPacket();
        dnsPacket.setId(new byte[] {bytes[0], bytes[1]});

        dnsPacket.setQr(DNSParser.extractBit(bytes[2], 0));
        dnsPacket.setOpcode(DNSParser.subBits(bytes[2], 1, 5));
        dnsPacket.setAa(DNSParser.extractBit(bytes[2], 5));
        dnsPacket.setTc(DNSParser.extractBit(bytes[2], 6));
        dnsPacket.setRd(DNSParser.extractBit(bytes[2], 7));

        dnsPacket.setRa(DNSParser.extractBit(bytes[3], 0));
        dnsPacket.setZ(DNSParser.subBits(bytes[3], 1, 4));
        /// dnsPacket.setRcode(DNSParser.subBits(bytes[3], 4, 7));
        dnsPacket.setRcode(bytes[3] & 0x0F);

        dnsPacket.setQdcount(DNSParser.as16BitInt(bytes[4], bytes[5]));
        dnsPacket.setAncount(DNSParser.as16BitInt(bytes[6], bytes[7]));
        dnsPacket.setNscount(DNSParser.as16BitInt(bytes[8], bytes[9]));
        dnsPacket.setArcount(DNSParser.as16BitInt(bytes[10], bytes[11]));

        int byte_index = 12;
        dnsPacket.setQuestionSections(new QuestionSection[dnsPacket.getQdcount()]);
        for (int i = 0; i < dnsPacket.getQdcount(); i++) {
            dnsPacket.getQuestionSections()[i] = QuestionSection.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getQuestionSection(i).getLastProcessedByte();
        }
        dnsPacket.setAnswerSections(new ResourceRecord[dnsPacket.getAncount()]);
        for (int i = 0; i < dnsPacket.getAncount(); i++) {
            dnsPacket.getAnswerSections()[i] = ResourceRecord.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getAnswerSection(i).getLastProcessedByte();
        }
        dnsPacket.setAuthoritySections(new ResourceRecord[dnsPacket.getNscount()]);
        for (int i = 0; i < dnsPacket.getNscount(); i++) {
            dnsPacket.getAuthoritySections()[i] = ResourceRecord.fromBytes(bytes, byte_index);
            byte_index = dnsPacket.getAuthoritySection(i).getLastProcessedByte();
        }

        dnsPacket.setAdditionalSections(new ResourceRecord[dnsPacket.getArcount()]);
        for (int i = 0; i < dnsPacket.getArcount(); i++) {
            ResourceRecord record = ResourceRecord.fromBytes(bytes, byte_index);
            if (record.getType() == RRType.OPT) {
                if (dnsPacket.getEdnsRecord() != null) {
                    throw new IllegalArgumentException(
                            "Invalid form body. DNS Packets may only have a maximum of 1 EDNS pseudo Record");
                }
                dnsPacket.setEdnsRecord(
                        (OPTPseudoResourceRecord) record); // Should not be forwarded or cached, so stored separately
            } else {
                dnsPacket.getAdditionalSections()[i] = record;
            }
            byte_index = record.getLastProcessedByte();
        }
        if (dnsPacket.ednsRecord != null) { // truncate additional sections by 1 element as ednsRecord is missing
            dnsPacket.setAdditionalSections(
                    Arrays.copyOf(dnsPacket.getAdditionalSections(), dnsPacket.getAdditionalSections().length - 1));
        }
        return dnsPacket;
    }

    // Helper function to encode a name
    public static byte[] encodeName(String[] name) throws IllegalArgumentException {
        int nameLength = 0;
        for (String value : name) {
            int labelLength = value.length();
            if (labelLength > 255) {
                throw new IllegalArgumentException(
                        "Malformatted labels. Must be <256 characters, including length, in size.");
            }
            nameLength += labelLength + 1;
        }
        byte[] result = new byte[nameLength + 1];
        int pos = 0;

        for (String label : name) {
            byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
            result[pos++] = (byte) labelBytes.length;
            System.arraycopy(labelBytes, 0, result, pos, labelBytes.length);
            pos += labelBytes.length;
        }
        result[pos++] = 0;
        return Arrays.copyOf(result, pos);
    }
}
