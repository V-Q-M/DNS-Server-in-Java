package io.gitlab.teamproject2026.config.interpreter;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the DNS server configuration language. Consumes tokens produced by {@link ConfLexer} and
 * builds a tree of {@link ConfNode}s representing the hierarchical configuration structure.
 */
public class ConfParser {
    private final ConfLexer lexer;
    private ConfToken current;

    private final List<ConfNode> rootNodes = new ArrayList<>();
    private boolean parserError = false;

    public ConfParser(String input) {
        this.lexer = new ConfLexer(input);
        this.current = lexer.nextToken();
        parseConfig();
    }

    private void parseConfig() {
        while (!check(ConfTokenType.EOF) && !parserError) {
            rootNodes.add(parseStatement());
        }
    }

    private ConfNode parseStatement() {
        ConfToken nameToken = consume(ConfTokenType.ATOM, "Expected statement name");
        if (parserError) {
            return new ConfNode("", List.of(), List.of(), nameToken.lineNumber());
        }

        String name = nameToken.literal();

        List<String> args = new ArrayList<>();
        List<ConfNode> children = new ArrayList<>();

        while (!check(ConfTokenType.SEMICOLON) && !check(ConfTokenType.LBRACE) && !check(ConfTokenType.EOF)) {
            ConfToken arg = advance();

            if (arg.type() != ConfTokenType.ATOM && arg.type() != ConfTokenType.STRING) {
                error("Expected Argument, got: " + arg.literal(), arg.lineNumber());
                break;
            }

            args.add(arg.literal());
        }
        if (match(ConfTokenType.LBRACE)) {
            while (!check(ConfTokenType.RBRACE) && !check(ConfTokenType.EOF) && !parserError) {
                children.add(parseStatement());
            }
            consume(ConfTokenType.RBRACE, "Expected closing brace");
            if (!parserError) {
                consume(ConfTokenType.SEMICOLON, "Expected semicolon after block");
            }
        } else {
            consume(ConfTokenType.SEMICOLON, "Expected semicolon");
        }

        return new ConfNode(name, args, children, nameToken.lineNumber());
    }

    private boolean match(ConfTokenType type) {
        if (!check(type)) return false;
        advance();
        return true;
    }

    private boolean check(ConfTokenType type) {
        return current.type() == type;
    }

    private ConfToken advance() {
        ConfToken previous = current;
        current = lexer.nextToken();
        return previous;
    }

    private ConfToken consume(ConfTokenType type, String message) {
        if (check(type)) return advance();
        error(message + ", got: " + current.literal(), current.lineNumber());
        return current;
    }

    public List<ConfNode> getRootNodes() {
        return List.copyOf(rootNodes);
    }

    /** Reports a fatal config parsing error and marks the parser as failed. */
    private void error(String message, int lineNumber) {
        ConfigManager.logger.log(
                (RED + BOLD + "[CONFIG-ERROR]" + RESET + RED + " line " + RESET + YELLOW + lineNumber + RESET + RED
                        + ": " + message + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true);
        parserError = true;
    }

    /** Reports a non-fatal zone parsing warning. */
    private void warning(String message, int lineNumber) {
        ConfigManager.logger.log(
                (YELLOW + BOLD + "[CONFIG-WARNING]" + RESET + YELLOW + " line " + RESET + CYAN + lineNumber + RESET
                        + YELLOW + ": " + message + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.WARNING,
                true);
    }

    public boolean hasParserError() {
        return this.parserError;
    }
}
