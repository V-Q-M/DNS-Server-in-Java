package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.ResourceRecord;

import java.util.List;

public record DNSZone(String origin, long defaultTtl, List<ResourceRecord> records) {

    @Override
    public String toString() {
        return "Zone{" +
                "origin='" + origin + '\'' +
                ", records=" + records +
                '}';
    }

    public String getOrigin() {
        return origin;
    }

    public long getDefaultTTL() { return defaultTtl; }

    public List<ResourceRecord> getRecords () {
        return records;
    }
}
