/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.*;

abstract class ConfigNodeComplexValue extends AbstractConfigNodeValue {
    final protected ArrayList<AbstractConfigNode> children;

    ConfigNodeComplexValue(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList(children);
    }

    final public Collection<AbstractConfigNode> children() {
        return children;
    }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }
}
