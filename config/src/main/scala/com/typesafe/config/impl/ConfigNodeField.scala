/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import java.{ util => ju }
import scala.collection.JavaConverters._

final class ConfigNodeField(_children: ju.Collection[AbstractConfigNode])
    extends AbstractConfigNode {
    val children = new ju.ArrayList[AbstractConfigNode](_children)

    override def tokens: ju.Collection[Token] = {
        val tokens = new ju.ArrayList[Token]
        for (child <- children.asScala) {
            tokens.addAll(child.tokens)
        }
        tokens
    }
    def replaceValue(newValue: AbstractConfigNodeValue): ConfigNodeField = {
        val childrenCopy =
            new ju.ArrayList[AbstractConfigNode](children)
        var i = 0
        while ({ i < childrenCopy.size }) {
            if (childrenCopy.get(i).isInstanceOf[AbstractConfigNodeValue]) {
                childrenCopy.set(i, newValue)
                return new ConfigNodeField(childrenCopy)
            }

            { i += 1; i - 1 }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value")
    }
    def value: AbstractConfigNodeValue = {
        var i = 0
        while ({ i < children.size }) {
            if (children.get(i).isInstanceOf[AbstractConfigNodeValue])
                return children.get(i).asInstanceOf[AbstractConfigNodeValue]

            { i += 1; i - 1 }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value")
    }
    def path: ConfigNodePath = {
        var i = 0
        while ({ i < children.size }) {
            if (children.get(i).isInstanceOf[ConfigNodePath])
                return children.get(i).asInstanceOf[ConfigNodePath]

            { i += 1; i - 1 }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a path")
    }
    protected def separator: Token = {
        for (child <- children.asScala) {
            if (child.isInstanceOf[ConfigNodeSingleToken]) {
                val t = child.asInstanceOf[ConfigNodeSingleToken].token
                if ((t eq Tokens.PLUS_EQUALS) || (t eq Tokens.COLON) || (t eq Tokens.EQUALS))
                    return t
            }
        }
        null
    }
    protected def comments: ju.List[String] = {
        val comments = new ju.ArrayList[String]
        for (child <- children.asScala) {
            if (child.isInstanceOf[ConfigNodeComment])
                comments.add(child.asInstanceOf[ConfigNodeComment].commentText)
        }
        comments
    }
}
