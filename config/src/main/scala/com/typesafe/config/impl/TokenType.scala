/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

final class TokenType private (name: String, ordinal: Int)
    extends Enum[TokenType](name, ordinal)

object TokenType {

    final val START = new TokenType("START", 0)
    final val END = new TokenType("END", 1)
    final val COMMA = new TokenType("COMMA", 2)
    final val EQUALS = new TokenType("EQUALS", 3)
    final val COLON = new TokenType("COLON", 4)
    final val OPEN_CURLY = new TokenType("OPEN_CURLY", 5)
    final val CLOSE_CURLY = new TokenType("CLOSE_CURLY", 6)
    final val OPEN_SQUARE = new TokenType("OPEN_SQUARE", 7)
    final val CLOSE_SQUARE = new TokenType("CLOSE_SQUARE", 8)
    final val VALUE = new TokenType("VALUE", 9)
    final val NEWLINE = new TokenType("NEWLINE", 10)
    final val UNQUOTED_TEXT = new TokenType("UNQUOTED_TEXT", 11)
    final val IGNORED_WHITESPACE = new TokenType("IGNORED_WHITESPACE", 12)
    final val SUBSTITUTION = new TokenType("SUBSTITUTION", 13)
    final val PROBLEM = new TokenType("PROBLEM", 14)
    final val COMMENT = new TokenType("COMMENT", 15)
    final val PLUS_EQUALS = new TokenType("PLUS_EQUALS", 16)

    private[this] val _values: Array[TokenType] =
        Array(START, END, COMMA, EQUALS, COLON, OPEN_CURLY, CLOSE_CURLY, OPEN_SQUARE,
            CLOSE_SQUARE, VALUE, NEWLINE, UNQUOTED_TEXT, IGNORED_WHITESPACE, SUBSTITUTION,
            PROBLEM, COMMENT, PLUS_EQUALS)

    def values(): Array[TokenType] = _values.clone()

    def valueOf(name: String): TokenType =
        _values.find(_.name == name).getOrElse {
            throw new IllegalArgumentException("No enum const TokenType." + name)
        }
}