/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import java.{ util => ju }

final class ConfigNodePath private[impl] (
    path: Path,
    tokensArg: ju.Collection[Token])
    extends AbstractConfigNode {
    override def tokens(): ju.Collection[Token] = tokensArg
    private[impl] def value: Path = path
    private[impl] def subPath(toRemove: Int): ConfigNodePath = {
        var periodCount = 0
        val tokensCopy = new ju.ArrayList[Token](tokensArg)
        var i = 0
        while ({ i < tokensCopy.size }) {
            if (Tokens.isUnquotedText(tokensCopy.get(i)) && tokensCopy
                .get(i)
                .tokenText == ".") { periodCount += 1; periodCount - 1 }
            if (periodCount == toRemove)
                return new ConfigNodePath(
                    path.subPath(toRemove),
                    tokensCopy.subList(i + 1, tokensCopy.size))

            { i += 1; i - 1 }
        }
        throw new ConfigException.BugOrBroken(
            "Tried to remove too many elements from a Path node")
    }
    private[impl] def first: ConfigNodePath = {
        val tokensCopy = new ju.ArrayList[Token](tokens)
        var i = 0
        while ({ i < tokensCopy.size }) {
            if (Tokens.isUnquotedText(tokensCopy.get(i)) && tokensCopy
                .get(i)
                .tokenText == ".")
                return new ConfigNodePath(path.subPath(0, 1), tokensCopy.subList(0, i))

            { i += 1; i - 1 }
        }
        this
    }
}
