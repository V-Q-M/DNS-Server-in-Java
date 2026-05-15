package io.gitlab.teamproject2026;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.gitlab.teamproject2026.DNSCache;


public class DNSCacheTest {
    @Test
    void cacheConnectionTest(){
        DNSCache cache = new DNSCache();
        assertTrue(cache.connect());
    }
}
