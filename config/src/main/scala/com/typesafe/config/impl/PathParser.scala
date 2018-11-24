/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigValueType
import java.io.StringReader
import java.{ util => ju }
import scala.util.control.Breaks._

object PathParser {
    private[impl] class Element private[impl] (
        initial: String,
        // an element can be empty if it has a quoted empty string "" in it
        var canBeEmpty: Boolean) {
        private[impl] val sb = new StringBuilder(initial)
        override def toString: String =
            "Element(" + sb.toString + "," + canBeEmpty + ")"
    }

    private[impl] val apiOrigin = SimpleConfigOrigin.newSimple("path parameter")
    private[impl] def parsePathNode(path: String): ConfigNodePath =
        parsePathNode(path, ConfigSyntax.CONF)
    private[impl] def parsePathNode(path: String, flavor: ConfigSyntax): ConfigNodePath = {
        val reader = new StringReader(path)
        try {
            val tokens = Tokenizer.tokenize(apiOrigin, reader, flavor)
            tokens.next // drop START
            parsePathNodeExpression(tokens, apiOrigin, path, flavor)
        } finally {
            reader.close()
        }
    }
    private[impl] def parsePath(path: String): Path = {
        val speculated = speculativeFastParsePath(path)
        if (speculated != null) return speculated
        val reader = new StringReader(path)
        try {
            val tokens = Tokenizer.tokenize(apiOrigin, reader, ConfigSyntax.CONF)
            tokens.next
            parsePathExpression(tokens, apiOrigin, path)
        } finally {
            reader.close()
        }
    }
    def parsePathExpression(expression: ju.Iterator[Token], origin: ConfigOrigin): Path =
        parsePathExpression(expression, origin, null, null, ConfigSyntax.CONF)
    protected def parsePathExpression(expression: ju.Iterator[Token], origin: ConfigOrigin, originalText: String): Path =
        parsePathExpression(expression, origin, originalText, null, ConfigSyntax.CONF)
    private[impl] def parsePathNodeExpression(expression: ju.Iterator[Token], origin: ConfigOrigin): ConfigNodePath =
        parsePathNodeExpression(expression, origin, null, ConfigSyntax.CONF)
    protected def parsePathNodeExpression(expression: ju.Iterator[Token], origin: ConfigOrigin, originalText: String,
        flavor: ConfigSyntax): ConfigNodePath = {
        val pathTokens = new ju.ArrayList[Token]
        val path = parsePathExpression(expression, origin, originalText, pathTokens, flavor)
        new ConfigNodePath(path, pathTokens)
    }
    // originalText may be null if not available
    protected def parsePathExpression(
        expression: ju.Iterator[Token],
        origin: ConfigOrigin,
        originalText: String,
        pathTokens: ju.ArrayList[Token],
        flavor: ConfigSyntax): Path = {
        // each builder in "buf" is an element in the path.
        val buf = new ju.ArrayList[PathParser.Element]
        buf.add(new PathParser.Element("", false))
        if (!expression.hasNext) {
            throw new ConfigException.BadPath(
                origin,
                originalText,
                "Expecting a field name or path here, but got nothing")
        }

        while (expression.hasNext) {
            breakable {
                val t = expression.next
                if (pathTokens != null) pathTokens.add(t)
                // Ignore all IgnoredWhitespace tokens
                if (Tokens.isIgnoredWhitespace(t))
                    break // continue
                if (Tokens.isValueWithType(t, ConfigValueType.STRING)) {
                    val v = Tokens.getValue(t)
                    // this is a quoted string; so any periods
                    // in here don't count as path separators
                    val s = v.transformToString
                    addPathText(buf, true, s)
                } else if (t eq Tokens.END) {
                    // ignore this; when parsing a file, it should not happen
                    // since we're parsing a token list rather than the main
                    // token iterator, and when parsing a path expression from the
                    // API, it's expected to have an END.
                } else {
                    // any periods outside of a quoted string count as separators
                    var text: String = null
                    if (Tokens.isValue(t)) {
                        // appending a number here may add
                        // a period, but we _do_ count those as path
                        // separators, because we basically want
                        // "foo 3.0bar" to parse as a string even
                        // though there's a number in it. The fact that
                        // we tokenize non-string values is largely an
                        // implementation detail.
                        val v = Tokens.getValue(t)
                        // We need to split the tokens on a . so that we can get sub-paths but still preserve
                        // the original path text when doing an insertion
                        if (pathTokens != null) {
                            pathTokens.remove(pathTokens.size - 1)
                            pathTokens.addAll(splitTokenOnPeriod(t, flavor))
                        }
                        text = v.transformToString
                    } else if (Tokens.isUnquotedText(t)) {
                        // the original path text when doing an insertion on ConfigNodeObjects
                        if (pathTokens != null) {
                            pathTokens.remove(pathTokens.size - 1)
                            pathTokens.addAll(splitTokenOnPeriod(t, flavor))
                        }
                        text = Tokens.getUnquotedText(t)
                    } else {
                        throw new ConfigException.BadPath(
                            origin,
                            originalText,
                            "Token not allowed in path expression: " + t +
                                " (you can double-quote this token if you really want it here)")
                    }
                    addPathText(buf, false, text)
                }
            }
        }
        val pb = new PathBuilder
        import scala.collection.JavaConverters._
        for (e <- buf.asScala) {
            if (e.sb.length == 0 && !e.canBeEmpty)
                throw new ConfigException.BadPath(origin, originalText,
                    "path has a leading, trailing, or two adjacent period '.' (use quoted \"\" empty string if you want an empty element)")
            else pb.appendKey(e.sb.toString)
        }
        return pb.result
    }

    private def splitTokenOnPeriod(t: Token, flavor: ConfigSyntax): ju.List[Token] = {
        val tokenText: String = t.tokenText
        if (tokenText == ".") return ju.Collections.singletonList(t)
        val splitToken = tokenText.split("\\.")
        val splitTokens = new ju.ArrayList[Token]
        for (s <- splitToken) {
            if (flavor eq ConfigSyntax.CONF)
                splitTokens.add(Tokens.newUnquotedText(t.origin, s))
            else splitTokens.add(Tokens.newString(t.origin, s, "\"" + s + "\""))
            splitTokens.add(Tokens.newUnquotedText(t.origin, "."))
        }
        if (tokenText.charAt(tokenText.length - 1) != '.') {
            splitTokens.remove(splitTokens.size - 1)
        }
        splitTokens
    }

    private def addPathText(buf: ju.List[PathParser.Element], wasQuoted: Boolean, newText: String): Unit = {
        val i = if (wasQuoted) -1 else newText.indexOf('.')
        val current = buf.get(buf.size - 1)
        if (i < 0) {
            // add to current path element
            current.sb.append(newText)
            // any empty quoted string means this element can now be empty.
            if (wasQuoted && current.sb.length == 0) current.canBeEmpty = true
        } else {
            // "buf" plus up to the period is an element
            current.sb.append(newText.substring(0, i))
            // then start a new element
            buf.add(new PathParser.Element("", false))
            // recurse to consume remainder of newText
            addPathText(buf, false, newText.substring(i + 1))
        }
    }
    // the idea is to see if the string has any chars or features
    // that might require the full parser to deal with.
    private def looksUnsafeForFastParser(s: String): Boolean = {
        var lastWasDot = true
        // start of path is also a "dot"
        val len = s.length
        if (s.isEmpty) return true
        if (s.charAt(0) == '.') return true
        if (s.charAt(len - 1) == '.') return true

        var i = 0
        var returnNow = false

        while (i < len) {
            breakable {
                val c = s.charAt(i)
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
                    lastWasDot = false
                    break // continue
                } else if (c == '.') {
                    if (lastWasDot) {
                        // ".." means we need to throw an error
                        returnNow = true
                        break // continue for return
                    } else {
                        lastWasDot = true
                    }

                } else if (c == '-') {
                    if (lastWasDot) {
                        returnNow = true
                        break // continue for return
                    } else {
                        break // continue
                    }
                } else {
                    returnNow = true
                    break // continue for return
                }
            }
            if (returnNow) {
                return true
            } else {
                i += 1
            }
            // normally increment here but we have short circuit returns
            // modeled by "returnNow
        }
        if (lastWasDot) return true
        else return false

        // old Java code
        //  while (i < len) {
        //      val c = s.charAt(i)
        //      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
        //          lastWasDot = false
        //          continue //todo: continue is not supported
        //      } else if (c == '.') {
        //          if (lastWasDot) return true
        //          // ".." means we need to throw an error
        //          lastWasDot = true
        //      } else if (c == '-') {
        //          if (lastWasDot) return true
        //          continue //todo: continue is not supported
        //      } else return true
        //      i += 1
        //  }
        //  if (lastWasDot) return true
        //  else return false
    }

    private def fastPathBuild(tail: Path, s: String, end: Int): Path = {
        // lastIndexOf takes last index it should look at, end - 1 not end
        val splitAt = s.lastIndexOf('.', end - 1)
        val tokens = new ju.ArrayList[Token]
        tokens.add(Tokens.newUnquotedText(null, s))
        // this works even if splitAt is -1; then we start the substring at 0
        val withOneMoreElement = new Path(s.substring(splitAt + 1, end), tail)
        if (splitAt < 0) {
            withOneMoreElement
        } else {
            fastPathBuild(withOneMoreElement, s, splitAt)
        }
    }

    // do something much faster than the full parser if
    // we just have something like "foo" or "foo.bar"
    private def speculativeFastParsePath(path: String): Path = {
        val s = ConfigImplUtil.unicodeTrim(path)
        if (looksUnsafeForFastParser(s)) null
        else fastPathBuild(null, s, s.length)
    }

}
