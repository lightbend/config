/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

import com.twitter_typesafe.config.ConfigException;

import java.util.ArrayList;
import java.util.Collection;

final class ConfigNodePath extends AbstractConfigNode {
    final private Path path;
    final ArrayList<Token> tokens;
    ConfigNodePath(Path path, Collection<Token> tokens) {
        this.path = path;
        this.tokens = new ArrayList<Token>(tokens);
    }

    @Override
    protected Collection<Token> tokens() {
        return tokens;
    }

    protected Path value() {
        return path;
    }

    protected ConfigNodePath subPath(int toRemove) {
        int periodCount = 0;
        ArrayList<Token> tokensCopy = new ArrayList<Token>(tokens);
        for (int i = 0; i < tokensCopy.size(); i++) {
            if (Tokens.isUnquotedText(tokensCopy.get(i)) &&
                    tokensCopy.get(i).tokenText().equals("."))
                periodCount++;

            if (periodCount == toRemove) {
                return new ConfigNodePath(path.subPath(toRemove), tokensCopy.subList(i + 1, tokensCopy.size()));
            }
        }
        throw new ConfigException.BugOrBroken("Tried to remove too many elements from a Path node");
    }

    protected ConfigNodePath first() {
        ArrayList<Token> tokensCopy = new ArrayList<Token>(tokens);
        for (int i = 0; i < tokensCopy.size(); i++) {
            if (Tokens.isUnquotedText(tokensCopy.get(i)) &&
                    tokensCopy.get(i).tokenText().equals("."))
                return new ConfigNodePath(path.subPath(0, 1), tokensCopy.subList(0, i));
        }
        return this;
    }
}
