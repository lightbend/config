package com.typesafe.config.impl

import com.typesafe.config.ConfigSyntax
import java.{ util => ju }
import scala.collection.JavaConverters._
import scala.util.control.Breaks._

final class ConfigNodeObject private[impl] (
    _children: ju.Collection[AbstractConfigNode]) extends ConfigNodeComplexValue(_children) {

    override def newNode(nodes: ju.Collection[AbstractConfigNode]) =
        new ConfigNodeObject(nodes)

    def hasValue(desiredPath: Path): Boolean = {
        for (node <- children.asScala) {
            if (node.isInstanceOf[ConfigNodeField]) {
                val field = node.asInstanceOf[ConfigNodeField]
                val key = field.path.value
                if (key == desiredPath || key.startsWith(desiredPath)) return true
                else if (desiredPath.startsWith(key)) {
                    if (field.value.isInstanceOf[ConfigNodeObject]) {
                        val obj =
                            field.value.asInstanceOf[ConfigNodeObject]
                        val remainingPath = desiredPath.subPath(key.length)
                        if (obj.hasValue(remainingPath)) return true
                    }
                }
            }
        }
        false
    }

    protected def changeValueOnPath(
        desiredPath: Path,
        value: AbstractConfigNodeValue,
        flavor: ConfigSyntax): ConfigNodeObject = {
        val childrenCopy =
            new ju.ArrayList[AbstractConfigNode](children)
        var seenNonMatching = false
        // Copy the value so we can change it to null but not modify the original parameter
        var valueCopy = value
        var i = childrenCopy.size - 1
        while (i >= 0) {
            breakable {
                if (childrenCopy.get(i).isInstanceOf[ConfigNodeSingleToken]) {
                    val t =
                        childrenCopy.get(i).asInstanceOf[ConfigNodeSingleToken].token
                    // Ensure that, when we are removing settings in JSON, we don't end up with a trailing comma
                    if ((flavor == ConfigSyntax.JSON) && !seenNonMatching && (t == Tokens.COMMA)) {
                        childrenCopy.remove(i)
                    }
                    break // continue
                } else if (!childrenCopy.get(i).isInstanceOf[ConfigNodeField]) {
                    break // continue
                }
                val node =
                    childrenCopy.get(i).asInstanceOf[ConfigNodeField]
                val key = node.path.value
                // Delete all multi-element paths that start with the desired path, since technically they are duplicates
                if ((valueCopy == null && key == desiredPath) || (key.startsWith(
                    desiredPath) && !(key == desiredPath))) {
                    childrenCopy.remove(i)
                    // Remove any whitespace or commas after the deleted setting
                    var j = i
                    breakable {
                        while (j < childrenCopy.size) {
                            if (childrenCopy.get(j).isInstanceOf[ConfigNodeSingleToken]) {
                                val t =
                                    childrenCopy.get(j).asInstanceOf[ConfigNodeSingleToken].token
                                if (Tokens.isIgnoredWhitespace(t) || (t == Tokens.COMMA)) {
                                    childrenCopy.remove(j)
                                    j -= 1
                                } else break // break
                            } else break // break

                            j += 1
                        }
                    }
                } else if (key == desiredPath) {
                    seenNonMatching = true
                    var indentedValue: AbstractConfigNodeValue = null
                    val before =
                        if (i - 1 > 0) childrenCopy.get(i - 1) else null
                    if (value.isInstanceOf[ConfigNodeComplexValue] && before
                        .isInstanceOf[ConfigNodeSingleToken] && Tokens
                        .isIgnoredWhitespace(
                            before.asInstanceOf[ConfigNodeSingleToken].token))
                        indentedValue =
                            value.asInstanceOf[ConfigNodeComplexValue].indentText(before)
                    else indentedValue = value
                    childrenCopy.set(i, node.replaceValue(indentedValue))
                    valueCopy = null
                } else if (desiredPath.startsWith(key)) {
                    seenNonMatching = true
                    if (node.value.isInstanceOf[ConfigNodeObject]) {
                        val remainingPath = desiredPath.subPath(key.length)
                        childrenCopy.set(
                            i,
                            node.replaceValue(
                                node.value
                                    .asInstanceOf[ConfigNodeObject]
                                    .changeValueOnPath(remainingPath, valueCopy, flavor)))
                        if (valueCopy != null && !(node == children.get(i))) valueCopy = null
                    }
                } else seenNonMatching = true
            } // end break for continue in Java - increment needed as it was a for loop
            i -= 1
        }
        new ConfigNodeObject(childrenCopy)
    }

    def setValueOnPath(
        desiredPath: String,
        value: AbstractConfigNodeValue): ConfigNodeObject =
        setValueOnPath(desiredPath, value, ConfigSyntax.CONF)

    def setValueOnPath(
        desiredPath: String,
        value: AbstractConfigNodeValue,
        flavor: ConfigSyntax): ConfigNodeObject = {
        val path = PathParser.parsePathNode(desiredPath, flavor)
        setValueOnPath(path, value, flavor)
    }

    private def setValueOnPath(
        desiredPath: ConfigNodePath,
        value: AbstractConfigNodeValue,
        flavor: ConfigSyntax): ConfigNodeObject = {
        val node =
            changeValueOnPath(desiredPath.value, value, flavor)
        // If the desired Path did not exist, add it
        if (!node.hasValue(desiredPath.value))
            return node.addValueOnPath(desiredPath, value, flavor)
        node
    }

    private def indentation: ju.Collection[AbstractConfigNode] = {
        var seenNewLine = false
        val indentation = new ju.ArrayList[AbstractConfigNode]
        if (children.isEmpty) return indentation
        var i = 0
        while (i < children.size) {
            if (!seenNewLine) {
                if (children.get(i).isInstanceOf[ConfigNodeSingleToken] && Tokens
                    .isNewline(
                        children.get(i).asInstanceOf[ConfigNodeSingleToken].token)) {
                    seenNewLine = true
                    indentation.add(new ConfigNodeSingleToken(Tokens.newLine(null)))
                }
            } else {
                if (children.get(i).isInstanceOf[ConfigNodeSingleToken] && Tokens
                    .isIgnoredWhitespace(
                        children.get(i).asInstanceOf[ConfigNodeSingleToken].token) && i + 1 < children.size && (children
                            .get(i + 1)
                            .isInstanceOf[ConfigNodeField] || children
                            .get(i + 1)
                            .isInstanceOf[ConfigNodeInclude])) {
                    // Return the indentation of the first setting on its own line
                    indentation.add(children.get(i))
                    return indentation
                }
            }
            i += 1
        }
        if (indentation.isEmpty)
            indentation.add(
                new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")))
        else {
            // Calculate the indentation of the ending curly-brace to get the indentation of the root object
            val last = children.get(children.size - 1)
            if (last.isInstanceOf[ConfigNodeSingleToken] && (last
                .asInstanceOf[ConfigNodeSingleToken]
                .token == Tokens.CLOSE_CURLY)) {
                val beforeLast = children.get(children.size - 2)
                var indent = ""
                if (beforeLast.isInstanceOf[ConfigNodeSingleToken] && Tokens
                    .isIgnoredWhitespace(
                        beforeLast.asInstanceOf[ConfigNodeSingleToken].token))
                    indent =
                        beforeLast.asInstanceOf[ConfigNodeSingleToken].token.tokenText
                indent += "  "
                indentation.add(
                    new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, indent)))
                return indentation
            }
        }
        // The object has no curly braces and is at the root level, so don't indent
        indentation
    }

    protected def addValueOnPath(
        desiredPath: ConfigNodePath,
        value: AbstractConfigNodeValue,
        flavor: ConfigSyntax): ConfigNodeObject = {
        val path = desiredPath.value
        val childrenCopy = new ju.ArrayList[AbstractConfigNode](children)
        val indentationCopy = new ju.ArrayList[AbstractConfigNode](indentation)
        // If the value we're inserting is a complex value, we'll need to indent it for insertion
        var indentedValue: AbstractConfigNodeValue = null
        if (value.isInstanceOf[ConfigNodeComplexValue] && !indentationCopy.isEmpty)
            indentedValue = value
                .asInstanceOf[ConfigNodeComplexValue]
                .indentText(indentationCopy.get(indentationCopy.size - 1))
        else indentedValue = value
        val sameLine = !(indentationCopy.size > 0 && indentationCopy.get(0)
            .isInstanceOf[ConfigNodeSingleToken] && Tokens.isNewline(
                indentationCopy.get(0).asInstanceOf[ConfigNodeSingleToken].token))
        // If the path is of length greater than one, see if the value needs to be added further down
        if (path.length > 1) {
            var i = children.size - 1
            while (i >= 0) {
                breakable {
                    if (!children.get(i).isInstanceOf[ConfigNodeField])
                        break // continue
                    val node = children.get(i).asInstanceOf[ConfigNodeField]
                    val key: Path = node.path.value
                    if (path.startsWith(key) && node.value.isInstanceOf[ConfigNodeObject]) {
                        val remainingPath = desiredPath.subPath(key.length)
                        val newValue = node.value.asInstanceOf[ConfigNodeObject]
                        childrenCopy.set(
                            i,
                            node.replaceValue(newValue.addValueOnPath(remainingPath, value, flavor)))
                        return new ConfigNodeObject(childrenCopy)
                    }
                } // end break for continue in Java - increment needed as it was a for loop
                i -= 1
            }
        }
        // Otherwise, construct the new setting
        val startsWithBrace = !children.isEmpty &&
            children.get(0).isInstanceOf[ConfigNodeSingleToken] &&
            (children.get(0).asInstanceOf[ConfigNodeSingleToken].token == Tokens.OPEN_CURLY)
        val newNodes = new ju.ArrayList[AbstractConfigNode]
        newNodes.addAll(indentationCopy)
        newNodes.add(desiredPath.first)
        newNodes.add(
            new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")))
        newNodes.add(new ConfigNodeSingleToken(Tokens.COLON))
        newNodes.add(
            new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")))
        if (path.length == 1) newNodes.add(indentedValue)
        else { // If the path is of length greater than one add the required new objects along the path
            val newObjectNodes =
                new ju.ArrayList[AbstractConfigNode]
            newObjectNodes.add(new ConfigNodeSingleToken(Tokens.OPEN_CURLY))
            if (indentationCopy.isEmpty)
                newObjectNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)))
            newObjectNodes.addAll(indentationCopy)
            newObjectNodes.add(new ConfigNodeSingleToken(Tokens.CLOSE_CURLY))
            val newObject = new ConfigNodeObject(newObjectNodes)
            newNodes.add(
                newObject.addValueOnPath(desiredPath.subPath(1), indentedValue, flavor))
        }
        // Combine these two cases so that we only have to iterate once
        if ((flavor == ConfigSyntax.JSON) || startsWithBrace || sameLine) {
            var i = childrenCopy.size - 1
            breakable {
                while (i >= 0) { // If we are in JSON or are adding a setting on the same line, we need to add a comma to the
                    // last setting
                    if (((flavor == ConfigSyntax.JSON) || sameLine) && childrenCopy
                        .get(i)
                        .isInstanceOf[ConfigNodeField]) {
                        if (i + 1 >= childrenCopy.size ||
                            !(childrenCopy.get(i + 1).isInstanceOf[ConfigNodeSingleToken] &&
                                childrenCopy
                                .get(i + 1)
                                .asInstanceOf[ConfigNodeSingleToken]
                                .token == Tokens.COMMA))
                            childrenCopy.add(i + 1, new ConfigNodeSingleToken(Tokens.COMMA))
                        break // break
                    }
                    // Add the value into the copy of the children map, keeping any whitespace/newlines
                    // before the close curly brace
                    if (startsWithBrace &&
                        childrenCopy.get(i).isInstanceOf[ConfigNodeSingleToken] &&
                        childrenCopy
                        .get(i)
                        .asInstanceOf[ConfigNodeSingleToken]
                        .token == Tokens.CLOSE_CURLY) {
                        val previous = childrenCopy.get(i - 1)
                        if (previous.isInstanceOf[ConfigNodeSingleToken] &&
                            Tokens.isNewline(
                                previous.asInstanceOf[ConfigNodeSingleToken].token)) {
                            childrenCopy.add(i - 1, new ConfigNodeField(newNodes))
                            i -= 1
                        } else if (previous.isInstanceOf[ConfigNodeSingleToken] &&
                            Tokens.isIgnoredWhitespace(
                                previous.asInstanceOf[ConfigNodeSingleToken].token)) {
                            val beforePrevious = childrenCopy.get(i - 2)
                            if (sameLine) {
                                childrenCopy.add(i - 1, new ConfigNodeField(newNodes))
                                i -= 1
                            } else if (beforePrevious.isInstanceOf[ConfigNodeSingleToken] &&
                                Tokens.isNewline(
                                    beforePrevious.asInstanceOf[ConfigNodeSingleToken].token)) {
                                childrenCopy.add(i - 2, new ConfigNodeField(newNodes))
                                i -= 2
                            } else childrenCopy.add(i, new ConfigNodeField(newNodes))
                        } else childrenCopy.add(i, new ConfigNodeField(newNodes))
                    }
                    i -= 1
                }
            }
        }
        if (!startsWithBrace)
            if (!childrenCopy.isEmpty &&
                childrenCopy.get(childrenCopy.size - 1).isInstanceOf[ConfigNodeSingleToken] &&
                Tokens.isNewline(childrenCopy.get(childrenCopy.size - 1)
                    .asInstanceOf[ConfigNodeSingleToken].token))
                childrenCopy.add(childrenCopy.size - 1, new ConfigNodeField(newNodes))
            else childrenCopy.add(new ConfigNodeField(newNodes))
        new ConfigNodeObject(childrenCopy)
    }

    def removeValueOnPath(
        desiredPath: String,
        flavor: ConfigSyntax): ConfigNodeObject = {
        val path = PathParser.parsePathNode(desiredPath, flavor).value
        changeValueOnPath(path, null, flavor)
    }
}
