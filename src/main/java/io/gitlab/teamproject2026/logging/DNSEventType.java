package io.gitlab.teamproject2026.logging;

public enum DNSEventType {
    QUERY_SENT,
    SUCCESS,
    TIMEOUT,
    IO_ERROR,
    ATTEMPTS_EXHAUSTED,
    CACHE_ERROR,
    CACHE_HIT,
    CACHE_MISS,
    // die neue werte
    QUERY_RECEIVED,
    ZONE_HIT,
    ZONE_MISS,
    NXDOMAIN,
    REFUSED,
    RESPONSE_SENT,
}
