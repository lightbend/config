/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ util => ju }

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigValueType

/* FIXME the way the subclasses of Token are private with static isFoo and accessors is kind of ridiculous. */
object Tokens {

    class Value private[impl] (val value: AbstractConfigValue, origText: String)
        extends Token(TokenType.VALUE, value.origin, origText) {

        def this(value: AbstractConfigValue) = this(value, null)

        override def toString(): String =
            if (value.resolveStatus eq ResolveStatus.RESOLVED)
                "'" + value.unwrapped + "' (" + value.valueType.name + ")"
            else "'<unresolved value>' (" + value.valueType.name + ")"
        override def canEqual(other: Any): Boolean = other.isInstanceOf[Tokens.Value]
        override def equals(other: Any): Boolean =
            super.equals(other) && other
                .asInstanceOf[Tokens.Value]
                .value == value
        override def hashCode: Int = 41 * (41 + super.hashCode) + value.hashCode
    }

    private[Tokens] class Line private[impl] (origin: ConfigOrigin)
        extends Token(TokenType.NEWLINE, origin) {
        override def toString: String = "'\\n'@" + lineNumber
        override def canEqual(other: Any): Boolean = other.isInstanceOf[Tokens.Line]
        override def equals(other: Any): Boolean =
            super.equals(other) && other
                .asInstanceOf[Tokens.Line]
                .lineNumber == lineNumber
        override def hashCode: Int = 41 * (41 + super.hashCode) + lineNumber
        override def tokenText = "\n"
    }

    // This is not a Value, because it requires special processing
    private[Tokens] class UnquotedText private[impl] (
        origin: ConfigOrigin,
        val value: String)
        extends Token(TokenType.UNQUOTED_TEXT, origin) {

        override def toString(): String = "'" + value + "'"
        override def canEqual(other: Any): Boolean =
            other.isInstanceOf[Tokens.UnquotedText]
        override def equals(other: Any): Boolean =
            super.equals(other) && other
                .asInstanceOf[Tokens.UnquotedText]
                .value == value
        override def hashCode(): Int = 41 * (41 + super.hashCode) + value.hashCode
        override def tokenText(): String = value
    }

    private[Tokens] class IgnoredWhitespace private[impl] (
        origin: ConfigOrigin,
        val value: String)
        extends Token(TokenType.IGNORED_WHITESPACE, origin) {
        override def toString(): String = "'" + value + "' (WHITESPACE)"
        override def canEqual(other: Any): Boolean =
            other.isInstanceOf[Tokens.IgnoredWhitespace]
        override def equals(other: Any): Boolean =
            super.equals(other) && other.asInstanceOf[Tokens.IgnoredWhitespace].value == value
        override def hashCode(): Int = 41 * (41 + super.hashCode) + value.hashCode
        override def tokenText(): String = value
    }

    private[Tokens] class Problem private[impl] (
        origin: ConfigOrigin,
        val what: String,
        val message: String,
        val suggestQuotes: Boolean,
        val cause: Throwable)
        extends Token(TokenType.PROBLEM, origin) {

        override def toString(): String = {
            val sb = new StringBuilder
            sb.append('\'')
            sb.append(what)
            sb.append('\'')
            sb.append(" (")
            sb.append(message)
            sb.append(")")
            sb.toString
        }
        override def canEqual(other: Any): Boolean =
            other.isInstanceOf[Tokens.Problem]
        override def equals(other: Any): Boolean =
            super.equals(other) && other
                .asInstanceOf[Tokens.Problem]
                .what == what && other
                .asInstanceOf[Tokens.Problem]
                .message == message && other
                .asInstanceOf[Tokens.Problem]
                .suggestQuotes == suggestQuotes && ConfigImplUtil.equalsHandlingNull(
                    other.asInstanceOf[Tokens.Problem].cause,
                    cause)
        override def hashCode(): Int = {
            var h = 41 * (41 + super.hashCode)
            h = 41 * (h + what.hashCode)
            h = 41 * (h + message.hashCode)
            h = 41 * (h + suggestQuotes.hashCode)
            if (cause != null) h = 41 * (h + cause.hashCode)
            h
        }
    }

    private[Tokens] object Comment {
        final private[Tokens] class DoubleSlashComment private[impl] (origin: ConfigOrigin, text: String) extends Tokens.Comment(origin, text) {
            override def tokenText(): String = "//" + text
        }
        final private[impl] class HashComment private[impl] (origin: ConfigOrigin, text: String) extends Tokens.Comment(origin, text) {
            override def tokenText(): String = "#" + text
        }
    }

    abstract private class Comment private[impl] (
        origin: ConfigOrigin,
        val text: String)
        extends Token(TokenType.COMMENT, origin) {

        override def toString(): String = {
            val sb = new StringBuilder
            sb.append("'#")
            sb.append(text)
            sb.append("' (COMMENT)")
            sb.toString
        }
        override def canEqual(other: Any): Boolean =
            other.isInstanceOf[Tokens.Comment]
        override def equals(other: Any): Boolean =
            super.equals(other) && other
                .asInstanceOf[Tokens.Comment]
                .text == text
        override def hashCode(): Int = {
            var h = 41 * (41 + super.hashCode)
            h = 41 * (h + text.hashCode)
            h
        }
    }

    class Substitution(
        origin: ConfigOrigin,
        val optional: Boolean,
        val value: ju.List[Token])
        extends Token(TokenType.SUBSTITUTION, origin) {

        override def tokenText(): String =
            "${" + (if (this.optional) "?" else "") + Tokenizer.render(
                this.value.iterator) + "}"
        override def toString(): String = {
            val sb = new StringBuilder
            import scala.collection.JavaConverters._
            for (t <- value.asScala) {
                sb.append(t.toString)
            }
            "'${" + sb.toString + "}'"
        }
        override def canEqual(other: Any): Boolean =
            other.isInstanceOf[Tokens.Substitution]
        override def equals(other: Any): Boolean =
            super.equals(other) && other
                .asInstanceOf[Tokens.Substitution]
                .value == value
        override def hashCode(): Int = 41 * (41 + super.hashCode) + value.hashCode
    }

    def isValue(token: Token) = token.isInstanceOf[Tokens.Value]
    def getValue(token: Token) =
        if (token.isInstanceOf[Tokens.Value]) token.asInstanceOf[Tokens.Value].value
        else
            throw new ConfigException.BugOrBroken(
                "tried to get value of non-value token " + token)
    def isValueWithType(
        t: Token,
        valueType: ConfigValueType) =
        isValue(t) && (getValue(t).valueType eq valueType)
    def isNewline(token: Token) = token.isInstanceOf[Tokens.Line]
    def isProblem(token: Token) = token.isInstanceOf[Tokens.Problem]
    def getProblemWhat(token: Token) =
        if (token.isInstanceOf[Tokens.Problem])
            token.asInstanceOf[Tokens.Problem].what
        else
            throw new ConfigException.BugOrBroken(
                "tried to get problem what from " + token)
    def getProblemMessage(token: Token) =
        if (token.isInstanceOf[Tokens.Problem])
            token.asInstanceOf[Tokens.Problem].message
        else
            throw new ConfigException.BugOrBroken(
                "tried to get problem message from " + token)
    def getProblemSuggestQuotes(token: Token) =
        if (token.isInstanceOf[Tokens.Problem])
            token.asInstanceOf[Tokens.Problem].suggestQuotes
        else
            throw new ConfigException.BugOrBroken(
                "tried to get problem suggestQuotes from " + token)
    def getProblemCause(token: Token) =
        if (token.isInstanceOf[Tokens.Problem])
            token.asInstanceOf[Tokens.Problem].cause
        else
            throw new ConfigException.BugOrBroken(
                "tried to get problem cause from " + token)
    def isComment(token: Token) = token.isInstanceOf[Tokens.Comment]
    def getCommentText(token: Token) =
        if (token.isInstanceOf[Tokens.Comment])
            token.asInstanceOf[Tokens.Comment].text
        else
            throw new ConfigException.BugOrBroken(
                "tried to get comment text from " + token)
    def isUnquotedText(token: Token) =
        token.isInstanceOf[Tokens.UnquotedText]
    def getUnquotedText(token: Token) =
        if (token.isInstanceOf[Tokens.UnquotedText])
            token.asInstanceOf[Tokens.UnquotedText].value
        else
            throw new ConfigException.BugOrBroken(
                "tried to get unquoted text from " + token)
    def isIgnoredWhitespace(token: Token) =
        token.isInstanceOf[Tokens.IgnoredWhitespace]
    def isSubstitution(token: Token) =
        token.isInstanceOf[Tokens.Substitution]
    def getSubstitutionPathExpression(token: Token) =
        if (token.isInstanceOf[Tokens.Substitution])
            token.asInstanceOf[Tokens.Substitution].value
        else
            throw new ConfigException.BugOrBroken(
                "tried to get substitution from " + token)
    def getSubstitutionOptional(token: Token) =
        if (token.isInstanceOf[Tokens.Substitution])
            token.asInstanceOf[Tokens.Substitution].optional
        else
            throw new ConfigException.BugOrBroken(
                "tried to get substitution optionality from " + token)
    val START =
        Token.newWithoutOrigin(TokenType.START, "start of file", "")
    val END =
        Token.newWithoutOrigin(TokenType.END, "end of file", "")
    val COMMA = Token.newWithoutOrigin(TokenType.COMMA, "','", ",")
    val EQUALS =
        Token.newWithoutOrigin(TokenType.EQUALS, "'='", "=")
    val COLON =
        Token.newWithoutOrigin(TokenType.COLON, "':'", ":")
    val OPEN_CURLY =
        Token.newWithoutOrigin(TokenType.OPEN_CURLY, "'{'", "{")
    val CLOSE_CURLY =
        Token.newWithoutOrigin(TokenType.CLOSE_CURLY, "'}'", "}")
    val OPEN_SQUARE =
        Token.newWithoutOrigin(TokenType.OPEN_SQUARE, "'['", "[")
    val CLOSE_SQUARE =
        Token.newWithoutOrigin(TokenType.CLOSE_SQUARE, "']'", "]")
    val PLUS_EQUALS =
        Token.newWithoutOrigin(TokenType.PLUS_EQUALS, "'+='", "+=")
    def newLine(origin: ConfigOrigin) = new Tokens.Line(origin)
    def newProblem(
        origin: ConfigOrigin,
        what: String,
        message: String,
        suggestQuotes: Boolean,
        cause: Throwable) =
        new Tokens.Problem(origin, what, message, suggestQuotes, cause)
    def newCommentDoubleSlash(
        origin: ConfigOrigin,
        text: String) =
        new Comment.DoubleSlashComment(origin, text)
    def newCommentHash(
        origin: ConfigOrigin,
        text: String) =
        new Comment.HashComment(origin, text)
    def newUnquotedText(origin: ConfigOrigin, s: String) =
        new Tokens.UnquotedText(origin, s)
    def newIgnoredWhitespace(
        origin: ConfigOrigin,
        s: String) =
        new Tokens.IgnoredWhitespace(origin, s)
    def newSubstitution(
        origin: ConfigOrigin,
        optional: Boolean,
        expression: ju.List[Token]) =
        new Tokens.Substitution(origin, optional, expression)
    def newValue(value: AbstractConfigValue) =
        new Tokens.Value(value)
    def newValue(
        value: AbstractConfigValue,
        origText: String) =
        new Tokens.Value(value, origText)
    def newString(
        origin: ConfigOrigin,
        value: String,
        origText: String) =
        newValue(new ConfigString.Quoted(origin, value), origText)
    def newInt(
        origin: ConfigOrigin,
        value: Int,
        origText: String) =
        newValue(ConfigNumber.newNumber(origin, value, origText), origText)
    def newDouble(
        origin: ConfigOrigin,
        value: Double,
        origText: String) =
        newValue(ConfigNumber.newNumber(origin, value, origText), origText)
    def newLong(
        origin: ConfigOrigin,
        value: Long,
        origText: String) =
        newValue(ConfigNumber.newNumber(origin, value, origText), origText)
    def newNull(origin: ConfigOrigin) =
        newValue(new ConfigNull(origin), "null")
    def newBoolean(origin: ConfigOrigin, value: Boolean) =
        newValue(new ConfigBoolean(origin, value), "" + value)
}
