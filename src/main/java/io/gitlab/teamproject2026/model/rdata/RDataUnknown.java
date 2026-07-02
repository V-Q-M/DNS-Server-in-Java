package io.gitlab.teamproject2026.model.rdata;

import java.util.Arrays;

public record RDataUnknown(int type, byte[] data) implements RData {
    public RDataUnknown(int type, byte[] bytes, int byteIndex, int rdlength) {

        this(type, Arrays.copyOfRange(bytes, byteIndex, byteIndex + rdlength));
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public byte[] toBytes() {
        return data;
    }
}
