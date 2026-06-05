package io.gitlab.teamproject2026.config.options;

import java.util.List;

public record OptionSection(
        String dir,
        int port,
        String ipAddress,
        boolean recursionDesired,
        String forwardMode,
        List<String> forwarders,
        ResolverSection resolverSection
) {
}