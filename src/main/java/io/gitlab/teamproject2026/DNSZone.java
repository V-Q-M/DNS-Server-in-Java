package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.ResourceRecord;
import java.util.List;

/**
 * Immutable representation of a DNS zone, including its origin, default TTL, SOA refresh information, and contained
 * resource records.
 */
public record DNSZone(String origin, long defaultTtl, long serial, long refresh, List<ResourceRecord> records) {

    @Override
    public String toString() {
        return "Zone{" + "origin='" + origin + '\'' + ", records=" + records + '}';
    }

    public String getOrigin() {
        return origin;
    }

    public long getDefaultTTL() {
        return defaultTtl;
    }

    public List<ResourceRecord> getRecords() {
        return records;
    }
}
