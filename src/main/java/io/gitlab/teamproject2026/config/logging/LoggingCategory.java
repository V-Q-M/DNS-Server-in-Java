package io.gitlab.teamproject2026.config.logging;

import java.util.List;

public record LoggingCategory(
        LoggingCategoryType type,
        List<LoggingChannel> channels
) {}