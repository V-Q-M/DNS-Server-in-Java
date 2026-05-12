package io.gitlab.teamproject2026.model.rdata;

import java.nio.charset.StandardCharsets;

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
    public String toString(){
        return txtdata;
    }

    @Override
    public byte[] toBytes() {
        return txtdata.getBytes(StandardCharsets.US_ASCII);
    }

    public static RDataTXT fromBytes(byte[] bytes) {
        RDataTXT rDataTXT = new RDataTXT();
        rDataTXT.setTxtdata(new String(bytes));
        return rDataTXT;
    }
}