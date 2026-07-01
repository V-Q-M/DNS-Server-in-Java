package io.gitlab.teamproject2026.logging;

public enum ResponseSource {
    ZONE,
    CACHE,
    FORWARDER,
    // currently not used, instead always forwarder but exchange/create logs for those at later point
    RECURSIVE_RESOLVER,
    STUB
}
