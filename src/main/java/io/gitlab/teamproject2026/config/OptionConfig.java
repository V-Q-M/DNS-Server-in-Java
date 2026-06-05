package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.StubResolver;

import java.util.List;

public record OptionConfig(
        String dir,
        int port,
        String ipAddress,
        boolean recursionDesired,
        String forwardMode,
        List<String> forwarders,
        int resolverTimeout,
        int resolverMaxRetries,
        long resolverRetryDelay,
        StubResolver.Strategy resolverStrategy
) {
}