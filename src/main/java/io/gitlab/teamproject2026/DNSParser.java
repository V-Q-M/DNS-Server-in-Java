package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class DNSParser{
    /**
     * Deserializes a complete DNS packet from DNS wire format.
     * Splits the byte array into header and individual sections
     * and parses them individually
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new DnsPacket populated with all parsed fields
     */
    public static DNSPacket parseBytes(byte[] bytes){
                /*
        for (byte bte : bytes){
            System.out.println(Integer.toBinaryString(bte & 0xFF));
        }*/
        return DNSPacket.fromBytes(bytes);
    }

    /**
     * Uses bitwise logic to only read the single specified bit and converts it to a Boolean
     * @param source The byte to extract the bit from
     * @param index Integer from 0-7 with 0 representing the msb and 7 being the lsb
     * @return The indexed bit as Boolean value
     */
    public static Boolean extractBit(byte source, int index){
        return (source >> 7-index & 1) == 1;
    }

    /**
     * Extracts the specified sequence of bits into the least significant bits of a new byte
     * @param source The original byte
     * @param from The starting index (inclusive)
     * @param to The end index (exclusive)
     * @return A new byte, only containing the masked bits shifted all the way to the right.
     */
    public static byte subBits(byte source, int from, int to){
        return (byte) ((source >> 8-to) & (byte) Math.pow(2, to-from)-1);
    }

    public static int as16BitInt(byte byte1, byte byte2){
        return (((byte1 & 0xFF) << 8) + (byte2 & 0xFF)) & 0xFFFF;
    }
    public static int as32BitInt(byte byte1, byte byte2, byte byte3, byte byte4){
        return (((byte1 & 0xFF) << 24) + ((byte1 & 0xFF) << 16) + ((byte1 & 0xFF) << 8) + (byte2 & 0xFF));
    }

    public static String[] parseName(byte[] bytes, int byteIndex){
        LinkedList<String> labels = new LinkedList<>();
        while (bytes[byteIndex] != 0) {
            int labelLength = bytes[byteIndex++] & 0xFF;
            labels.add(new String(Arrays.copyOfRange(bytes, byteIndex, byteIndex + labelLength)));
            byteIndex += labelLength;
        }
        return labels.toArray(new String[0]);
    }
    public static int countNameLength(byte[] bytes, int byteIndex){
        while (bytes[byteIndex] != 0) {
            byteIndex += bytes[byteIndex++] & 0xFF;
        }
        return byteIndex;
    }
}