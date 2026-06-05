package io.gitlab.teamproject2026.config.interpreter;

public enum ConfTokenType {
    ILLEGAL,
    EOF,

    // Identifiers
    ATOM,
    STRING,
    NUMBER,
    IP_ADDRESS,

    // Delimiter
    SEMICOLON,
    LBRACE,
    RBRACE,

}
