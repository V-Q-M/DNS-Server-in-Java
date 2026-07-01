package io.gitlab.teamproject2026.zones;

import java.util.Map;

/**
 * Immutable representation of a token produced by the zone file lexer, including its type, literal value, and source
 * line number.
 */
public record ZoneToken(ZoneTokenType type, String literal, int lineNumber) {
    public ZoneToken(ZoneTokenType type, String literal) {
        this(type, literal, 0);
    }

    public ZoneToken(ZoneTokenType type, char ch) {
        this(type, String.valueOf(ch), 0);
    }

    public ZoneToken(ZoneTokenType type, char ch, int lineNumber) {
        this(type, String.valueOf(ch), lineNumber);
    }

    /**
     * Mapping of known DNS directives, classes, and RR type mnemonics to their corresponding token types. Identifiers
     * not present in this map are treated as generic ATOM tokens.
     */
    private static final Map<String, ZoneTokenType> KEYWORDS = Map.ofEntries(
            Map.entry("$ORIGIN", ZoneTokenType.ORIGIN),
            Map.entry("$TTL", ZoneTokenType.TTL),
            Map.entry("$INCLUDE", ZoneTokenType.INCLUDE),
            Map.entry("IN", ZoneTokenType.CLASS),
            Map.entry("A", ZoneTokenType.A_RECORD),
            Map.entry("AAAA", ZoneTokenType.AAAA_RECORD),
            Map.entry("NS", ZoneTokenType.NS_RECORD),
            Map.entry("MX", ZoneTokenType.MX_RECORD),
            Map.entry("CNAME", ZoneTokenType.CNAME_RECORD),
            Map.entry("TXT", ZoneTokenType.TXT_RECORD),
            Map.entry("SOA", ZoneTokenType.SOA_RECORD),
            Map.entry("PTR", ZoneTokenType.PTR_RECORD),
            // DNSSEC values
            Map.entry("DNSKEY", ZoneTokenType.DNSKEY_RECORD),
            Map.entry("DS", ZoneTokenType.DS_RECORD),
            Map.entry("RRSIG", ZoneTokenType.RRSIG_RECORD),
            Map.entry("NSEC", ZoneTokenType.NSEC_RECORD),
            Map.entry("NSEC3", ZoneTokenType.NSEC3_RECORD),
            Map.entry("NSEC3PARAM", ZoneTokenType.NSEC3PARAM_RECORD));

    /**
     * Resolves a zone file identifier to its corresponding token type. Matching is case-insensitive; unknown
     * identifiers are treated as {@link ZoneTokenType#ATOM}.
     *
     * @param identifier identifier text from the lexer
     * @return the matching {@link ZoneTokenType}
     */
    public static ZoneTokenType lookUpIdentifier(String identifier) {
        return KEYWORDS.getOrDefault(identifier.toUpperCase(), ZoneTokenType.ATOM);
    }
}
