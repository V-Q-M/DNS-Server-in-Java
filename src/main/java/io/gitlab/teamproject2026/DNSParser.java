package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class DNSParser{
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

    /**
     * Converts 2 bytes into a 16bit integer by concatenating them within the 16 lsb of the int.
     * @param byte1 The first byte (2^8 - 2^15)
     * @param byte2 The second byte (2^0 - 2^7)
     * @return an unsigned integer of the 2 bytes
     */
    public static int as16BitInt(byte byte1, byte byte2){
        return (((byte1 & 0xFF) << 8) + (byte2 & 0xFF)) & 0xFFFF;
    }
    /**
     * Converts 4 bytes into a 32bit integer by concatenating them.
     * @param byte1 The first byte (2^24 - 2^31)
     * @param byte2 The second byte (2^16 - 2^23)
     * @param byte3 The third byte (2^8 - 2^15)
     * @param byte4 The fourth byte (2^0 - 2^7)
     * @return an unsigned integer of the 4 bytes
     */
    public static long as32BitInt(byte byte1, byte byte2, byte byte3, byte byte4){
        return (((((long) byte1 & 0xFF) << 24) + (((long) byte2 & 0xFF) << 16) + (((long) byte3 & 0xFF) << 8) + ((long) byte4 & 0xFF)));
    }

    /**
     * Parses domain names by the [length]text convention
     * @param bytes the full array of bytes
     * @param byteIndex the index, where the domain name is supposed to start
     * @return an array of labels, sourced from the bytes
     */
    public static String[] parseName(byte[] bytes, int byteIndex){
        
        LinkedList<String> labels = new LinkedList<>();
        while (bytes[byteIndex] != 0) {
            if((bytes[byteIndex] & 0xFF) >= 32+64){ // Message compression, first 2 bits = 1
               byteIndex = goToPointer(bytes, byteIndex)
            }
            int labelLength = bytes[byteIndex++] & 0xFF;
            labels.add(new String(Arrays.copyOfRange(bytes, byteIndex, byteIndex + labelLength), StandardCharsets.US_ASCII));
            byteIndex += labelLength;
        }
        return labels.toArray(new String[0]);
    }

    /**
     * Finds the amount of bytes used within this domain name byte array
     * @param bytes the full array of bytes
     * @param startIndex the index, where the domain name is supposed to start
     * @return the amount of bytes until a length 0 byte is found.
     */
    public static int countNameLength(byte[] bytes, int startIndex){
        int byteIndex = startIndex;
        if((bytes[byteIndex] & 0xFF) >= 32+64){ // Message compression, first 2 bytes = 1
            return byteIndex - startIndex + 2;  //pointer always 2 bytes
        }
        while (bytes[byteIndex] != 0) {
            int labelLength = bytes[byteIndex++] & 0xFF;
            byteIndex += labelLength;
        }
        return byteIndex - startIndex + 1;
    }

    /**
     * resolves pointer to the location of the label(s) being pointed to
     * @param bytes the full array of bytes
     * @param pointerIndex the index, where the pointer starts
     */
    public static int goToPointer(bytes, pointerIndex){
        LinkedList<String> labels = new LinkedList<>();
        Boolean jumped = false;
        //nextOffse
        int countPointers = 0;
        while true {
            countPointers += 2
            if (countPointers >= 257){
                throw new IllegalArgumentException("Compression loop detected")  //name restricted to max 255 label octets including label length octets
            }
            if (pointerIndex >= bytes.length){
                throw new IndexOutOfBoundsException("pointer points out of bounds")
            }
            byte[] labelLengthOrPointer = bytes[pointerIndex]

            if (pointerIndex+1 >= bytes.length){
                throw new IndexOutOfBoundsException("pointer points out of bounds")
            }
            int pointerOffset = (0x3F & as16BitInt(byte1, bytes[pointerIndex+1])) //extract pointer offset
            if (pointerOffset >= bytes.length){
                throw new IndexOutOfBoundsException("pointer points out of bounds")
            }
            return pointerOffset
            

        }

    }

}