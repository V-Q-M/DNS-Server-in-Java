package io.gitlab.teamproject2026.config.interpreter;

/**
 * Token types recognized by the configuration lexer, covering literals, structural delimiters, and special markers used
 * during parsing.
 */
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
