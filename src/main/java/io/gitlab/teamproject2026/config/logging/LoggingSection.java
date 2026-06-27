package io.gitlab.teamproject2026.config.logging;

import java.util.List;
import java.util.Map;

/**
 * Represents the complete {@code logging} section of the configuration.
 * <p>
 * Contains all configured logging channels as well as the mappings from
 * logging categories to their associated channels. The logger uses this
 * information to determine where messages should be written based on their
 * category and severity.
 *
 * @param channels the configured logging channels
 * @param categories the mappings from logging categories to their associated
 *                   logging channels
 */
public record LoggingSection(
        List<LoggingChannel> channels,
        Map<LoggingCategoryType, LoggingCategory> categories
) {}