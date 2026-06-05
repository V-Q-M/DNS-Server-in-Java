package io.gitlab.teamproject2026.config.options;

import io.gitlab.teamproject2026.resolver.StubResolverStrategy;

public record ResolverSection(
        int timeout,
        int maxRetries,
        long retryDelay,
        StubResolverStrategy strategy
) {
}
