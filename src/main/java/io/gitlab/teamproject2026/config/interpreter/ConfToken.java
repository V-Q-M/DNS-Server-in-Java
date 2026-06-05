package io.gitlab.teamproject2026.config.interpreter;

import java.util.Map;

public record ConfToken(ConfTokenType type, String literal, int lineNumber) {
    public ConfToken(ConfTokenType type, String literal) { this(type, literal, 0); }
    public ConfToken(ConfTokenType type, char ch, int lineNumber){ this(type, String.valueOf(ch), lineNumber); }
    public ConfToken(ConfTokenType type, char ch){ this(type, String.valueOf(ch), 0); }

    private static final Map<String, ConfTokenType> KEYWORDS = Map.ofEntries(
            Map.entry("option", ConfTokenType.ATOM),
            Map.entry("zone", ConfTokenType.ATOM),
            Map.entry("include", ConfTokenType.ATOM)
    );


    public static ConfTokenType lookUpIdentifier(String identifier) {
        return KEYWORDS.getOrDefault(identifier.toUpperCase(), ConfTokenType.ATOM);
    }
}
