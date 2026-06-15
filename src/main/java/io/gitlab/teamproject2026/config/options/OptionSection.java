package io.gitlab.teamproject2026.config.options;

import io.gitlab.teamproject2026.resolver.StubResolverStrategy;

import java.util.List;

/**
 * Immutable representation of the server's global configuration options,
 * including networking, forwarding behavior, and resolver settings.
 */
public record OptionSection(
        String dir,
        int port,
        String ipAddress,
        boolean recursionDesired,
        String forwardMode,
        List<String> forwarders,
        ResolverSection resolverSection
) {
    public static OptionSection defaultOptions() {
        ResolverSection resolver = new ResolverSection(
                2000,
                2,
                100,
                StubResolverStrategy.FAILOVER
        );

        return new OptionSection(
                "",
                4242,
                "0.0.0.0",
                false,
                "forward-only",
                List.of("1.1.1.1", "8.8.8.8"),
                resolver
        );
    }
}