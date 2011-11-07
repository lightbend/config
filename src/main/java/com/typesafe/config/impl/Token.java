package com.typesafe.config.impl;

class Token {
    private TokenType tokenType;

    Token(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public TokenType tokenType() {
        return tokenType;
    }

    @Override
    public String toString() {
        return tokenType.name();
    }
}
