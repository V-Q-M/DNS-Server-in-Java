package io.gitlab.teamproject2026.config.interpreter;

public class ConfLexer {
    String input;
    int position = 0;
    int readPosition = 0;
    char ch;
    int lineCount = 1;
    
    public ConfLexer(String input){
        this.input = input;     
        this.readNextChar();
    }

    public void readNextChar(){
        if (this.readPosition >= input.length()){
            this.ch = '\0';
        } else {
            this.ch = input.charAt(readPosition);
        }
        this.position = this.readPosition;
        this.readPosition++;
    }

    public ConfToken nextToken() {
        ConfToken tok;
        this.skipWhitespaceAndComments();

        switch(this.ch) {
            case ';' -> tok = new ConfToken(ConfTokenType.SEMICOLON, this.ch, lineCount);
            case '{' -> tok = new ConfToken(ConfTokenType.LBRACE, this.ch, lineCount);
            case '}' -> tok = new ConfToken(ConfTokenType.RBRACE, this.ch, lineCount);
            case '\0' -> tok = new ConfToken(ConfTokenType.EOF, "", lineCount);
            case '"' -> tok = readStringToken();
            default -> {
                if (Character.isLetter(this.ch) || Character.isDigit(this.ch) || this.ch == '"') {
                    return determineIdentifier();
                } else {
                    tok = new ConfToken(ConfTokenType.ILLEGAL, this.ch, lineCount);
                }
            }
        }

        this.readNextChar();
        return tok;
    }

    private void skipWhitespaceAndComments(){
        while (true) {
            // Skip whitespace
            while (this.ch == ' ' || this.ch == '\t' || this.ch == '\n' || this.ch == '\r') {
                this.readNextChar();
            }
            // Skip comments
            if (this.ch == '#') {
                while (this.ch != '\n' && this.ch != '\r' && this.ch != '\0') {
                    this.readNextChar();
                }
            } else {
                break;
            }
        }
    }

    private ConfToken determineIdentifier(){
        int startPosition = this.position;
        while (this.ch != '\0') {
            if (Character.isWhitespace(this.ch) || this.ch == '{' || this.ch == '}' || this.ch == ';' || this.ch == '#') {
                break;
            }
            this.readNextChar();

        }
        String literal = this.input.substring(startPosition, this.position);

        return new ConfToken(ConfToken.lookUpIdentifier(literal),literal, lineCount);
    }

    private ConfToken readStringToken() {
        StringBuilder str = new StringBuilder();
        boolean escaped = false;
        // consume opening quote
        this.readNextChar();
        while (this.ch != '\0') {
            if (escaped) {
                str.append('\\');
                str.append(this.ch);
                escaped = false;
            } else if (this.ch == '\\') {
                escaped = true;
            } else if (this.ch == '"') {
                break;
            } else {
                str.append(this.ch);
            }
            this.readNextChar();
        }
        if(escaped){
            str.append('\\');
        }
        return new ConfToken(ConfTokenType.STRING, str.toString(), lineCount);
    }
}
