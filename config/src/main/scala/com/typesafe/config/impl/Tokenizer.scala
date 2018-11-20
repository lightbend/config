/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.IOException
import java.io.Reader
import java.{ lang => jl }
import java.{ util => ju }

import scala.util.control.Breaks._
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigSyntax

import scala.annotation.tailrec

object Tokenizer {
    // this exception should not leave this file
    @SerialVersionUID(1L)
    private class ProblemException(val problem: Token) extends Exception

    private def asString(codepoint: Int): String =
        if (codepoint == '\n') "newline"
        else if (codepoint == '\t') "tab"
        else if (codepoint == -1) "end of file"
        else if (ConfigImplUtil.isC0Control(codepoint))
            "control character 0x%x".format(codepoint)
        else "%c".format(codepoint)

    /**
     * Tokenizes a Reader. Does not close the reader; you have to arrange to do
     * that after you're done with the returned iterator.
     */
    def tokenize(
        origin: ConfigOrigin,
        input: Reader,
        flavor: ConfigSyntax) =
        new Tokenizer.TokenIterator(
            origin,
            input,
            flavor ne ConfigSyntax.JSON)

    private[impl] def render(tokens: ju.Iterator[Token]) = {
        val renderedText = new StringBuilder
        while (tokens.hasNext) renderedText.append(tokens.next.tokenText)
        renderedText.toString
    }

    object TokenIterator {

        class WhitespaceSaver() {
            val whitespace = new jl.StringBuilder
            // has to be saved inside value concatenations
            // may need to value-concat with next value
            var lastTokenWasSimpleValue = false

            private[impl] def add(c: Int): Unit = whitespace.appendCodePoint(c)
            private[impl] def check(
                t: Token,
                baseOrigin: ConfigOrigin,
                lineNumber: Int) =
                if (isSimpleValue(t)) nextIsASimpleValue(baseOrigin, lineNumber)
                else nextIsNotASimpleValue(baseOrigin, lineNumber)

            // called if the next token is not a simple value;
            // discards any whitespace we were saving between
            // simple values.
            private def nextIsNotASimpleValue(
                baseOrigin: ConfigOrigin,
                lineNumber: Int) = {
                lastTokenWasSimpleValue = false
                createWhitespaceTokenFromSaver(baseOrigin, lineNumber)
            }
            // called if the next token IS a simple value,
            // so creates a whitespace token if the previous
            // token also was.
            private def nextIsASimpleValue(
                baseOrigin: ConfigOrigin,
                lineNumber: Int) = {
                val t = createWhitespaceTokenFromSaver(baseOrigin, lineNumber)
                if (!lastTokenWasSimpleValue) lastTokenWasSimpleValue = true
                t
            }
            private def createWhitespaceTokenFromSaver(
                baseOrigin: ConfigOrigin,
                lineNumber: Int): Token = {
                if (whitespace.length > 0) {
                    var t: Token = null
                    if (lastTokenWasSimpleValue)
                        t = Tokens.newUnquotedText(
                            lineOrigin(baseOrigin, lineNumber),
                            whitespace.toString)
                    else
                        t = Tokens.newIgnoredWhitespace(
                            lineOrigin(baseOrigin, lineNumber),
                            whitespace.toString)
                    whitespace.setLength(0) // reset

                    return t
                }
                null
            }
        }
        private[impl] def isWhitespace(c: Int): Boolean = ConfigImplUtil.isWhitespace(c)
        private[impl] def isWhitespaceNotNewline(c: Int): Boolean =
            c != '\n' && ConfigImplUtil.isWhitespace(c)
        private def problem(
            origin: ConfigOrigin,
            what: String,
            message: String,
            cause: Throwable): ProblemException =
            problem(origin, what, message, false, cause)
        private def problem(
            origin: ConfigOrigin,
            what: String,
            message: String,
            suggestQuotes: Boolean,
            cause: Throwable): ProblemException = {
            if (what == null || message == null)
                throw new ConfigException.BugOrBroken(
                    "internal error, creating bad ProblemException")
            new Tokenizer.ProblemException(
                Tokens.newProblem(origin, what, message, suggestQuotes, cause))
        }
        private def problem(
            origin: ConfigOrigin,
            message: String): ProblemException =
            problem(origin, "", message, null)
        private def lineOrigin(
            baseOrigin: ConfigOrigin,
            lineNumber: Int) =
            baseOrigin
                .asInstanceOf[SimpleConfigOrigin]
                .withLineNumber(lineNumber)
        // chars JSON allows a number to start with
        private[impl] val firstNumberChars = "0123456789-"
        // chars JSON allows to be part of a number
        private[impl] val numberChars = "0123456789eE+-."
        // chars that stop an unquoted string
        private[impl] val notInUnquotedText = "$\"{}[]:=,+#`^?!@*&\\"
        private def isSimpleValue(t: Token) =
            if (Tokens.isSubstitution(t) || Tokens.isUnquotedText(t) || Tokens
                .isValue(t)) true
            else false
    }

    class TokenIterator(
        _origin: ConfigOrigin,
        val input: Reader,
        val allowComments: Boolean)
        extends ju.Iterator[Token] {

        val origin = _origin.asInstanceOf[SimpleConfigOrigin]
        val buffer = new ju.LinkedList[Integer]
        var lineNumber = 1
        var lineOrigin = origin.withLineNumber(lineNumber)
        val tokens = new ju.LinkedList[Token]
        tokens.add(Tokens.START)
        val whitespaceSaver = new TokenIterator.WhitespaceSaver

        // this should ONLY be called from nextCharSkippingComments
        // or when inside a quoted string, or when parsing a sequence
        // like ${ or +=, everything else should use
        // nextCharSkippingComments().
        private def nextCharRaw: Int =
            if (buffer.isEmpty)
                try input.read
                catch {
                    case e: IOException =>
                        throw new ConfigException.IO(
                            origin,
                            "read error: " + e.getMessage,
                            e)
                }
            else {
                val c = buffer.pop
                c
            }
        private def putBack(c: Int): Unit = {
            if (buffer.size > 2)
                throw new ConfigException.BugOrBroken(
                    "bug: putBack() three times, undesirable look-ahead")
            buffer.push(c)
        }
        private def startOfComment(c: Int) =
            if (c == -1) false
            else if (allowComments)
                if (c == '#') true
                else if (c == '/') {
                    val maybeSecondSlash = nextCharRaw
                    // we want to predictably NOT consume any chars
                    putBack(maybeSecondSlash)
                    if (maybeSecondSlash == '/') true else false
                } else false
            else false

        // get next char, skipping non-newline whitespace
        // needed to rewrite in a Scala fashion
        private def nextCharAfterWhitespace(
            saver: TokenIterator.WhitespaceSaver): Int = {
            @tailrec
            def consume(c: Int): Int =
                if (c == -1) -1
                else if (TokenIterator.isWhitespaceNotNewline(c)) {
                    saver.add(c)
                    consume(nextCharRaw)
                } else c
            consume(nextCharRaw)
        }

        private def problem(message: String): ProblemException = problem("", message, null)
        private def problem(
            what: String,
            message: String): ProblemException =
            problem(what, message, null)
        private def problem(
            what: String,
            message: String,
            suggestQuotes: Boolean): ProblemException =
            problem(what, message, suggestQuotes, null)
        private def problem(
            what: String,
            message: String,
            cause: Throwable): ProblemException =
            TokenIterator.problem(lineOrigin, what, message, cause)
        private def problem(
            what: String,
            message: String,
            suggestQuotes: Boolean,
            cause: Throwable): ProblemException =
            TokenIterator.problem(lineOrigin, what, message, suggestQuotes, cause)

        // ONE char has always been consumed, either the # or the first /, but not both slashes
        private def pullComment(firstChar: Int): Token = {
            var doubleSlash = false
            if (firstChar == '/') {
                val discard = nextCharRaw
                if (discard != '/')
                    throw new ConfigException.BugOrBroken(
                        "called pullComment but // not seen")
                doubleSlash = true
            }
            val sb = new jl.StringBuilder
            var token: Token = null
            breakable {
                while (true) {
                    val c = nextCharRaw
                    if (c == -1 || c == '\n') {
                        putBack(c)
                        if (doubleSlash) {
                            token = Tokens.newCommentDoubleSlash(lineOrigin, sb.toString)
                            break
                        } else {
                            token = Tokens.newCommentHash(lineOrigin, sb.toString)
                            break
                        }
                    } else sb.appendCodePoint(c)
                }
            }
            token
        }

        // The rules here are intended to maximize convenience while
        // avoiding confusion with real valid JSON. Basically anything
        // that parses as JSON is treated the JSON way and otherwise
        // we assume it's a string and let the parser sort it out.
        private def pullUnquotedText: Token = {
            val origin = lineOrigin
            val sb = new jl.StringBuilder
            var t: Token = null
            var c = nextCharRaw
            var retToken = false
            breakable {
                while (true) {
                    if (c == -1) break // break
                    else if (TokenIterator.notInUnquotedText.indexOf(c) >= 0)
                        break // break
                    else if (TokenIterator.isWhitespace(c))
                        break // break
                    else if (startOfComment(c)) break // break
                    else sb.appendCodePoint(c)
                    // we parse true/false/null tokens as such no matter
                    // what is after them, as long as they are at the
                    // start of the unquoted token.
                    if (sb.length == 4) {
                        val s = sb.toString
                        if (s == "true") {
                            retToken = true
                            t = Tokens.newBoolean(origin, true)
                            break // return
                        } else if (s == "null") {
                            retToken = true
                            t = Tokens.newNull(origin)
                            break // return
                        }
                    } else if (sb.length == 5) {
                        val s = sb.toString
                        if (s == "false") {
                            retToken = true
                            t = Tokens.newBoolean(origin, false)
                            break // return
                        }
                    }
                    c = nextCharRaw
                }
            }
            if (retToken == false) {
                // put back the char that ended the unquoted text
                putBack(c)
                val s = sb.toString
                t = Tokens.newUnquotedText(origin, s)
            }
            t
        }
        @throws[ProblemException]
        private def pullNumber(firstChar: Int) = {
            val sb = new jl.StringBuilder
            sb.appendCodePoint(firstChar)
            var containedDecimalOrE = false
            var c = nextCharRaw
            while (c != -1 && TokenIterator.numberChars.indexOf(c) >= 0) {
                if (c == '.' || c == 'e' || c == 'E') containedDecimalOrE = true
                sb.appendCodePoint(c)
                c = nextCharRaw
            }
            // the last character we looked at wasn't part of the number, put it back
            putBack(c)
            val s = sb.toString
            try
                if (containedDecimalOrE) {
                    // force floating point representation
                    Tokens.newDouble(lineOrigin, s.toDouble, s)
                } else { // this should throw if the integer is too large for Long
                    Tokens.newLong(lineOrigin, s.toLong, s)
                }
            catch {
                case e: NumberFormatException =>
                    // not a number after all, see if it's an unquoted string.
                    for (u <- s.toCharArray) {
                        if (TokenIterator.notInUnquotedText.indexOf(u) >= 0)
                            throw problem(
                                asString(u),
                                "Reserved character '" + asString(u) + "' is not allowed outside quotes",
                                true /* suggestQuotes */
                            )
                    }
                    // no evil chars so we just decide this was a string and
                    // not a number.
                    Tokens.newUnquotedText(lineOrigin, s)
            }
        }
        @throws[ProblemException]
        private def pullEscapeSequence(
            sb: jl.StringBuilder,
            sbOrig: jl.StringBuilder): Unit = {
            val escaped = nextCharRaw
            if (escaped == -1)
                throw problem(
                    "End of input but backslash in string had nothing after it")
            // This is needed so we return the unescaped escape characters back out when rendering the token
            sbOrig.appendCodePoint('\\')
            sbOrig.appendCodePoint(escaped)
            escaped match {
                case '"' => sb.append('"')
                case '\\' => sb.append('\\')
                case '/' => sb.append('/')
                case 'b' => sb.append('\b')
                case 'f' => sb.append('\f')
                case 'n' => sb.append('\n')
                case 'r' => sb.append('\r')
                case 't' => sb.append('\t')
                case 'u' =>
                    // kind of absurdly slow, but screw it for now
                    val a = new Array[Char](4)
                    var i = 0
                    while (i < 4) {
                        val c = nextCharRaw
                        if (c == -1)
                            throw problem(
                                "End of input but expecting 4 hex digits for \\uXXXX escape")
                        a(i) = c.toChar
                        i += 1
                    }
                    val digits = new String(a)
                    sbOrig.append(a)
                    try sb.appendCodePoint(Integer.parseInt(digits, 16))
                    catch {
                        case e: NumberFormatException =>
                            throw problem(
                                digits,
                                "Malformed hex digits after \\u escape in string: '%s'".format(digits),
                                e)
                    }
                case _ => throw problem(
                    asString(escaped),
                    "backslash followed by '%s', this is not a valid escape sequence (quoted strings use JSON escaping, so use double-backslash \\\\ for literal backslash)"
                        .format(asString(escaped)))
            }
        }
        @throws[ProblemException]
        private def appendTripleQuotedString(
            sb: jl.StringBuilder,
            sbOrig: jl.StringBuilder): Unit = {
            // we are after the opening triple quote and need to consume the close triple
            var consecutiveQuotes = 0
            breakable {
                while (true) {
                    val c = nextCharRaw
                    if (c == '"') consecutiveQuotes += 1 else if (consecutiveQuotes >= 3) {
                        // the last three quotes end the string and the others are kept.
                        sb.setLength(sb.length - 3)
                        putBack(c)
                        break // break
                    } else {
                        consecutiveQuotes = 0
                        if (c == -1)
                            throw problem(
                                "End of input but triple-quoted string was still open")
                        else if (c == '\n') { // keep the line number accurate
                            lineNumber += 1
                            lineOrigin = origin.withLineNumber(lineNumber)
                        }
                    }
                    sb.appendCodePoint(c)
                    sbOrig.appendCodePoint(c)
                }
            }

        }
        @throws[ProblemException]
        private def pullQuotedString: Tokens.Value = {
            // the open quote has already been consumed
            val sb = new jl.StringBuilder
            // We need a second string builder to keep track of escape characters.
            // We want to return them exactly as they appeared in the original text,
            // which means we will need a new StringBuilder to escape escape characters
            // so we can also keep the actual value of the string. This is gross.
            val sbOrig = new jl.StringBuilder
            sbOrig.appendCodePoint('"')
            breakable {
                while (true) {
                    val c = nextCharRaw
                    if (c == -1) throw problem("End of input but string quote was still open")
                    if (c == '\\') pullEscapeSequence(sb, sbOrig) else if (c == '"') {
                        sbOrig.appendCodePoint(c)
                        break // break
                    } else if (ConfigImplUtil.isC0Control(c))
                        throw problem(
                            asString(c),
                            "JSON does not allow unescaped " + asString(c) + " in quoted strings, use a backslash escape")
                    else {
                        sb.appendCodePoint(c)
                        sbOrig.appendCodePoint(c)
                    }
                }
            }

            // maybe switch to triple-quoted string, sort of hacky...
            if (sb.length == 0) {
                val third = nextCharRaw
                if (third == '"') {
                    sbOrig.appendCodePoint(third)
                    appendTripleQuotedString(sb, sbOrig)
                } else putBack(third)
            }
            Tokens.newString(lineOrigin, sb.toString, sbOrig.toString)
        }
        @throws[ProblemException]
        private def pullPlusEquals: Token = {
            // the initial '+' has already been consumed
            val c = nextCharRaw
            if (c != '=') throw problem(
                asString(c),
                "'+' not followed by =, '" + asString(c) + "' not allowed after '+'",
                true)
            Tokens.PLUS_EQUALS
        }
        @throws[ProblemException]
        private def pullSubstitution: Tokens.Substitution = {
            // the initial '$' has already been consumed
            val origin = lineOrigin
            var c = nextCharRaw
            if (c != '{') throw problem(
                asString(c),
                "'$' not followed by {, '" + asString(c) + "' not allowed after '$'",
                true)
            var optional = false
            c = nextCharRaw
            if (c == '?') optional = true else putBack(c)
            val saver =
                new TokenIterator.WhitespaceSaver
            val expression = new ju.ArrayList[Token]
            var t: Token = null
            breakable {
                do {
                    t = pullNextToken(saver)
                    // note that we avoid validating the allowed tokens inside
                    // the substitution here; we even allow nested substitutions
                    // in the tokenizer. The parser sorts it out.
                    if (t eq Tokens.CLOSE_CURLY) { // end the loop, done!
                        break // break
                    } else if (t eq Tokens.END)
                        throw TokenIterator.problem(
                            origin,
                            "Substitution ${ was not closed with a }")
                    else {
                        val whitespace = saver.check(t, origin, lineNumber)
                        if (whitespace != null) expression.add(whitespace)
                        expression.add(t)
                    }
                } while (true)
            }
            Tokens.newSubstitution(origin, optional, expression)
        }
        @throws[ProblemException]
        private def pullNextToken(saver: TokenIterator.WhitespaceSaver): Token = {
            val c = nextCharAfterWhitespace(saver)
            if (c == -1) Tokens.END else if (c == '\n') { // newline tokens have the just-ended line number
                val line = Tokens.newLine(lineOrigin)
                lineNumber += 1
                lineOrigin = origin.withLineNumber(lineNumber)
                line
            } else {
                var t: Token = null
                if (startOfComment(c)) t = pullComment(c)
                else {
                    c match {
                        case '"' => t = pullQuotedString
                        case '$' => t = pullSubstitution
                        case ':' => t = Tokens.COLON
                        case ',' => t = Tokens.COMMA
                        case '=' => t = Tokens.EQUALS
                        case '{' => t = Tokens.OPEN_CURLY
                        case '}' => t = Tokens.CLOSE_CURLY
                        case '[' => t = Tokens.OPEN_SQUARE
                        case ']' => t = Tokens.CLOSE_SQUARE
                        case '+' => t = pullPlusEquals
                        case _ => t = null
                    }
                    if (t == null)
                        if (TokenIterator.firstNumberChars.indexOf(c) >= 0) t = pullNumber(c)
                        else if (TokenIterator.notInUnquotedText.indexOf(c) >= 0)
                            throw problem(
                                asString(c),
                                "Reserved character '" + asString(c) + "' is not allowed outside quotes",
                                true)
                        else {
                            putBack(c)
                            t = pullUnquotedText
                        }
                }
                if (t == null)
                    throw new ConfigException.BugOrBroken(
                        "bug: failed to generate next token")
                t
            }
        }
        @throws[ProblemException]
        private def queueNextToken(): Unit = {
            val t = pullNextToken(whitespaceSaver)
            val whitespace = whitespaceSaver.check(t, origin, lineNumber)
            if (whitespace != null) tokens.add(whitespace)
            tokens.add(t)
        }
        override def hasNext: Boolean = !tokens.isEmpty
        override def next: Token = {
            val t = tokens.remove
            if (tokens.isEmpty && (t ne Tokens.END)) {
                try queueNextToken()
                catch {
                    case e: Tokenizer.ProblemException =>
                        tokens.add(e.problem)
                }
                if (tokens.isEmpty) throw new ConfigException.BugOrBroken(
                    "bug: tokens queue should not be empty here")
            }
            t
        }
        override def remove(): Unit = {
            throw new UnsupportedOperationException(
                "Does not make sense to remove items from token stream")
        }
    }
}
