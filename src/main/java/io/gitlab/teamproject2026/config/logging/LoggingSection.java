package io.gitlab.teamproject2026.config.logging;

import java.util.List;
import java.util.Map;

public record LoggingSection(
        List<LoggingChannel> channels,
        Map<LoggingCategoryType, LoggingCategory> categories
) {}