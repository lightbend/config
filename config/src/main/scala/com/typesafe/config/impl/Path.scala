/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ util => ju }
import com.typesafe.config.ConfigException
import util.control.Breaks._

object Path {
    // this doesn't have a very precise meaning, just to reduce
    // noise from quotes in the rendered path for average cases
    private[impl] def hasFunkyChars(s: String): Boolean = {
        val length = s.length
        if (length == 0) return false
        var i = 0
        while (i < length) {
            breakable {
                val c = s.charAt(i)
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                    break // continue
                } else {
                    return true
                }
            }
            i += 1
        }
        return false
    }
    def newKey(key: String): Path = new Path(key, null: Path)
    def newPath(path: String): Path = PathParser.parsePath(path)

    private def convert(i: ju.Iterator[Path]): Seq[String] = {
        import scala.collection.JavaConverters._
        i.asScala.toSeq.map(_.first)
    }

    /**
     *
     * @return path minus the first element or null if no more elements
     */
    private def create(elements: String*): (String, Path) = {
        val first = if (elements.length > 0) elements(0) else null
        val remainder = if (elements.length > 1) {
            val pb = new PathBuilder
            var i = 1
            while (i < elements.length) {
                pb.appendKey(elements(i))
                i += 1
            }
            pb.result
        } else null
        (first, remainder)
    }

    private def create(i: ju.Iterator[Path]): (String, Path) =
        if (i.hasNext) {
            val firstPath = i.next
            val pb = new PathBuilder
            if (firstPath.remainder != null)
                pb.appendPath(firstPath.remainder)
            while (i.hasNext)
                pb.appendPath(i.next)
            (firstPath.first, pb.result)
        } else {
            // only throw error from primary constructor
            (null, null)
        }
}

@throws(classOf[ConfigException])
final class Path(val first: String, val remainder: Path) {
    if (first == null)
        throw new ConfigException.BugOrBroken("empty path")

    // added as private constructor helper
    private def this(tuple: (String, Path)) = this(tuple._1, tuple._2)

    def this(elements: String*) = this(Path.create(elements: _*))

    // append all the paths in the iterator together into one path
    def this(i: ju.Iterator[Path]) = this(Path.create(i))

    // append all the paths in the list together into one path
    def this(pathsToConcat: ju.List[Path]) = this(pathsToConcat.iterator)

    /**
     *
     * @return path minus the last element or null if we have just one element
     */
    private[impl] def parent: Path = {
        if (remainder == null) return null
        val pb = new PathBuilder
        var p = this
        while (p.remainder != null) {
            pb.appendKey(p.first)
            p = p.remainder
        }
        pb.result
    }

    /**
     *
     * @return last element in the path
     */
    private[impl] def last: String = {
        var p = this
        while (p.remainder != null)
            p = p.remainder
        p.first
    }

    private[impl] def prepend(toPrepend: Path) = {
        val pb = new PathBuilder
        pb.appendPath(toPrepend)
        pb.appendPath(this)
        pb.result
    }

    private[impl] def length = {
        var count = 1
        var p = remainder
        while (p != null) {
            count += 1
            p = p.remainder
        }
        count
    }

    private[impl] def subPath(removeFromFront: Int): Path = {
        var count = removeFromFront
        var p = this
        while (p != null && count > 0) {
            count -= 1
            p = p.remainder
        }
        p
    }

    private[impl] def subPath(firstIndex: Int, lastIndex: Int): Path = {
        if (lastIndex < firstIndex)
            throw new ConfigException.BugOrBroken("bad call to subPath")
        var from = subPath(firstIndex)
        val pb = new PathBuilder
        var count = lastIndex - firstIndex
        while (count > 0) {
            count -= 1
            pb.appendKey(from.first)
            from = from.remainder
            if (from == null)
                throw new ConfigException.BugOrBroken("subPath lastIndex out of range " + lastIndex)
        }
        pb.result
    }

    private[impl] def startsWith(other: Path): Boolean = {
        var myRemainder = this
        var otherRemainder = other
        if (otherRemainder.length <= myRemainder.length) {
            while (otherRemainder != null) {
                if (!(otherRemainder.first == myRemainder.first)) return false
                myRemainder = myRemainder.remainder
                otherRemainder = otherRemainder.remainder
            }
            return true
        }
        false
    }

    override def equals(other: Any): Boolean =
        if (other.isInstanceOf[Path]) {
            val that = other.asInstanceOf[Path]
            this.first == that.first && ConfigImplUtil.equalsHandlingNull(this.remainder, that.remainder)
        } else false

    override def hashCode: Int =
        41 * (41 + first.hashCode) + (if (remainder == null) 0
        else remainder.hashCode)

    private def appendToStringBuilder(sb: StringBuilder): Unit = {
        if (Path.hasFunkyChars(first) || first.isEmpty)
            sb.append(ConfigImplUtil.renderJsonString(first))
        else sb.append(first)
        if (remainder != null) {
            sb.append(".")
            remainder.appendToStringBuilder(sb)
        }
    }
    override def toString: String = {
        val sb = new StringBuilder
        sb.append("Path(")
        appendToStringBuilder(sb)
        sb.append(")")
        sb.toString
    }
    /**
     * toString() is a debugging-oriented version while this is an
     * error-message-oriented human-readable one.
     */
    private[impl] def render = {
        val sb = new StringBuilder
        appendToStringBuilder(sb)
        sb.toString
    }
}
