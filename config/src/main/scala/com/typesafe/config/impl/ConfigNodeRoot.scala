package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigSyntax
import java.{ util => ju }

final class ConfigNodeRoot private[impl] (
    children: ju.Collection[AbstractConfigNode],
    val origin: ConfigOrigin) extends ConfigNodeComplexValue(children) {

    override def newNode(nodes: ju.Collection[AbstractConfigNode]) =
        throw new ConfigException.BugOrBroken("Tried to indent the root object")

    private[impl] def value: ConfigNodeComplexValue = {
        import scala.collection.JavaConverters._
        for (node <- children.asScala) {
            if (node.isInstanceOf[ConfigNodeComplexValue])
                return node.asInstanceOf[ConfigNodeComplexValue]
        }
        throw new ConfigException.BugOrBroken(
            "ConfigNodeRoot did not contain a value")
    }
    protected def setValue(
        desiredPath: String,
        value: AbstractConfigNodeValue,
        flavor: ConfigSyntax): ConfigNodeRoot = {
        val childrenCopy =
            new ju.ArrayList[AbstractConfigNode](children)
        var i = 0
        while ({ i < childrenCopy.size }) {
            val node = childrenCopy.get(i)
            if (node.isInstanceOf[ConfigNodeComplexValue])
                if (node.isInstanceOf[ConfigNodeArray])
                    throw new ConfigException.WrongType(
                        origin,
                        "The ConfigDocument had an array at the root level, and values cannot be modified inside an array.")
                else if (node.isInstanceOf[ConfigNodeObject]) {
                    if (value == null)
                        childrenCopy.set(
                            i,
                            node
                                .asInstanceOf[ConfigNodeObject]
                                .removeValueOnPath(desiredPath, flavor))
                    else
                        childrenCopy.set(
                            i,
                            node
                                .asInstanceOf[ConfigNodeObject]
                                .setValueOnPath(desiredPath, value, flavor))
                    return new ConfigNodeRoot(childrenCopy, origin)
                }

            { i += 1; i - 1 }
        }
        throw new ConfigException.BugOrBroken(
            "ConfigNodeRoot did not contain a value")
    }
    protected def hasValue(desiredPath: String): Boolean = {
        val path = PathParser.parsePath(desiredPath)
        val childrenCopy =
            new ju.ArrayList[AbstractConfigNode](children)
        var i = 0
        while ({ i < childrenCopy.size }) {
            val node = childrenCopy.get(i)
            if (node.isInstanceOf[ConfigNodeComplexValue])
                if (node.isInstanceOf[ConfigNodeArray])
                    throw new ConfigException.WrongType(
                        origin,
                        "The ConfigDocument had an array at the root level, and values cannot be modified inside an array.")
                else if (node.isInstanceOf[ConfigNodeObject])
                    return node.asInstanceOf[ConfigNodeObject].hasValue(path)

            { i += 1; i - 1 }
        }
        throw new ConfigException.BugOrBroken(
            "ConfigNodeRoot did not contain a value")
    }
}
