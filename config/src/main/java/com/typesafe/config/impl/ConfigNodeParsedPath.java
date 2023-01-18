/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.parser.ConfigNodeVisitor;

final class ConfigNodeParsedPath extends AbstractConfigNode {
    final private Path path;
    final ArrayList<Token> tokens;
    ConfigNodeParsedPath(Path path, Collection<Token> tokens) {
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

    protected ConfigNodeParsedPath subPath(int toRemove) {
        int periodCount = 0;
        ArrayList<Token> tokensCopy = new ArrayList<Token>(tokens);
        for (int i = 0; i < tokensCopy.size(); i++) {
            if (Tokens.isUnquotedText(tokensCopy.get(i)) &&
                    tokensCopy.get(i).tokenText().equals("."))
                periodCount++;

            if (periodCount == toRemove) {
                return new ConfigNodeParsedPath(path.subPath(toRemove), tokensCopy.subList(i + 1, tokensCopy.size()));
            }
        }
        throw new ConfigException.BugOrBroken("Tried to remove too many elements from a Path node");
    }

    protected ConfigNodeParsedPath first() {
        ArrayList<Token> tokensCopy = new ArrayList<Token>(tokens);
        for (int i = 0; i < tokensCopy.size(); i++) {
            if (Tokens.isUnquotedText(tokensCopy.get(i)) &&
                    tokensCopy.get(i).tokenText().equals("."))
                return new ConfigNodeParsedPath(path.subPath(0, 1), tokensCopy.subList(0, i));
        }
        return this;
    }

    public ConfigNodeUnparsedPath toUnparsed(ConfigOrigin origin) {
        return new ConfigNodeUnparsedPath(
                Collections.unmodifiableList(tokens.stream().map(tok -> new ConfigNodeSingleToken(tok)).collect(Collectors.toList())),
                origin);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitPath(toUnparsed(null));
    }
}
