/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

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
}
