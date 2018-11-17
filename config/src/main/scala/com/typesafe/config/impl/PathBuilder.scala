/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ util => ju }
import util.control.Breaks._
import com.typesafe.config.ConfigException

final class PathBuilder private[impl] () {
    final private val keys = new ju.Stack[String]
    // the keys are kept "backward" (top of stack is end of path)
    private var resultPath: Path = null

    private def checkCanAppend(): Unit = {
        if (resultPath != null)
            throw new ConfigException.BugOrBroken(
                "Adding to PathBuilder after getting result")
    }

    private[impl] def appendKey(key: String): Unit = {
        checkCanAppend()
        keys.push(key)
    }

    private[impl] def appendPath(path: Path): Unit = {
        checkCanAppend()
        var first = path.first
        var remainder = path.remainder
        breakable {
            while (true) {
                keys.push(first)
                if (remainder != null) {
                    first = remainder.first
                    remainder = remainder.remainder
                } else break // break
            }
        }
    }

    private[impl] def result: Path = { // note: if keys is empty, we want to return null, which is a valid
        // empty path
        if (resultPath == null) {
            var remainder: Path = null
            while (!keys.isEmpty) {
                val key = keys.pop
                remainder = new Path(key, remainder)
            }
            resultPath = remainder
        }
        resultPath
    }
}
