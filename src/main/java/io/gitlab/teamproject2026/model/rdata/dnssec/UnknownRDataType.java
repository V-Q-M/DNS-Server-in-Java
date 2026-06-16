package io.gitlab.teamproject2026.model.rdata.dnssec;

import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.RData;

import java.util.Arrays;

public record UnknownRDataType(byte[] data, int byteIndex) implements RData {
    public byte[] getData() { return data; }

    @Override
    public RRType getType(){
        return RRType.UNKNOWN;
    }

    @Override
    public String toString() { return Arrays.toString(data);}

    @Override
    public byte[] toBytes() { return data; }
}
