package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS TXT record.
 * Stores arbitrary text data associated with a domain.
 */
public class ResourceRecordTXT implements ResourceRecord {
    public static final int TYPE = 16;
    
    private String txtdata;

    public String getTxtdata() { return txtdata; }
    public void setTxtdata(String txtdata) { this.txtdata = txtdata; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecordTXT from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}