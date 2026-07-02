package io.gitlab.teamproject2026.model.rdata;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

/**
 * Represents a DNS SOA (Start of Authority) record. Contains administrative information about a DNS zone. Includes
 * primary nameserver, responsible email, and zone serial number.
 */
public class RDataSOA implements RData {
    private String[] mname;
    private String[] rname;
    private long serial;
    private long refresh;
    private long retry;
    private long expire;
    private long minimum;

    public RDataSOA() {}

    public RDataSOA(String[] mname, String[] rname, long serial, long refresh, long retry, long expire, long minimum) {
        this.mname = mname;
        this.rname = rname;
        this.serial = serial;
        this.refresh = refresh;
        this.retry = retry;
        this.expire = expire;
        this.minimum = minimum;
    }

    @Override
    public int getType() {
        return RRType.SOA;
    }

    public String[] getMname() {
        return mname;
    }

    public void setMname(String[] mname) {
        this.mname = mname;
    }

    public String[] getRname() {
        return rname;
    }

    public void setRname(String[] rname) {
        this.rname = rname;
    }

    public long getSerial() {
        return serial;
    }

    public void setSerial(long serial) {
        this.serial = serial;
    }

    public long getRefresh() {
        return refresh;
    }

    public void setRefresh(long refresh) {
        this.refresh = refresh;
    }

    public long getRetry() {
        return retry;
    }

    public void setRetry(long retry) {
        this.retry = retry;
    }

    public long getExpire() {
        return expire;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    public long getMinimum() {
        return minimum;
    }

    public void setMinimum(long minimum) {
        this.minimum = minimum;
    }

    @Override
    public String toString() {
        return "    mname: " + String.join(".", mname) + "." + "\n    rname: " + String.join(".", rname) + "."
                + "\n    serial: " + serial + ", refresh: " + refresh + ", retry: " + retry + ", expire: " + expire
                + ", minimum: " + minimum;
    }

    @Override
    public byte[] toBytes() {
        byte[] mname = encodeName(this.getMname());
        byte[] rname = encodeName(this.getRname());
        byte[] result = new byte[mname.length + rname.length + 20]; // 5 x 4-byte ints
        int pos = 0;

        System.arraycopy(mname, 0, result, pos, mname.length);
        pos += mname.length;
        System.arraycopy(rname, 0, result, pos, rname.length);
        pos += rname.length;

        result[pos] = (byte) (serial >> 24);
        result[pos + 1] = (byte) (serial >> 16);
        result[pos + 2] = (byte) (serial >> 8);
        result[pos + 3] = (byte) (serial);
        pos += 4;
        result[pos] = (byte) (refresh >> 24);
        result[pos + 1] = (byte) (refresh >> 16);
        result[pos + 2] = (byte) (refresh >> 8);
        result[pos + 3] = (byte) (refresh);
        pos += 4;
        result[pos] = (byte) (retry >> 24);
        result[pos + 1] = (byte) (retry >> 16);
        result[pos + 2] = (byte) (retry >> 8);
        result[pos + 3] = (byte) (retry);
        pos += 4;
        result[pos] = (byte) (expire >> 24);
        result[pos + 1] = (byte) (expire >> 16);
        result[pos + 2] = (byte) (expire >> 8);
        result[pos + 3] = (byte) (expire);
        pos += 4;
        result[pos] = (byte) (minimum >> 24);
        result[pos + 1] = (byte) (minimum >> 16);
        result[pos + 2] = (byte) (minimum >> 8);
        result[pos + 3] = (byte) (minimum);
        pos += 4;

        return result;
    }

    public static RDataSOA fromBytes(byte[] bytes, int byteIndex) {
        String[] mname = DNSParser.parseName(bytes, byteIndex);
        byteIndex += DNSParser.countNameLength(bytes, byteIndex);

        String[] rname = DNSParser.parseName(bytes, byteIndex);
        byteIndex += DNSParser.countNameLength(bytes, byteIndex);

        long serial =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        long refresh =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        long retry =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        long expire =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        long minimum =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);

        return new RDataSOA(mname, rname, serial, refresh, retry, expire, minimum);
    }
}
