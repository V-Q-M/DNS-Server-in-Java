package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.rdata.RData;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;
/**
 * Represents the Answer Section of a DNS packet.
 * Contains the domain name, class, TTL, data length and the actual resource record data.
 */
public class ResourceRecord {

    protected String[] name;
    //protected RRType type;
    protected RRClass rrclass;
    protected long ttl;
    //protected int rdlength;
    protected RData rdata;
    protected int lastProcessedByte;

    public ResourceRecord(){}
    public ResourceRecord(String[] name, RRClass rrclass, long ttl, RData rdata){
        this.name = name;
        this.rrclass = rrclass;
        this.ttl = ttl;
        this.rdata = rdata;
    }

    public String[] getName() { return name; }
    public void setName(String[] name) { this.name = name; }

    //public RRType getType() { return type; }
    // Derive the type from the actual rdata type instead of setting it manually
    public RRType getType() {
        return this.rdata == null ? null : this.rdata.getType();
    }

    //public void setType(RRType type) { this.type = type; }

    public RRClass getRrclass() { return rrclass; }
    public void setRrclass(RRClass rrclass) { this.rrclass = rrclass; }

    public long getTtl() { return ttl; }
    public void setTtl(long ttl) { this.ttl = ttl; }

    //public int getRdlength() { return rdlength; }

    // Derive the rdlength from the actual length of rdata instead of setting it manually
    public int getRdlength() { return this.rdata == null ? 0 : this.rdata.toBytes().length; }

    /*
    public void setRdlength(int rdlength) {this.rdlength = rdlength;}
    public void setRdlength(int rdlength, byte[] bytes, int byteIndex) { 
        if (type == RRType.SOA || type == RRType.NS || type == RRType.MX || type == RRType.CNAME || type == RRType.PTR){
            if (this.type == RRType.SOA){
                int rnameIndex = byteIndex + DNSParser.countNameLength(bytes, byteIndex);
                 this.rdlength = DNSParser.countNameLengthDecomp(bytes, byteIndex) + DNSParser.countNameLengthDecomp(bytes, rnameIndex) + 20; // mname.length + rname.length + 20 bytes (serial,...:fixed length)
            }
            else if (this.type == RRType.MX){
                 this.rdlength = DNSParser.countNameLengthDecomp(bytes, byteIndex+2) +2; // first 2 bytes: preference + mxdomainname.length
            }
            else { this.rdlength = DNSParser.countNameLengthDecomp(bytes, byteIndex);}
        }
        else { this.rdlength = rdlength;}
    }
     */

    public RData getRdata() { return rdata; }
    public void setRdata(RData rdata) { this.rdata = rdata; }

    /**
     * Only curated if parsed from bytes and used by the surrounding packet to adjust the parsing offset
     * @return amount of bytes used to parse this ResourceRecord
     */
    public int getLastProcessedByte() { return lastProcessedByte; }

    @Override
    public String toString() {
        return "\n    name: " + String.join(".", this.name) + "." + "\n"
                + "    rrtype: " + rdata.getType() + "\n"
                + "    rrclass: " + rrclass + "\n"
                + "    ttl: " + ttl + "s\n"
                + "    rdlength: " + rdata.toBytes().length + "\n"
                + "    rdata: " + rdata + "\n";
    }
    /**
     * Serializes this answer section into DNS wire format.
     *
     * @return byte array representing this answer section
     */
    public byte[] toBytes() {
        byte[] name  = encodeName(this.name);
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
        String[] labels = DNSParser.parseName(bytes, byteIndex);
        byteIndex += DNSParser.countNameLength(bytes, byteIndex);
        //resourceRecord.setName(labels);
        RRType type = RRType.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]));
        if(type == RRType.OPT){
            return new OPTPseudoResourceRecord(bytes, byteIndex);
        }
        RRClass rrclass = RRClass.fromValue(DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]));
        long ttl = DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        int rdlengthComp = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);
        RData rdata = RData.fromBytes(bytes, byteIndex, rdlengthComp, type);

        ResourceRecord resourceRecord = new ResourceRecord(labels, rrclass, ttl, rdata);
        //resourceRecord.setRdlength(rdlengthComp, bytes, byteIndex);
        resourceRecord.lastProcessedByte = byteIndex + rdlengthComp;
        return resourceRecord;
    }
}