package io.gitlab.teamproject2026.config.logging;

import java.util.List;

/**
 * Represents a logging category defined in the {@code logging} section.
 * <p>
 * A category groups related log events (e.g. {@code QUERIES} or
 * {@code CONFIG}) and specifies the channels to which messages of that
 * category should be written.
 *
 * @param type the logging category
 * @param channels the logging channels that receive messages belonging to
 *                 this category
 */
public record LoggingCategory(
        LoggingCategoryType type,
        List<LoggingChannel> channels
) {}