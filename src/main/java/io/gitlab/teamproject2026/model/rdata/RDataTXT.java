package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Represents a DNS TXT record. Stores arbitrary text data associated with a domain. */
public final class RDataTXT implements RData {
    private String[] txtdata;

    public RDataTXT() {}

    public RDataTXT(String... txtdata) {
        this.txtdata = txtdata;
    }

    public String[] getTxtdata() {
        return txtdata;
    }

    public void setTxtdata(String... txtdata) {
        this.txtdata = txtdata;
    }

    @Override
    public int getType() {
        return RRType.TXT;
    }

    @Override
    public String toString() {
        return String.join(", ", txtdata);
    }

    @Override
    public byte[] toBytes() {
        int totalLength = 0;

        for (String txt : txtdata) {
            byte[] data = txt.getBytes(StandardCharsets.UTF_8);
            if (data.length > 255) {
                throw new IllegalArgumentException("TXT string too long");
            }
            totalLength += 1 + data.length;
        }

        byte[] result = new byte[totalLength];
        int pos = 0;

        for (String txt : txtdata) {
            byte[] data = txt.getBytes(StandardCharsets.UTF_8);
            result[pos++] = (byte) data.length;
            System.arraycopy(data, 0, result, pos, data.length);
            pos += data.length;
        }

        return result;
    }

    public static RDataTXT fromBytes(byte[] bytes, int byteIndex, int rdlength) throws IllegalArgumentException {
        int end = byteIndex + rdlength;
        List<String> chunks = new ArrayList<>();

        int pos = byteIndex;
        while (pos < end) {
            int length = bytes[pos] & 0xFF;
            pos++;
            if (pos + length > end) {
                throw new IllegalArgumentException("TXT chunk exceeds available data");
            }
            chunks.add(new String(bytes, pos, length, StandardCharsets.UTF_8));
            pos += length;
        }
        return new RDataTXT(chunks.toArray(new String[0]));
    }
}
