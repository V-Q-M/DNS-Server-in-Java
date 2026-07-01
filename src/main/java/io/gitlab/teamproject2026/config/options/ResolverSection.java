package io.gitlab.teamproject2026.config.options;

import io.gitlab.teamproject2026.resolver.StubResolverStrategy;

/**
 * Immutable configuration for the stub resolver, defining retry behavior, timeouts, and the strategy used when
 * contacting upstream servers.
 */
public record ResolverSection(int timeout, int maxRetries, long retryDelay, StubResolverStrategy strategy) {}
