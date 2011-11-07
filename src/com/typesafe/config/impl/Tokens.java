package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;

final class Tokens {
    static class Token {
        private TokenType tokenType;

        Token(TokenType tokenType) {
            this.tokenType = tokenType;
        }

        public TokenType tokenType() {
            return tokenType;
        }
    }

    static class Value extends Token {

        private AbstractConfigValue value;

        Value(AbstractConfigValue value) {
            super(TokenType.VALUE);
            this.value = value;
        }

        AbstractConfigValue value() {
            return value;
        }
    }

    static boolean isValue(Token token) {
        return token instanceof Value;
    }

    static Token START = new Token(TokenType.START);
    static Token END = new Token(TokenType.END);
    static Token COMMA = new Token(TokenType.COMMA);
    static Token COLON = new Token(TokenType.COLON);
    static Token OPEN_CURLY = new Token(TokenType.OPEN_CURLY);
    static Token CLOSE_CURLY = new Token(TokenType.CLOSE_CURLY);
    static Token OPEN_SQUARE = new Token(TokenType.OPEN_SQUARE);
    static Token CLOSE_SQUARE = new Token(TokenType.CLOSE_SQUARE);

    static Token newValue(AbstractConfigValue value) {
        return new Value(value);
    }

    static Token newString(ConfigOrigin origin, String value) {
        return newValue(new ConfigString(origin, value));
    }

    static Token newInt(ConfigOrigin origin, int value) {
        return newValue(new ConfigInt(origin, value));
    }

    static Token newDouble(ConfigOrigin origin, double value) {
        return newValue(new ConfigDouble(origin, value));
    }

    static Token newLong(ConfigOrigin origin, long value) {
        return newValue(new ConfigLong(origin, value));
    }

    static Token newNull(ConfigOrigin origin) {
        return newValue(new ConfigNull(origin));
    }

    static Token newBoolean(ConfigOrigin origin, boolean value) {
        return newValue(new ConfigBoolean(origin, value));
    }
}
