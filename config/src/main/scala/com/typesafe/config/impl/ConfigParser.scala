/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.{ util => ju }

import scala.collection.JavaConverters._
import scala.util.control.Breaks._

import com.typesafe.config._

object ConfigParser {
    private[impl] def parse(
        document: ConfigNodeRoot,
        origin: ConfigOrigin,
        options: ConfigParseOptions,
        includeContext: ConfigIncludeContext): AbstractConfigValue = {
        val context = new ParseContext(
            options.getSyntax,
            origin,
            document,
            SimpleIncluder.makeFull(options.getIncluder),
            includeContext)
        context.parse
    }
    private object ParseContext {
        private def createValueUnderPath(
            path: Path,
            value: AbstractConfigValue): AbstractConfigObject = {
            // for path foo.bar, we are creating
            // { "foo" : { "bar" : value } }
            val keys = new ju.ArrayList[String]
            var key = path.first
            var remaining = path.remainder
            breakable {
                while (key != null) {
                    keys.add(key)
                    if (remaining == null) {
                        break // break
                    } else {
                        key = remaining.first
                        remaining = remaining.remainder
                    }
                }
            }
            // the withComments(null) is to ensure comments are only
            // on the exact leaf node they apply to.
            // a comment before "foo.bar" applies to the full setting
            // "foo.bar" not also to "foo"
            val i = keys.listIterator(keys.size)
            val deepest = i.previous
            var o = new SimpleConfigObject(
                value.origin.withComments(null),
                ju.Collections.singletonMap[String, AbstractConfigValue](deepest, value))
            while (i.hasPrevious) {
                val m =
                    ju.Collections.singletonMap[String, AbstractConfigValue](i.previous, o)
                o = new SimpleConfigObject(value.origin.withComments(null), m)
            }
            o
        }
    }

    final private class ParseContext private[impl] (
        val flavor: ConfigSyntax,
        val baseOrigin: ConfigOrigin,
        val document: ConfigNodeRoot,
        val includer: FullIncluder,
        val includeContext: ConfigIncludeContext) {

        private var lineNumber = 1
        final private var pathStack = new ju.LinkedList[Path]
        // the number of lists we are inside; this is used to detect the "cannot
        // generate a reference to a list element" problem, and once we fix that
        // problem we should be able to get rid of this variable.
        private[impl] var arrayCount = 0
        // merge a bunch of adjacent values into one
        // value; change unquoted text into a string
        // value.
        private def parseConcatenation(
            n: ConfigNodeConcatenation): AbstractConfigValue = {
            // this trick is not done in JSON
            if (flavor eq ConfigSyntax.JSON)
                throw new ConfigException.BugOrBroken(
                    "Found a concatenation node in JSON")
            val values = new ju.ArrayList[AbstractConfigValue]
            for (node <- n.children.asScala) {
                var v: AbstractConfigValue = null
                if (node.isInstanceOf[AbstractConfigNodeValue]) {
                    v = parseValue(node.asInstanceOf[AbstractConfigNodeValue], null)
                    values.add(v)
                }
            }
            ConfigConcatenation.concatenate(values)
        }
        private def lineOrigin: SimpleConfigOrigin =
            baseOrigin
                .asInstanceOf[SimpleConfigOrigin]
                .withLineNumber(lineNumber)
        private def parseError(message: String): ConfigException.Parse = parseError(message, null)
        private def parseError(
            message: String,
            cause: Throwable): ConfigException.Parse =
            new ConfigException.Parse(lineOrigin, message, cause)
        private def fullCurrentPath = {
            // pathStack has top of stack at front
            if (pathStack.isEmpty)
                throw new ConfigException.BugOrBroken(
                    "Bug in parser; tried to get current path when at root")
            else new Path(pathStack.descendingIterator)
        }

        private def parseValue(
            n: AbstractConfigNodeValue,
            comments: ju.List[String]): AbstractConfigValue = {
            var v: AbstractConfigValue = null
            val startingArrayCount = arrayCount
            if (n.isInstanceOf[ConfigNodeSimpleValue])
                v = n.asInstanceOf[ConfigNodeSimpleValue].value
            else if (n.isInstanceOf[ConfigNodeObject])
                v = parseObject(n.asInstanceOf[ConfigNodeObject])
            else if (n.isInstanceOf[ConfigNodeArray])
                v = parseArray(n.asInstanceOf[ConfigNodeArray])
            else if (n.isInstanceOf[ConfigNodeConcatenation])
                v = parseConcatenation(n.asInstanceOf[ConfigNodeConcatenation])
            else
                throw parseError(
                    "Expecting a value but got wrong node type: " + n.getClass)
            if (comments != null && !comments.isEmpty) {
                v = v.withOrigin(
                    v.origin.prependComments(new ju.ArrayList[String](comments)))
                comments.clear()
            }
            if (arrayCount != startingArrayCount)
                throw new ConfigException.BugOrBroken(
                    "Bug in config parser: unbalanced array count")
            v
        }
        private def parseInclude(
            values: ju.Map[String, AbstractConfigValue],
            n: ConfigNodeInclude): Unit = {
            val isRequired = n.isRequired
            val cic = includeContext.setParseOptions(
                includeContext.parseOptions.setAllowMissing(!isRequired))
            var obj: AbstractConfigObject = null
            n.kind.name match {
                case "URL" =>
                    var url: URL = null
                    try url = new URL(n.name)
                    catch {
                        case e: MalformedURLException =>
                            throw parseError("include url() specifies an invalid URL: " + n.name, e)
                    }
                    obj = includer.includeURL(cic, url).asInstanceOf[AbstractConfigObject]
                case "FILE" =>
                    obj = includer
                        .includeFile(cic, new File(n.name))
                        .asInstanceOf[AbstractConfigObject]
                case "CLASSPATH" =>
                    obj = includer
                        .includeResources(cic, n.name)
                        .asInstanceOf[AbstractConfigObject]
                case "HEURISTIC" =>
                    obj = includer.include(cic, n.name).asInstanceOf[AbstractConfigObject]
                case _ =>
                    throw new ConfigException.BugOrBroken("should not be reached")
            }
            // we really should make this work, but for now throwing an
            // exception is better than producing an incorrect result.
            // See https://github.com/lightbend/config/issues/160
            if (arrayCount > 0 && (obj.resolveStatus ne ResolveStatus.RESOLVED)) throw parseError(
                "Due to current limitations of the config parser, when an include statement is nested inside a list value, " + "${} substitutions inside the included file cannot be resolved correctly. Either move the include outside of the list value or " + "remove the ${} statements from the included file.")
            if (!pathStack.isEmpty) {
                val prefix = fullCurrentPath
                obj = obj.relativized(prefix)
            }
            for (key <- obj.keySet.asScala) {
                val v = obj.get(key)
                val existing = values.get(key)
                if (existing != null) values.put(key, v.withFallback(existing)) else values.put(key, v)
            }
        }

        private def parseObject(n: ConfigNodeObject): AbstractConfigObject = {
            val values =
                new ju.HashMap[String, AbstractConfigValue]
            val objectOrigin = lineOrigin
            var lastWasNewline = false
            val nodes =
                new ju.ArrayList[AbstractConfigNode](n.children)
            val comments = new ju.ArrayList[String]
            var i = 0
            while (i < nodes.size) {
                val node = nodes.get(i)
                if (node.isInstanceOf[ConfigNodeComment]) {
                    lastWasNewline = false
                    comments.add(node.asInstanceOf[ConfigNodeComment].commentText)
                } else if (node.isInstanceOf[ConfigNodeSingleToken] && Tokens.isNewline(
                    node.asInstanceOf[ConfigNodeSingleToken].token)) {
                    lineNumber += 1
                    if (lastWasNewline) { // Drop all comments if there was a blank line and start a new comment block
                        comments.clear()
                    }
                    lastWasNewline = true
                } else if ((flavor ne ConfigSyntax.JSON) && node
                    .isInstanceOf[ConfigNodeInclude]) {
                    parseInclude(values, node.asInstanceOf[ConfigNodeInclude])
                    lastWasNewline = false
                } else if (node.isInstanceOf[ConfigNodeField]) {
                    lastWasNewline = false
                    val path = node.asInstanceOf[ConfigNodeField].path.value
                    comments.addAll(node.asInstanceOf[ConfigNodeField].comments)
                    // path must be on-stack while we parse the value
                    pathStack.push(path)
                    if (node
                        .asInstanceOf[ConfigNodeField]
                        .separator eq Tokens.PLUS_EQUALS) { // we really should make this work, but for now throwing
                        // an exception is better than producing an incorrect
                        // result. See
                        // https://github.com/lightbend/config/issues/160
                        if (arrayCount > 0)
                            throw parseError(
                                "Due to current limitations of the config parser, += does not work nested inside a list. " + "+= expands to a ${} substitution and the path in ${} cannot currently refer to list elements. " + "You might be able to move the += outside of the list and then refer to it from inside the list with ${}.")
                        // because we will put it in an array after the fact so
                        // we want this to be incremented during the parseValue
                        // below in order to throw the above exception.
                        arrayCount += 1
                    }
                    var valueNode: AbstractConfigNodeValue = null
                    var newValue: AbstractConfigValue = null
                    valueNode = node.asInstanceOf[ConfigNodeField].value
                    // comments from the key token go to the value token
                    newValue = parseValue(valueNode, comments)
                    if (node
                        .asInstanceOf[ConfigNodeField]
                        .separator eq Tokens.PLUS_EQUALS) {
                        arrayCount -= 1
                        val concat =
                            new ju.ArrayList[AbstractConfigValue](2)
                        val previousRef = new ConfigReference(
                            newValue.origin,
                            new SubstitutionExpression(
                                fullCurrentPath,
                                true /* optional */
                            ))
                        val list = new SimpleConfigList(
                            newValue.origin,
                            ju.Collections.singletonList(newValue))
                        concat.add(previousRef)
                        concat.add(list)
                        newValue = ConfigConcatenation.concatenate(concat)
                    }
                    // Grab any trailing comments on the same line
                    if (i < nodes.size - 1) {
                        i += 1
                        breakable {
                            while (i < nodes.size) {
                                if (nodes.get(i).isInstanceOf[ConfigNodeComment]) {
                                    val comment =
                                        nodes.get(i).asInstanceOf[ConfigNodeComment]
                                    newValue = newValue.withOrigin(
                                        newValue.origin
                                            .appendComments(ju.Collections.singletonList(comment.commentText)))
                                    break // break
                                } else if (nodes.get(i).isInstanceOf[ConfigNodeSingleToken]) {
                                    val curr =
                                        nodes.get(i).asInstanceOf[ConfigNodeSingleToken]
                                    if ((curr.token eq Tokens.COMMA) || Tokens
                                        .isIgnoredWhitespace(curr.token)) {
                                        // keep searching, as there could still be a comment
                                    } else {
                                        i -= 1
                                        break // break
                                    }
                                } else {
                                    i -= 1
                                    break // break
                                }
                                i += 1
                            }
                        }
                    }
                    pathStack.pop
                    val key = path.first
                    val remaining = path.remainder
                    if (remaining == null) {
                        val existing = values.get(key)
                        if (existing != null) { // In strict JSON, dups should be an error; while in
                            // our custom config language, they should be merged
                            // if the value is an object (or substitution that
                            // could become an object).
                            if (flavor eq ConfigSyntax.JSON)
                                throw parseError(
                                    "JSON does not allow duplicate fields: '" + key + "' was already seen at " + existing.origin.description)
                            else newValue = newValue.withFallback(existing)
                        }
                        values.put(key, newValue)
                    } else {
                        if (flavor eq ConfigSyntax.JSON)
                            throw new ConfigException.BugOrBroken(
                                "somehow got multi-element path in JSON mode")
                        var obj =
                            ParseContext.createValueUnderPath(remaining, newValue)
                        val existing = values.get(key)
                        if (existing != null) obj = obj.withFallback(existing)
                        values.put(key, obj)
                    }
                }
                i += 1
            }
            new SimpleConfigObject(objectOrigin, values)
        }

        private def parseArray(n: ConfigNodeArray) = {
            arrayCount += 1
            val arrayOrigin = lineOrigin
            val values = new ju.ArrayList[AbstractConfigValue]
            var lastWasNewLine = false
            val comments = new ju.ArrayList[String]
            var v: AbstractConfigValue = null
            for (node <- n.children.asScala) {
                if (node.isInstanceOf[ConfigNodeComment]) {
                    comments.add(node.asInstanceOf[ConfigNodeComment].commentText)
                    lastWasNewLine = false
                } else if (node.isInstanceOf[ConfigNodeSingleToken] && Tokens.isNewline(
                    node.asInstanceOf[ConfigNodeSingleToken].token)) {
                    lineNumber += 1
                    if (lastWasNewLine && v == null) comments.clear()
                    else if (v != null) {
                        values.add(
                            v.withOrigin(
                                v.origin.appendComments(new ju.ArrayList[String](comments))))
                        comments.clear()
                        v = null
                    }
                    lastWasNewLine = true
                } else if (node.isInstanceOf[AbstractConfigNodeValue]) {
                    lastWasNewLine = false
                    if (v != null) {
                        values.add(
                            v.withOrigin(
                                v.origin.appendComments(new ju.ArrayList[String](comments))))
                        comments.clear()
                    }
                    v =
                        parseValue(node.asInstanceOf[AbstractConfigNodeValue], comments)
                }
            }
            // There shouldn't be any comments at this point, but add them just in case
            if (v != null) values.add(
                v.withOrigin(v.origin.appendComments(new ju.ArrayList[String](comments))))
            arrayCount -= 1
            new SimpleConfigList(arrayOrigin, values)
        }
        private[impl] def parse: AbstractConfigValue = {
            var result: AbstractConfigValue = null
            val comments = new ju.ArrayList[String]
            var lastWasNewLine = false
            breakable {
                for (node <- document.children.asScala) {
                    if (node.isInstanceOf[ConfigNodeComment]) {
                        comments.add(node.asInstanceOf[ConfigNodeComment].commentText)
                        lastWasNewLine = false
                    } else if (node.isInstanceOf[ConfigNodeSingleToken]) {
                        val t = node.asInstanceOf[ConfigNodeSingleToken].token
                        if (Tokens.isNewline(t)) {
                            lineNumber += 1
                            if (lastWasNewLine && result == null) comments.clear()
                            else if (result != null) {
                                result = result.withOrigin(
                                    result.origin
                                        .appendComments(new ju.ArrayList[String](comments)))
                                comments.clear()
                                break // break
                            }
                            lastWasNewLine = true
                        }
                    } else if (node.isInstanceOf[ConfigNodeComplexValue]) {
                        result = parseValue(node.asInstanceOf[ConfigNodeComplexValue], comments)
                        lastWasNewLine = false
                    }
                }
            }
            result
        }
    }
}
