package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a DNS TXT record.
 * Stores arbitrary text data associated with a domain.
 */
public class RDataTXT implements RData {
    private String txtdata;

    public String getTxtdata() { return txtdata; }
    public void setTxtdata(String txtdata) { this.txtdata = txtdata; }

    @Override
    public RRType getType() {
        return RRType.TXT;
    }

    @Override
    public String toString(){
        return txtdata;
    }

    @Override
    public byte[] toBytes() {
        byte[] text = txtdata.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[1 + text.length];
        result[0] = (byte) text.length;
        System.arraycopy(text, 0, result, 1, text.length);
        return result;
    }

    public static RDataTXT fromBytes(byte[] bytes, int byteIndex) {
        RDataTXT rDataTXT = new RDataTXT();
        int txtLength = bytes[byteIndex] & 0xFF;
        rDataTXT.setTxtdata(new String(Arrays.copyOfRange(bytes, 1+byteIndex, 1+ byteIndex + txtLength), StandardCharsets.US_ASCII));
        return rDataTXT;
    }
}