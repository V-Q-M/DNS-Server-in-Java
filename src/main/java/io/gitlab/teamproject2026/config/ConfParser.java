package io.gitlab.teamproject2026.config;
import java.util.ArrayList;
import java.util.List;

public class ConfParser {
    private final List<ConfToken> tokens = new ArrayList<>();
    private int pos = 0;

    private final List<ConfNode> rootNodes = new ArrayList<>();
    private boolean parserError = false;

    String input;

    public ConfParser(String input){
        this.input = input;
        startParser();
    }
    private void startParser(){
        ConfLexer lexer = new ConfLexer(input);
        input = null;

        // Collect Tokens
        while (true) {
            ConfToken tok = lexer.nextToken();
            tokens.add(tok);

            //IO.println(tok.literal() + ":" +tok.type());
            if (tok.type() == ConfTokenType.EOF) {
                break;
            }
        }
        parseConfig();
        tokens.clear();
    }

    private void parseConfig() {
        while(!check(ConfTokenType.EOF) && !parserError) {
            rootNodes.add(parseStatement());
        }
    }

    private ConfNode parseStatement() {
        ConfToken nameToken = consume(ConfTokenType.ATOM, "Expected statement name");
        if (parserError) { return new ConfNode("", List.of(), List.of()); }

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
            consume(ConfTokenType.SEMICOLON, "Expected semicolon after block");
        } else {
            consume(ConfTokenType.SEMICOLON, "Expected semicolon");
        }

        return new ConfNode(name, args, children);
    }

    private boolean match(ConfTokenType type){
        if (!check(type)){
            return false;
        }

        advance();
        return true;
    }

    private boolean check(ConfTokenType type){
        if (pos >= tokens.size()){ return false; }

        return tokens.get(pos).type() == type;
    }

    private ConfToken advance(){
        if (pos < tokens.size()){
            pos++;
        }
        return tokens.get(pos - 1);
    }

    private ConfToken consume(ConfTokenType type, String message){
        if (check(type)){
            return advance();
        }

        ConfToken t = tokens.get(Math.min(pos, tokens.size() - 1));
        error(message + ", got: " + t.literal(), t.lineNumber());
        return t;
    }

    public List<ConfNode> getRootNodes() {
        return List.copyOf(rootNodes);
    }

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String BOLD   = "\u001B[1m";
    /**
     * Reports a fatal zone parsing error and marks the parser as failed.
     */
    private void error(String message, int lineNumber) {
        System.err.println(RED + BOLD + "[CONF-ERROR]" + RESET + RED + " line " + RESET + YELLOW + lineNumber + RESET + RED + ": " + message + RESET);
        parserError = true;
    }

    /**
     * Reports a non-fatal zone parsing warning.
     */
    private void warning(String message, int lineNumber) {
        System.err.println(YELLOW + BOLD + "[CONF-WARNING]" + RESET + YELLOW + " line "  + RESET + CYAN + lineNumber + RESET + YELLOW + ": " + message + RESET);
    }
    public boolean hasParserError(){
        return this.parserError;
    }

}
