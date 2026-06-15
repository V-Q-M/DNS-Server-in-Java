package io.gitlab.teamproject2026.config.zones;

/**
 * Immutable configuration describing a DNS zone declaration, including
 * its domain name, backing zone file, and operational type.
 */
public record ZoneSection(String name, String file, ZoneType type) {

}
