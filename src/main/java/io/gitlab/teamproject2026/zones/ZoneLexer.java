package io.gitlab.teamproject2026.zones;

/**
 * Lexical analyzer for BIND-style DNS zone files.
 * Converts raw zone file text into a stream of typed tokens
 * used by the parser to construct DNS resource records.
 *
 * Supports DNS directives, domain names, IP addresses,
 * multiline records, comments, and quoted TXT strings.
 */
public class ZoneLexer {
    String input;
    int position = 0;
    int readPosition = 0;
    char ch;
    int lineCount = 1;

    /**
     * Creates a lexer for the provided zone file input.
     * Initializes the lexer by reading the first character.
     *
     * @param input raw DNS zone file content
     */
    public ZoneLexer(String input) {
        this.input = input;
        this.readNextChar();
    }

    /**
     * Advances the lexer to the next character in the input stream.
     * Reaches '\0' when the end of input is encountered.
     */
    public void readNextChar(){
        if (this.readPosition >= input.length()){
            this.ch = '\0';
        } else {
            this.ch = input.charAt(readPosition);
        }
        this.position = this.readPosition;
        this.readPosition++;
    }

    /**
     * Produces the next token from the input stream.
     * Handles punctuation, directives, identifiers,
     * quoted TXT strings, comments, and end-of-file markers.
     *
     * @return next parsed token
     */
    public ZoneToken nextToken(){
        ZoneToken tok;

        this.skipComments();

        switch(this.ch) {
            case ' ', '\t' -> {
                tok = new ZoneToken(ZoneTokenType.SPACE, this.ch, lineCount);
                // collapse consecutive spaces/tabs into one SPACE token
                while (this.ch == ' ' || this.ch == '\t') {
                    this.readNextChar();
                }
                return tok;
            }
            case ';' -> tok = new ZoneToken(ZoneTokenType.SEMICOLON, this.ch, lineCount);
            case '(' -> tok = new ZoneToken(ZoneTokenType.LPAREN, this.ch, lineCount);
            case ')' -> tok = new ZoneToken(ZoneTokenType.RPAREN, this.ch, lineCount);
            case '\n' -> {
                tok = new ZoneToken(ZoneTokenType.NEWLINE, '\n', lineCount);
                lineCount++;
            }
            case '\0' -> tok = new ZoneToken(ZoneTokenType.EOF, "", lineCount);
            case '"' -> tok = readTextToken();
            default -> {
                if (isLetter(this.ch) || Character.isDigit(this.ch) || this.ch == '\\') {
                    return determineIdentifier();
                } else {
                    tok = new ZoneToken(ZoneTokenType.ILLEGAL, this.ch, lineCount);
                }
            }

        }

        this.readNextChar();
        return tok;
    }

    /**
     * Reads a quoted TXT string token.
     * Supports escaped quotes and escaped backslashes.
     *
     * @return TXT token containing the unescaped string value
     */
    private ZoneToken readTextToken() {
        StringBuilder text = new StringBuilder();
        boolean escaped = false;
        // consume opening quote
        this.readNextChar();
        while (this.ch != '\0') {
            if (this.ch == '\n' || this.ch == '\r'){
                return new ZoneToken(ZoneTokenType.ILLEGAL, "Unterminated TXT string", lineCount);
            }
            if (escaped) {
                text.append('\\');
                text.append(this.ch);
                escaped = false;
            } else if (this.ch == '\\') {
                escaped = true;
            } else if (this.ch == '"') {
                return new ZoneToken(ZoneTokenType.TXT, text.toString(), lineCount);
            } else {
                text.append(this.ch);
            }
            this.readNextChar();
        }
        return new ZoneToken(ZoneTokenType.ILLEGAL, "Unterminated TXT string", lineCount);
    }
    /**
     * Parses an identifier-like token and determines its concrete type.
     * Supports DNS directives, RR types, IP addresses, numeric literals,
     * and generic ATOM values.
     *
     * @return parsed token with inferred TokenType
     */
    private ZoneToken determineIdentifier(){
        int startPosition = this.position;
        boolean escaped = false;

        while (this.ch != '\0') {
            if (escaped) {
                escaped = false;
                this.readNextChar();
                continue;
            }

            if (this.ch == '\\') {
                escaped = true;
                this.readNextChar();
                continue;
            }

            if (Character.isWhitespace(this.ch) || this.ch == '(' || this.ch == ')' || this.ch == '"' || this.ch == ';') {
                break;
            }
            this.readNextChar();

        }
        String literal = this.input.substring(startPosition, this.position);

        if (literal.equals("$ORIGIN")) { return new ZoneToken(ZoneTokenType.ORIGIN, literal, lineCount); }
        if (literal.equals("$TTL")) { return new ZoneToken(ZoneTokenType.TTL, literal, lineCount); }

        if (isValidIPv4(literal)) { return new ZoneToken(ZoneTokenType.IPV4_ADDRESS, literal, lineCount); }
        if (literal.contains(":")) { return new ZoneToken(ZoneTokenType.IPV6_ADDRESS, literal, lineCount); }
        if (isInteger(literal)) { return new ZoneToken(ZoneTokenType.NUMBER, literal, lineCount); }

        return  new ZoneToken(ZoneToken.lookUpIdentifier(literal),literal, lineCount);
    }

    private boolean isInteger(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Skips semicolon comments.
     * Stops before newline characters because they are meaningful tokens
     * in DNS zone file syntax.
     */
    private void skipComments() {
        while (this.ch == ';') {
            while (this.ch != '\n' && this.ch != '\r' && this.ch != '\0') {
                this.readNextChar();
            }
        }
    }

    private boolean isLetter(char ch) {
        return 'a' <= ch && ch <= 'z' || 'A' <= ch && ch <= 'Z' || ch == '.' || ch == ':' || ch == '$' || ch == '@' || ch == '-' || ch == '_' || ch == '+' || ch == '/';
    }

    private boolean isValidIPv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return false;
            }
            if (value < 0 || value > 255) return false;
        }
        return true;
    }

}
