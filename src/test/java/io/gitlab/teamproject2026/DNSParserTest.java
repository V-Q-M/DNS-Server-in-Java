package io.gitlab.teamproject2026;

import org.junit.jupiter.api.Test;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

public class DNSParserTest {
    @Test
    void subBitTest(){
        assertEquals(0b110, DNSParser.subBits((byte) 0b10110110, 2, 5));
        assertEquals(0b10110, DNSParser.subBits((byte) 0b10110110, 0, 5));
        assertEquals(0b110, DNSParser.subBits((byte) 0b10110110, 5, 8));
        assertEquals(0b00000, DNSParser.subBits((byte) 0b00000000, 2, 7));
    }
    @Test
    void extractBitTest(){
        assertEquals(true, DNSParser.extractBit((byte) 0b10110110, 2));
        assertEquals(false, DNSParser.extractBit((byte) 0b10110110, 1));
        assertEquals(true, DNSParser.extractBit((byte) 0b10110110, 5));
        assertEquals(false, DNSParser.extractBit((byte) 0b10110110, 7));

    }
    @Test
    void as16BitIntTest(){
        assertEquals(5, DNSParser.as16BitInt((byte) 0x00, (byte) 0x05));
        assertEquals(16197, DNSParser.as16BitInt((byte) 0x3F, (byte) 0x45));
        assertEquals(65535, DNSParser.as16BitInt((byte) 0xFF, (byte) 0xFF));
    }
    @Test
    void as32BitIntTest(){
        assertEquals(328960, DNSParser.as32BitInt((byte) 0x00, (byte) 0x05, (byte) 0x05, (byte) 0x00));
        assertEquals(4294967295L, DNSParser.as32BitInt((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF));
        assertEquals(4278519043L, DNSParser.as32BitInt((byte) 0xFF, (byte) 0x05, (byte) 0x05, (byte) 0x03));
    }
    @Test
    void parseNameTest(){
        assertArrayEquals(new String[] {"uni-tuebingen", "de"}, DNSParser.parseName(HexFormat.of().parseHex("0d756e692d74756562696e67656e02646500"), 0));
    }
}
