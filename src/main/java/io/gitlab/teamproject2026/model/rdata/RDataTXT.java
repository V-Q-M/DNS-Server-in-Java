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
    public byte[] toBytes() {
        byte[] text = txtdata.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[1 + text.length];
        result[0] = (byte) text.length;
        System.arraycopy(text, 0, result, 1, text.length);
        return result;
    }

    public static RDataTXT fromBytes(byte[] bytes) {
        RDataTXT rDataTXT = new RDataTXT();
        rDataTXT.setTxtdata(new String(bytes));
        return rDataTXT;
    }
}