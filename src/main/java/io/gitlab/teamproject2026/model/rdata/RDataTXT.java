package io.gitlab.teamproject2026.model.rdata;

/**
 * Represents a DNS TXT record.
 * Stores arbitrary text data associated with a domain.
 */
public class RDataTXT implements RData {
    public static final int TYPE = 16;

    private String txtdata;

    public String getTxtdata() { return txtdata; }
    public void setTxtdata(String txtdata) { this.txtdata = txtdata; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static RDataTXT fromBytes(byte[] bytes) {
        RDataTXT rDataTXT = new RDataTXT();
        rDataTXT.setTxtdata(new String(bytes));
        return rDataTXT;
    }
}