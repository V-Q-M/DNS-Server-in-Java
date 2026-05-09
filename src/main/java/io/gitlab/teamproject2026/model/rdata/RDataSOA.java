package io.gitlab.teamproject2026.model.rdata;

/**
 * Represents a DNS SOA (Start of Authority) record.
 * Contains administrative information about a DNS zone.
 * Includes primary nameserver, responsible email, and zone serial number.
 */
public class RDataSOA implements RData {
    public static final int TYPE = 6;
    
    private String mname;
    private String rname;
    private long serial;
    private long refresh;
    private long retry;
    private long expire;
    private long minimum;

    public String getMname() { return mname; }
    public void setMname(String mname) { this.mname = mname; }

    public String getRname() { return rname; }
    public void setRname(String rname) { this.rname = rname; }

    public long getSerial() { return serial; }
    public void setSerial(long serial) { this.serial = serial; }

    public long getRefresh() { return refresh; }
    public void setRefresh(long refresh) { this.refresh = refresh; }

    public long getRetry() { return retry; }
    public void setRetry(long retry) { this.retry = retry; }

    public long getExpire() { return expire; }
    public void setExpire(long expire) { this.expire = expire; }

    public long getMinimum() { return minimum; }
    public void setMinimum(long minimum) { this.minimum = minimum; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static RDataSOA from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}