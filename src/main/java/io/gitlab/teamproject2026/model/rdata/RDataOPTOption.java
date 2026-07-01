package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;
import java.util.Arrays;

public class RDataOPTOption implements RData {
    private int optionCode;
    private byte[] optionData;

    public RDataOPTOption(byte[] bytes, int byteIndex) {
        this.optionCode = bytes[byteIndex++];
        int optionLength = bytes[byteIndex++];
        this.optionData = Arrays.copyOfRange(bytes, byteIndex, byteIndex + optionLength);
    }

    @Override
    public RRType getType() {
        return RRType.OPT;
    }

    @Override
    public byte[] toBytes() {
        byte[] result = new byte[2 + optionData.length];
        result[0] = (byte) optionCode;
        result[1] = (byte) optionData.length;
        System.arraycopy(optionData, 0, result, 2, optionData.length);
        return result;
    }
}
