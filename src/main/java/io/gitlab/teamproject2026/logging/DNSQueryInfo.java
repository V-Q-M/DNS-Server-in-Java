package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.RRType;
public record DNSQueryInfo(
    String id,
    String domain,
    RRType qType
) {}