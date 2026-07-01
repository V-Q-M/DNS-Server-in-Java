package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.ResourceRecord;
import java.util.LinkedList;

/**
 * Intermediate result used while resolving a DNS query, separating resolved records from questions that still require
 * further processing.
 */
public record DNSPayload(
        LinkedList<ResourceRecord> answer,
        LinkedList<ResourceRecord> authority,
        LinkedList<ResourceRecord> additional,
        LinkedList<QuestionSection> notResolved) {}
