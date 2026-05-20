package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.rdata.RData;

import java.util.Arrays;
import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;
/**
 * Represents the Answer Section of a DNS packet.
 * Contains the domain name, class, TTL, data length and the actual resource record data.
 */
public class ResourceRecord {

    private String[] name;
    private RRType type;
    private RRClass rrclass;
    private long ttl;
    private int rdlength;
    private RData rdata;
    private int lastProcessedByte;

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    public RRType getType() { return type; }
    public void setType(RRType type) { this.type = type; }

    public RRClass getRrclass() { return rrclass; }
    public void setRrclass(RRClass rrclass) { this.rrclass = rrclass; }

    public long getTtl() { return ttl; }
    public void setTtl(long ttl) { this.ttl = ttl; }

    public int getRdlength() { return rdlength; }
    public void setRdlength(int rdlength) {this.rdlength = rdlength;}
    public void setRdlength(int rdlength, byte[] bytes, int byteIndex) { 
        if (type == RRType.SOA || type == RRType.NS || type == RRType.MX || type == RRType.CNAME){
            int decompPlus = DNSParser.countNameLengthDecomp(bytes, byteIndex) - DNSParser.countNameLength(bytes, byteIndex); // counts by how many bytes the RData section increased due to decompression
            if (this.type == RRType.SOA){
                int rnameIndex = byteIndex + DNSParser.countNameLength(bytes, byteIndex);
                decompPlus += DNSParser.countNameLengthDecomp(bytes, rnameIndex) - DNSParser.countNameLength(bytes, rnameIndex);
            }
            this.rdlength = rdlength + decompPlus; 
        }
        else { this.rdlength = rdlength;}
    }

    public RData getRdata() { return rdata; }
    public void setRdata(RData rdata) { this.rdata = rdata; }

    /**
     * Only curated if parsed from bytes and used by the surrounding packet to adjust the parsing offset
     * @return amount of bytes used to parse this ResourceRecord
     */
    public int getLastProcessedByte() { return lastProcessedByte; }

    @Override
    public String toString() {
        return "\n    name: " + Arrays.toString(name) + "\n"
                + "    rrtype: " + type + "\n"
                + "    rrclass: " + rrclass + "\n"
                + "    ttl: " + ttl + "s\n"
                + "    rdlength: " + rdlength + "\n"
                + "    rdata: " + rdata + "\n";
    }
    /**
     * Serializes this answer section into DNS wire format.
     *
     * @return byte array representing this answer section
     */
    public byte[] toBytes() {
        byte[] name  = encodeName(String.join(".", this.name));
        byte[] rdata = this.getRdata().toBytes();
        byte[] result = new byte[name.length + 10 + rdata.length];

        int pos = 0;
        System.arraycopy(name, 0, result, pos, name.length);
        pos += name.length;

        result[pos]     = (byte) (this.getType().getValue() >> 8);
        result[pos + 1] = (byte) (this.getType().getValue());
        pos += 2;

        result[pos]     = (byte) (this.getRrclass().getValue() >> 8);
        result[pos + 1] = (byte) (this.getRrclass().getValue());
        pos += 2;

        result[pos]     = (byte) (this.getTtl() >> 24);
        result[pos + 1] = (byte) (this.getTtl() >> 16);
        result[pos + 2] = (byte) (this.getTtl() >> 8);
        result[pos + 3] = (byte) (this.getTtl());
        pos += 4;

        result[pos]     = (byte) (rdata.length >> 8);  // rdlength
        result[pos + 1] = (byte) (rdata.length);
        pos += 2;

        System.arraycopy(rdata, 0, result, pos, rdata.length);

        return result;
    }

    /**
     * Deserializes an answer section from DNS wire format.
     *
     * @param bytes COMPLETE incoming DNS packet
     * @param byteIndex starting index of answer section
     * @return a new AnswerSection populated with the parsed data
     */
    public static ResourceRecord fromBytes(byte[] bytes, int byteIndex) {
        ResourceRecord resourceRecord = new ResourceRecord();
        String[] labels = DNSParser.parseName(bytes, byteIndex);
        byteIndex += DNSParser.countNameLength(bytes, byteIndex);
        resourceRecord.setName(labels);
        resourceRecord.setType(RRType.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++])));
        resourceRecord.setRrclass(RRClass.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++])));
        resourceRecord.setTtl(DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]));
        int rdlengthComp = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);
        resourceRecord.setRdata(RData.fromBytes(bytes, byteIndex, resourceRecord.getType()));
        resourceRecord.setRdlength(rdlengthComp, bytes, byteIndex);
        resourceRecord.lastProcessedByte = byteIndex + rdlengthComp;
        return resourceRecord;
    }
}