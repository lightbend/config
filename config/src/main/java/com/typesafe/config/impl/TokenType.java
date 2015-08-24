/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

enum TokenType {
    START,
    END,
    COMMA,
    EQUALS,
    COLON,
    OPEN_CURLY,
    CLOSE_CURLY,
    OPEN_SQUARE,
    CLOSE_SQUARE,
    VALUE,
    NEWLINE,
    UNQUOTED_TEXT,
    IGNORED_WHITESPACE,
    SUBSTITUTION,
    PROBLEM,
    COMMENT,
    PLUS_EQUALS;
}
