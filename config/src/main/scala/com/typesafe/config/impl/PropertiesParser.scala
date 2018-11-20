/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.IOException
import java.io.Reader
import java.{ util => ju }
import java.util.Collections
import java.util.Comparator
import java.util.Properties
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin

object PropertiesParser {
    @throws[IOException]
    private[impl] def parse(
        reader: Reader,
        origin: ConfigOrigin) = {
        val props = new Properties
        props.load(reader)
        fromProperties(origin, props)
    }
    private[impl] def lastElement(path: String) = {
        val i = path.lastIndexOf('.')
        if (i < 0) path else path.substring(i + 1)
    }
    private[impl] def exceptLastElement(path: String) = {
        val i = path.lastIndexOf('.')
        if (i < 0) null else path.substring(0, i)
    }
    private[impl] def pathFromPropertyKey(key: String) = {
        var last = lastElement(key)
        var exceptLast = exceptLastElement(key)
        var path = new Path(last, null: Path)
        while ({ exceptLast != null }) {
            last = lastElement(exceptLast)
            exceptLast = exceptLastElement(exceptLast)
            path = new Path(last, path)
        }
        path
    }

    // Scala Port: Reworked the following a bit from the original to get it to compile
    // The types seems to be pretty much fixed so the generics were removed

    private[impl] def fromProperties(
        origin: ConfigOrigin,
        props: Properties): AbstractConfigObject =
        fromEntrySet(origin, props.asInstanceOf[ju.Map[String, String]])

    def fromStringMap(
        origin: ConfigOrigin,
        stringMap: ju.Map[String, String]): AbstractConfigObject =
        fromEntrySet(origin, stringMap)

    private def fromEntrySet(
        origin: ConfigOrigin,
        entries: ju.Map[String, String]): AbstractConfigObject = {
        val pathMap: ju.Map[Path, AnyRef] = getPathMap(entries)
        fromPathMap(origin, pathMap, true /* from properties */ )
    }

    def getPathMap(
        map: ju.Map[_ <: AnyRef, _ <: AnyRef]): ju.Map[Path, AnyRef] = {
        val pathMap = new ju.HashMap[Path, AnyRef]
        for (entry <- map.entrySet().asScala) {
            val key = entry.getKey
            if (key.isInstanceOf[String]) {
                val path = pathFromPropertyKey(key.asInstanceOf[String])
                pathMap.put(path, entry.getValue)
            }
        }
        pathMap
    }

    def fromPathMap(
        origin: ConfigOrigin,
        pathExpressionMap: ju.Map[_, _]): AbstractConfigObject = {
        val pathMap = new ju.HashMap[Path, AnyRef]
        for (entry <- pathExpressionMap.entrySet.asScala) {
            val keyObj = entry.getKey
            if (!keyObj.isInstanceOf[String])
                throw new ConfigException.BugOrBroken(
                    "Map has a non-string as a key, expecting a path expression as a String")
            val path = Path.newPath(keyObj.asInstanceOf[String])
            pathMap.put(path, entry.getValue.asInstanceOf[AnyRef])
        }
        fromPathMap(origin, pathMap, false)
    }

    def fromPathMap(
        origin: ConfigOrigin,
        pathMap: ju.Map[Path, AnyRef],
        convertedFromProperties: Boolean): AbstractConfigObject = {
        /*
     * First, build a list of paths that will have values, either string or
     * object values.
     */
        val scopePaths = new ju.HashSet[Path]
        val valuePaths = new ju.HashSet[Path]
        for (path <- pathMap.keySet.asScala) { // add value's path
            valuePaths.add(path)
            // all parent paths are objects
            var next = path.parent
            while ({ next != null }) {
                scopePaths.add(next)
                next = next.parent
            }
        }
        if (convertedFromProperties) {
            /*
       * If any string values are also objects containing other values,
       * drop those string values - objects "win".
       */
            valuePaths.removeAll(scopePaths)
        } else {
            /* If we didn't start out as properties, then this is an error. */
            for (path <- valuePaths.asScala) {
                if (scopePaths.contains(path))
                    throw new ConfigException.BugOrBroken(
                        "In the map, path '" + path.render + "' occurs as both the parent object of a value and as a value. " +
                            "Because Map has no defined ordering, this is a broken situation.")
            }
        }
        /*
     * Create maps for the object-valued values.
     */
        val root = new ju.HashMap[String, AbstractConfigValue]
        val scopes = new ju.HashMap[Path, ju.Map[String, AbstractConfigValue]]
        for (path <- scopePaths.asScala) {
            val scope = new ju.HashMap[String, AbstractConfigValue]
            scopes.put(path, scope)
        }
        /* Store string values in the associated scope maps */
        for (path <- valuePaths.asScala) {
            val parentPath = path.parent
            val parent =
                if (parentPath != null) scopes.get(parentPath) else root
            val last = path.last
            val rawValue = pathMap.get(path)
            var value: AbstractConfigValue = null
            if (convertedFromProperties)
                if (rawValue.isInstanceOf[String]) {
                    value = new ConfigString.Quoted(origin, rawValue.asInstanceOf[String])
                } else { // silently ignore non-string values in Properties
                    value = null
                }
            else
                value = ConfigImpl.fromAnyRef(
                    pathMap.get(path),
                    origin,
                    FromMapMode.KEYS_ARE_PATHS)
            if (value != null) parent.put(last, value)
        }
        /*
     * Make a list of scope paths from longest to shortest, so children go
     * before parents.
     */
        val sortedScopePaths = new ju.ArrayList[Path]
        sortedScopePaths.addAll(scopePaths)
        // sort descending by length
        Collections.sort(
            sortedScopePaths,
            new Comparator[Path]() {
                override def compare(a: Path, b: Path): Int = { // Path.length() is O(n) so in theory this sucks
                    // but in practice we can make Path precompute length
                    // if it ever matters.
                    b.length - a.length
                }
            })
        /*
     * Create ConfigObject for each scope map, working from children to
     * parents to avoid modifying any already-created ConfigObject. This is
     * where we need the sorted list.
     */
        for (scopePath <- sortedScopePaths.asScala) {
            val scope = scopes.get(scopePath)
            val parentPath = scopePath.parent
            val parent =
                if (parentPath != null) scopes.get(parentPath) else root
            val o = new SimpleConfigObject(
                origin,
                scope,
                ResolveStatus.RESOLVED,
                false /* ignoresFallbacks */
            )
            parent.put(scopePath.last, o)
        }
        // return root config object
        new SimpleConfigObject(origin, root, ResolveStatus.RESOLVED, false)
    }
}
