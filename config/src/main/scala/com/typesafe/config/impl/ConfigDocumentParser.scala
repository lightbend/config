/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }
import scala.util.control.Breaks._
import com.typesafe.config._

object ConfigDocumentParser {
    private[impl] def parse(
        tokens: ju.Iterator[Token],
        origin: ConfigOrigin,
        options: ConfigParseOptions): ConfigNodeRoot = {
        val syntax = if (options.getSyntax == null) ConfigSyntax.CONF else options.getSyntax
        val context =
            new ParseContext(syntax, origin, tokens)
        context.parse
    }
    private[impl] def parseValue(
        tokens: ju.Iterator[Token],
        origin: ConfigOrigin,
        options: ConfigParseOptions): AbstractConfigNodeValue = {
        val syntax = if (options.getSyntax == null) ConfigSyntax.CONF else options.getSyntax
        val context =
            new ParseContext(syntax, origin, tokens)
        context.parseSingleValue
    }
    private object ParseContext {
        private def isIncludeKeyword(t: Token): Boolean =
            Tokens.isUnquotedText(t) && Tokens.getUnquotedText(t) == "include"
        private def isUnquotedWhitespace(t: Token): Boolean = {
            if (!Tokens.isUnquotedText(t)) return false
            val s = Tokens.getUnquotedText(t)
            var i = 0
            while (i < s.length) {
                val c = s.charAt(i)
                if (!ConfigImplUtil.isWhitespace(c)) return false
                i += 1
            }
            true
        }
    }
    final private class ParseContext(
        val flavor: ConfigSyntax,
        val baseOrigin: ConfigOrigin,
        val tokens: ju.Iterator[Token]) {

        private var lineNumber = 1
        final private var buffer = new ju.Stack[Token]
        // this is the number of "equals" we are inside,
        // used to modify the error message to reflect that
        // someone may think this is .properties format.
        private[impl] var equalsCount = 0

        private def popToken: Token = {
            if (buffer.isEmpty) return tokens.next
            buffer.pop
        }
        private def nextToken: Token = {
            val t = popToken
            if (flavor eq ConfigSyntax.JSON)
                if (Tokens.isUnquotedText(t) && !ParseContext
                    .isUnquotedWhitespace(t))
                    throw parseError(
                        "Token not allowed in valid JSON: '" + Tokens
                            .getUnquotedText(t) + "'")
                else if (Tokens.isSubstitution(t))
                    throw parseError("Substitutions (${} syntax) not allowed in JSON")
            t
        }
        private def nextTokenCollectingWhitespace(
            nodes: ju.Collection[AbstractConfigNode]): Token = {
            var retToken: Token = null // added for Scala

            breakable {
                while (true) {
                    val t: Token = nextToken
                    if (Tokens.isIgnoredWhitespace(t) || Tokens.isNewline(t) || ParseContext
                        .isUnquotedWhitespace(t)) {
                        nodes.add(new ConfigNodeSingleToken(t))
                        if (Tokens.isNewline(t)) {
                            lineNumber = t.lineNumber + 1
                        }
                    } else if (Tokens.isComment(t)) {
                        nodes.add(new ConfigNodeComment(t))
                    } else {
                        val newNumber = t.lineNumber
                        if (newNumber >= 0) lineNumber = newNumber
                        //return t
                        retToken = t
                        break // break - added for Scala to "return"
                    }
                }
            }
            retToken
        }

        private def putBack(token: Token): Unit = { buffer.push(token) }
        // In arrays and objects, comma can be omitted
        // as long as there's at least one newline instead.
        // this skips any newlines in front of a comma,
        // skips the comma, and returns true if it found
        // either a newline or a comma. The iterator
        // is left just after the comma or the newline.
        private def checkElementSeparator(
            nodes: ju.Collection[AbstractConfigNode]): Boolean = {
            if (flavor eq ConfigSyntax.JSON) {
                val t = nextTokenCollectingWhitespace(nodes)
                if (t eq Tokens.COMMA) {
                    nodes.add(new ConfigNodeSingleToken(t))
                    true
                } else {
                    putBack(t)
                    false
                }
            } else {
                var sawSeparatorOrNewline = false
                var t = nextToken
                var retTrue = false // added for Scala for break below
                breakable {
                    while (true) {
                        if (Tokens.isIgnoredWhitespace(t) || ParseContext
                            .isUnquotedWhitespace(t)) nodes.add(new ConfigNodeSingleToken(t))
                        else if (Tokens.isComment(t)) nodes.add(new ConfigNodeComment(t))
                        else if (Tokens.isNewline(t)) {
                            sawSeparatorOrNewline = true
                            lineNumber += 1
                            nodes.add(new ConfigNodeSingleToken(t))
                            // we want to continue to also eat
                            // a comma if there is one.
                        } else if (t eq Tokens.COMMA) {
                            nodes.add(new ConfigNodeSingleToken(t))
                            //return true
                            retTrue = true
                            break // break - added for Scala to "return"
                        } else {
                            // non-newline-or-comma
                            putBack(t)
                            //return sawSeparatorOrNewline
                            break // break - added for Scala to "return"
                        }
                        t = nextToken
                    }
                }
                if (retTrue) true else sawSeparatorOrNewline
            }
        }
        // parse a concatenation. If there is no concatenation, return the next value
        private def consolidateValues(
            nodes: ju.Collection[AbstractConfigNode]): AbstractConfigNodeValue = {
            // this trick is not done in JSON
            if (flavor eq ConfigSyntax.JSON) return null
            // create only if we have value tokens
            val values =
                new ju.ArrayList[AbstractConfigNode]
            var valueCount = 0
            // ignore a newline up front
            var t = nextTokenCollectingWhitespace(nodes)

            breakable {
                while (true) {
                    var v: AbstractConfigNodeValue = null
                    if (Tokens.isIgnoredWhitespace(t)) {
                        values.add(new ConfigNodeSingleToken(t))
                    } else if (Tokens.isValue(t) || Tokens.isUnquotedText(t) || Tokens.isSubstitution(t) ||
                        (t eq Tokens.OPEN_CURLY) || (t eq Tokens.OPEN_SQUARE)) {
                        // there may be newlines _within_ the objects and arrays
                        v = parseValue(t)
                        valueCount += 1
                        if (v == null) throw new ConfigException.BugOrBroken("no value")
                        values.add(v)
                    } else {
                        break // break
                    }
                    t = nextToken // but don't consolidate across a newline
                }
            }

            // original converted Java code
            //while (true) {
            //    var v: : AbstractConfigNodeValue = null
            //    if (Tokens.isIgnoredWhitespace(t)) {
            //        values.add(new ConfigNodeSingleToken(t))
            //        t = nextToken
            //        continue //todo: continue is not supported
            //    } else if (Tokens.isValue(t) || Tokens.isUnquotedText(t) || Tokens.isSubstitution(
            //        t
            //    ) || (t eq Tokens.OPEN_CURLY) || (t eq Tokens.OPEN_SQUARE)) { // there may be newlines _within_ the objects and arrays
            //        v = parseValue(t)
            //        valueCount += 1
            //    } else break//todo: break is not supported
            //    if (v == null) throw new ConfigException.BugOrBroken("no value")
            //    values.add(v)
            //    t = nextToken // but don't consolidate across a newline
            //
            //}
            putBack(t)
            // No concatenation was seen, but a single value may have been parsed, so return it, and put back
            // all succeeding tokens
            if (valueCount < 2) {
                var value: AbstractConfigNodeValue = null
                import scala.collection.JavaConverters._
                for (node <- values.asScala) {
                    if (node.isInstanceOf[AbstractConfigNodeValue])
                        value = node.asInstanceOf[AbstractConfigNodeValue]
                    else if (value == null) nodes.add(node)
                    else putBack(new ju.ArrayList[Token](node.tokens).get(0))
                }
                return value
            }
            // Put back any trailing whitespace, as the parent object is responsible for tracking
            // any leading/trailing whitespace
            var i = values.size - 1
            breakable {
                while (i >= 0) {
                    if (values.get(i).isInstanceOf[ConfigNodeSingleToken]) {
                        putBack(values.get(i).asInstanceOf[ConfigNodeSingleToken].token)
                        values.remove(i)
                    } else {
                        break // break
                    }
                    i -= 1
                }
            }
            new ConfigNodeConcatenation(values)
        }
        private def parseError(message: String): ConfigException = parseError(message, null)
        private def parseError(
            message: String,
            cause: Throwable): ConfigException =
            new ConfigException.Parse(
                baseOrigin.withLineNumber(lineNumber),
                message,
                cause)
        private def addQuoteSuggestion(
            badToken: String,
            message: String): String =
            addQuoteSuggestion(null, equalsCount > 0, badToken, message)
        private def addQuoteSuggestion(
            lastPath: Path,
            insideEquals: Boolean,
            badToken: String,
            message: String): String = {
            val previousFieldName =
                if (lastPath != null) lastPath.render else null
            var part: String = null
            if (badToken == Tokens.END.toString) { // EOF requires special handling for the error to make sense.
                if (previousFieldName != null)
                    part = message + " (if you intended '" + previousFieldName + "' to be part of a value, instead of a key, " + "try adding double quotes around the whole value"
                else return message
            } else if (previousFieldName != null)
                part = message + " (if you intended " + badToken + " to be part of the value for '" + previousFieldName + "', " + "try enclosing the value in double quotes"
            else
                part = message + " (if you intended " + badToken + " to be part of a key or string value, " + "try enclosing the key or value in double quotes"
            if (insideEquals)
                part + ", or you may be able to rename the file .properties rather than .conf)"
            else part + ")"
        }
        private def parseValue(t: Token): AbstractConfigNodeValue = {
            var v: AbstractConfigNodeValue = null
            val startingEqualsCount = equalsCount
            if (Tokens.isValue(t) || Tokens.isUnquotedText(t) || Tokens
                .isSubstitution(t)) v = new ConfigNodeSimpleValue(t)
            else if (t eq Tokens.OPEN_CURLY) v = parseObject(true)
            else if (t eq Tokens.OPEN_SQUARE) v = parseArray
            else
                throw parseError(
                    addQuoteSuggestion(
                        t.toString,
                        "Expecting a value but got wrong token: " + t))
            if (equalsCount != startingEqualsCount)
                throw new ConfigException.BugOrBroken(
                    "Bug in config parser: unbalanced equals count")
            v
        }
        private def parseKey(token: Token) =
            if (flavor eq ConfigSyntax.JSON)
                if (Tokens.isValueWithType(token, ConfigValueType.STRING))
                    PathParser.parsePathNodeExpression(
                        ju.Collections.singletonList(token).iterator,
                        baseOrigin.withLineNumber(lineNumber))
                else
                    throw parseError(
                        "Expecting close brace } or a field name here, got " + token)
            else {
                val expression = new ju.ArrayList[Token]
                var t = token
                while (Tokens.isValue(t) || Tokens.isUnquotedText(t)) {
                    expression.add(t)
                    t = nextToken // note: don't cross a newline
                }
                if (expression.isEmpty) throw parseError(ExpectingClosingParenthesisError + t)
                putBack(t) // put back the token we ended with

                PathParser.parsePathNodeExpression(
                    expression.iterator,
                    baseOrigin.withLineNumber(lineNumber))
            }
        private def isKeyValueSeparatorToken(t: Token) =
            if (flavor eq ConfigSyntax.JSON) t eq Tokens.COLON
            else (t eq Tokens.COLON) || (t eq Tokens.EQUALS) || (t eq Tokens.PLUS_EQUALS)

        final private val ExpectingClosingParenthesisError =
            "expecting a close parentheses ')' here, not: "

        private def parseInclude(
            children: ju.ArrayList[AbstractConfigNode]) = {
            var t = nextTokenCollectingWhitespace(children)
            // we either have a 'required()' or a quoted string or the "file()" syntax
            if (Tokens.isUnquotedText(t)) {
                val kindText = Tokens.getUnquotedText(t)
                if (kindText.startsWith("required(")) {
                    val r = kindText.replaceFirst("required\\(", "")
                    if (r.length > 0) putBack(Tokens.newUnquotedText(t.origin, r))
                    children.add(new ConfigNodeSingleToken(t))
                    //children.add(new ConfigNodeSingleToken(tOpen));
                    val res = parseIncludeResource(children, true)
                    t = nextTokenCollectingWhitespace(children)
                    if (Tokens.isUnquotedText(t) && Tokens.getUnquotedText(t) == ")") {
                        // OK, close paren
                    } else throw parseError(ExpectingClosingParenthesisError + t)
                    res
                } else {
                    putBack(t)
                    parseIncludeResource(children, false)
                }
            } else {
                putBack(t)
                parseIncludeResource(children, false)
            }
        }
        private def parseIncludeResource(
            children: ju.ArrayList[AbstractConfigNode],
            isRequired: Boolean): ConfigNodeInclude = {
            var t = nextTokenCollectingWhitespace(children)
            // we either have a quoted string or the "file()" syntax
            if (Tokens.isUnquotedText(t)) {
                // get foo(
                val kindText = Tokens.getUnquotedText(t)
                var kind: ConfigIncludeKind = null
                var prefix: String = null
                if (kindText.startsWith("url(")) {
                    kind = ConfigIncludeKind.URL
                    prefix = "url("
                } else if (kindText.startsWith("file(")) {
                    kind = ConfigIncludeKind.FILE
                    prefix = "file("
                } else if (kindText.startsWith("classpath(")) {
                    kind = ConfigIncludeKind.CLASSPATH
                    prefix = "classpath("
                } else
                    throw parseError(
                        "expecting include parameter to be quoted filename, file(), classpath(), or url(). No spaces are allowed before the open paren. Not expecting: " + t)
                val r = kindText.replaceFirst("[^(]*\\(", "")
                if (r.length > 0) putBack(Tokens.newUnquotedText(t.origin, r))
                children.add(new ConfigNodeSingleToken(t))
                // skip space inside parens
                t = nextTokenCollectingWhitespace(children)
                // quoted string
                if (!Tokens.isValueWithType(t, ConfigValueType.STRING))
                    throw parseError(
                        "expecting include " + prefix + ") parameter to be a quoted string, rather than: " + t)
                children.add(new ConfigNodeSimpleValue(t))
                // skip space after string, inside parens
                t = nextTokenCollectingWhitespace(children)
                if (Tokens.isUnquotedText(t) && Tokens.getUnquotedText(t).startsWith(")")) {
                    val rest = Tokens.getUnquotedText(t).substring(1)
                    if (rest.length > 0) putBack(Tokens.newUnquotedText(t.origin, rest))
                } else throw parseError(ExpectingClosingParenthesisError + t)
                new ConfigNodeInclude(children, kind, isRequired)
            } else if (Tokens.isValueWithType(t, ConfigValueType.STRING)) {
                children.add(new ConfigNodeSimpleValue(t))
                new ConfigNodeInclude(
                    children,
                    ConfigIncludeKind.HEURISTIC,
                    isRequired)
            } else
                throw parseError(
                    "include keyword is not followed by a quoted string, but by: " + t)
        }
        private def parseObject(hadOpenCurly: Boolean): ConfigNodeComplexValue = {
            // invoked just after the OPEN_CURLY (or START, if !hadOpenCurly)
            var afterComma = false
            val lastPath: Path = null // always null here ??
            var lastInsideEquals = false
            val objectNodes =
                new ju.ArrayList[AbstractConfigNode]
            var keyValueNodes: ju.ArrayList[AbstractConfigNode] = null
            val keys = new ju.HashMap[String, jl.Boolean]
            if (hadOpenCurly)
                objectNodes.add(new ConfigNodeSingleToken(Tokens.OPEN_CURLY))
            breakable {
                while (true) {
                    var t = nextTokenCollectingWhitespace(objectNodes)
                    if (t eq Tokens.CLOSE_CURLY) {
                        if ((flavor eq ConfigSyntax.JSON) && afterComma)
                            throw parseError(
                                addQuoteSuggestion(
                                    t.toString,
                                    "expecting a field name after a comma, got a close brace } instead"))
                        else if (!hadOpenCurly)
                            throw parseError(
                                addQuoteSuggestion(
                                    t.toString,
                                    "unbalanced close brace '}' with no open brace"))
                        objectNodes.add(new ConfigNodeSingleToken(Tokens.CLOSE_CURLY))
                        break // break
                    } else if ((t eq Tokens.END) && !hadOpenCurly) {
                        putBack(t)
                        break // break
                    } else if ((flavor ne ConfigSyntax.JSON) && ParseContext.isIncludeKeyword(t)) {
                        val includeNodes =
                            new ju.ArrayList[AbstractConfigNode]
                        includeNodes.add(new ConfigNodeSingleToken(t))
                        objectNodes.add(parseInclude(includeNodes))
                        afterComma = false
                    } else {
                        keyValueNodes = new ju.ArrayList[AbstractConfigNode]
                        val keyToken = t
                        val path = parseKey(keyToken)
                        keyValueNodes.add(path)
                        val afterKey = nextTokenCollectingWhitespace(keyValueNodes)
                        var insideEquals = false
                        var nextValue: AbstractConfigNodeValue = null
                        if ((flavor eq ConfigSyntax.CONF) && (afterKey eq Tokens.OPEN_CURLY)) {
                            // can omit the ':' or '=' before an object value
                            nextValue = parseValue(afterKey)
                        } else {
                            if (!isKeyValueSeparatorToken(afterKey))
                                throw parseError(
                                    addQuoteSuggestion(
                                        afterKey.toString,
                                        "Key '" + path.render + "' may not be followed by token: " + afterKey))
                            keyValueNodes.add(new ConfigNodeSingleToken(afterKey))
                            if (afterKey eq Tokens.EQUALS) {
                                insideEquals = true
                                equalsCount += 1
                            }
                            nextValue = consolidateValues(keyValueNodes)
                            if (nextValue == null)
                                nextValue = parseValue(nextTokenCollectingWhitespace(keyValueNodes))
                        }
                        keyValueNodes.add(nextValue)
                        if (insideEquals) equalsCount -= 1
                        lastInsideEquals = insideEquals
                        val key = path.value.first
                        val remaining = path.value.remainder
                        if (remaining == null) {
                            val existing = keys.get(key)
                            if (existing != null) {
                                // In strict JSON, dups should be an error; while in
                                // our custom config language, they should be merged
                                // if the value is an object (or substitution that
                                // could become an object).
                                if (flavor eq ConfigSyntax.JSON)
                                    throw parseError(
                                        "JSON does not allow duplicate fields: '" + key + "' was already seen")
                            }
                            keys.put(key, true)
                        } else {
                            if (flavor eq ConfigSyntax.JSON)
                                throw new ConfigException.BugOrBroken(
                                    "somehow got multi-element path in JSON mode")
                            keys.put(key, true)
                        }
                        afterComma = false
                        objectNodes.add(new ConfigNodeField(keyValueNodes))
                    }
                    if (checkElementSeparator(objectNodes)) {
                        // continue looping
                        afterComma = true
                    } else {
                        t = nextTokenCollectingWhitespace(objectNodes)
                        if (t eq Tokens.CLOSE_CURLY) {
                            if (!hadOpenCurly)
                                throw parseError(
                                    addQuoteSuggestion(
                                        lastPath,
                                        lastInsideEquals,
                                        t.toString,
                                        "unbalanced close brace '}' with no open brace"))
                            objectNodes.add(new ConfigNodeSingleToken(t))
                            break // break
                        } else if (hadOpenCurly) {
                            throw parseError(
                                addQuoteSuggestion(
                                    lastPath,
                                    lastInsideEquals,
                                    t.toString,
                                    "Expecting close brace } or a comma, got " + t))
                        } else {
                            if (t eq Tokens.END) {
                                putBack(t)
                                break // break
                            } else
                                throw parseError(
                                    addQuoteSuggestion(
                                        lastPath,
                                        lastInsideEquals,
                                        t.toString,
                                        "Expecting end of input or a comma, got " + t))
                        }
                    }
                }
            }
            new ConfigNodeObject(objectNodes)
        }

        private def parseArray: ConfigNodeComplexValue = {
            val children =
                new ju.ArrayList[AbstractConfigNode]
            children.add(new ConfigNodeSingleToken(Tokens.OPEN_SQUARE))
            // invoked just after the OPEN_SQUARE
            var t: Token = null
            var nextValue = consolidateValues(children)
            if (nextValue != null) children.add(nextValue)
            else {
                t = nextTokenCollectingWhitespace(children)
                // special-case the first element
                if (t eq Tokens.CLOSE_SQUARE) {
                    children.add(new ConfigNodeSingleToken(t))
                    return new ConfigNodeArray(children)
                } else if (Tokens.isValue(t) || (t eq Tokens.OPEN_CURLY) || (t eq Tokens.OPEN_SQUARE) || Tokens
                    .isUnquotedText(t) || Tokens.isSubstitution(t)) {
                    nextValue = parseValue(t)
                    children.add(nextValue)
                } else
                    throw parseError(
                        "List should have ] or a first element after the open [, instead had token: " + t + " (if you want " + t + " to be part of a string value, then double-quote it)")
            }
            // now remaining elements
            breakable {
                while (true) {
                    // just after a value
                    if (checkElementSeparator(children)) {
                        // comma (or newline equivalent) consumed
                    } else {
                        t = nextTokenCollectingWhitespace(children)
                        if (t eq Tokens.CLOSE_SQUARE) {
                            children.add(new ConfigNodeSingleToken(t))
                            //return new ConfigNodeArray(children)
                            break // break - added for Scala to force return from while loop
                        } else
                            throw parseError(
                                "List should have ended with ] or had a comma, instead had token: " +
                                    t + " (if you want " + t + " to be part of a string value, then double-quote it)")
                    }
                    // now just after a comma
                    nextValue = consolidateValues(children)
                    if (nextValue != null) children.add(nextValue)
                    else {
                        t = nextTokenCollectingWhitespace(children)
                        if (Tokens.isValue(t) || (t eq Tokens.OPEN_CURLY) || (t eq Tokens.OPEN_SQUARE) || Tokens
                            .isUnquotedText(t) || Tokens.isSubstitution(t)) {
                            nextValue = parseValue(t)
                            children.add(nextValue)
                        } else if ((flavor ne ConfigSyntax.JSON) && (t eq Tokens.CLOSE_SQUARE)) {
                            // we allow one trailing comma
                            putBack(t)
                        } else
                            throw parseError(
                                "List should have had new element after a comma, instead had token: " +
                                    t + " (if you want the comma or " +
                                    t + " to be part of a string value, then double-quote it)")
                    }
                }
            }
            return new ConfigNodeArray(children)
        }

        private[impl] def parse: ConfigNodeRoot = {
            val children = new ju.ArrayList[AbstractConfigNode]
            var t = nextToken
            if (t eq Tokens.START) {
                // OK
            } else
                throw new ConfigException.BugOrBroken(
                    "token stream did not begin with START, had " + t)
            t = nextTokenCollectingWhitespace(children)
            var result: AbstractConfigNode = null
            var missingCurly = false
            if ((t eq Tokens.OPEN_CURLY) || (t eq Tokens.OPEN_SQUARE))
                result = parseValue(t)
            else if (flavor eq ConfigSyntax.JSON)
                if (t eq Tokens.END) throw parseError("Empty document")
                else
                    throw parseError(
                        "Document must have an object or array at root, unexpected token: " + t)
            else { // the root object can omit the surrounding braces.
                // this token should be the first field's key, or part
                // of it, so put it back.
                putBack(t)
                missingCurly = true
                result = parseObject(false)
            }
            // Need to pull the children out of the resulting node so we can keep leading
            // and trailing whitespace if this was a no-brace object. Otherwise, we need to add
            // the result into the list of children.
            if (result.isInstanceOf[ConfigNodeObject] && missingCurly)
                children.addAll(result.asInstanceOf[ConfigNodeComplexValue].children)
            else children.add(result)
            t = nextTokenCollectingWhitespace(children)
            if (t eq Tokens.END)
                if (missingCurly) { // If there were no braces, the entire document should be treated as a single object
                    new ConfigNodeRoot(
                        ju.Collections.singletonList(
                            new ConfigNodeObject(children).asInstanceOf[AbstractConfigNode]),
                        baseOrigin)
                } else new ConfigNodeRoot(children, baseOrigin)
            else
                throw parseError(
                    "Document has trailing tokens after first object or array: " + t)
        }
        // Parse a given input stream into a single value node. Used when doing a replace inside a ConfigDocument.
        private[impl] def parseSingleValue: AbstractConfigNodeValue = {
            var t = nextToken
            if (t eq Tokens.START) {
                // OK
            } else {
                throw new ConfigException.BugOrBroken(
                    "token stream did not begin with START, had " + t)
            }
            t = nextToken
            if (Tokens.isIgnoredWhitespace(t) || Tokens.isNewline(t) || ParseContext
                .isUnquotedWhitespace(t) || Tokens.isComment(t))
                throw parseError(
                    "The value from withValueText cannot have leading or trailing newlines, whitespace, or comments")
            if (t eq Tokens.END) throw parseError("Empty value")
            if (flavor eq ConfigSyntax.JSON) {
                val node = parseValue(t)
                t = nextToken
                if (t eq Tokens.END) return node
                else
                    throw parseError(
                        "Parsing JSON and the value set in withValueText was either a concatenation or " + "had trailing whitespace, newlines, or comments")
            } else {
                putBack(t)
                val nodes = new ju.ArrayList[AbstractConfigNode]
                val node = consolidateValues(nodes)
                t = nextToken
                if (t eq Tokens.END) return node
                else
                    throw parseError(
                        "The value from withValueText cannot have leading or trailing newlines, whitespace, or comments")
            }
        }
    }
}
