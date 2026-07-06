package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class RDataOPTOption implements RData {
    private Map<Integer, byte[]> options = new HashMap<>();

    public RDataOPTOption() {}

    public RDataOPTOption(Map<Integer, byte[]> options) {
        this.options = options;
    }

    public RDataOPTOption(byte[] bytes, int byteIndex, int rdlength) {
        while (rdlength > 0) {
            int optionCode = bytes[byteIndex++];
            int optionLength = bytes[byteIndex++];
            options.put(optionCode, Arrays.copyOfRange(bytes, byteIndex, byteIndex + optionLength));
            rdlength -= optionLength + 2;
        }
    }

    public Map<Integer, byte[]> getOptions() {
        return options;
    }

    public byte[] getOption(Integer code) {
        return options.get(code);
    }

    public void setOptions(Map<Integer, byte[]> options) {
        this.options = options;
    }

    @Override
    public int getType() {
        return RRType.OPT;
    }

    @Override
    public byte[] toBytes() {
        int rdlength = 0;
        for (byte[] rdata : this.options.values()) {
            rdlength += rdata.length + 2;
        }
        byte[] result = new byte[rdlength];
        int byteIndex = 0;
        for (Map.Entry<Integer, byte[]> option : options.entrySet()) {
            result[byteIndex++] = option.getKey().byteValue();
            int optionLength = option.getValue().length;
            result[byteIndex++] = (byte) optionLength;
            System.arraycopy(option.getValue(), 0, result, byteIndex, optionLength);
            byteIndex += optionLength;
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, byte[]> option : options.entrySet()) {
            result.append(option.getKey())
                    .append(": ")
                    .append(Arrays.toString(option.getValue()))
                    .append("\n");
        }
        return result.toString();
    }
}
