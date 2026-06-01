package io.gitlab.teamproject2026.zones;

public enum ZoneTokenType {
    ILLEGAL,
    EOF,

    // Identifiers
    IDENT,
    NUMBER,
    ATOM,
    IPV4_ADDRESS,
    IPV6_ADDRESS,
    TXT,

    // Delimiter
    SEMICOLON,
    LPAREN,
    RPAREN,
    NEWLINE,

    // Keywords
    ORIGIN,
    TTL,
    INCLUDE,
    CLASS,
    A_RECORD,
    AAAA_RECORD,
    NS_RECORD,
    MX_RECORD,
    CNAME_RECORD,
    TXT_RECORD,
    SOA_RECORD,
    PTR_RECORD,
    // DNSSEC values
    DNSKEY_RECORD,
    DS_RECORD,
    RRSIG_RECORD,
    NSEC_RECORD,
    NSEC3_RECORD,
    NSEC3PARAM_RECORD,
}
