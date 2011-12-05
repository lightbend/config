/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

class Token {
    final private TokenType tokenType;
    final private String debugString;

    Token(TokenType tokenType) {
        this(tokenType, null);
    }

    Token(TokenType tokenType, String debugString) {
        this.tokenType = tokenType;
        this.debugString = debugString;
    }


    public TokenType tokenType() {
        return tokenType;
    }

    @Override
    public String toString() {
        if (debugString != null)
            return debugString;
        else
            return tokenType.name();
    }

    protected boolean canEqual(Object other) {
        return other instanceof Token;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Token) {
            return canEqual(other)
                    && this.tokenType == ((Token) other).tokenType;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return tokenType.hashCode();
    }
}
