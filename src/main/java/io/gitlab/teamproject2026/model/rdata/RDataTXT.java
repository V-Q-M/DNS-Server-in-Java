package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

/**
 * Represents a DNS TXT record.
 * Stores arbitrary text data associated with a domain.
 */
public class RDataTXT implements RData {
    private String[] txtdata;

    public RDataTXT(){}
    public RDataTXT(String... txtdata){
        this.txtdata = txtdata;
    }

    public String[] getTxtdata() { return txtdata; }
    public void setTxtdata(String... txtdata) { this.txtdata = txtdata; }

    @Override
    public RRType getType() {
        return RRType.TXT;
    }

    @Override
    public String toString(){
        return String.join(", ", txtdata);
    }

    @Override
    public byte[] toBytes() {
        return encodeName(txtdata);
    }

    public static RDataTXT fromBytes(byte[] bytes, int byteIndex) {
        return new RDataTXT(DNSParser.parseName(bytes, byteIndex));
    }
}