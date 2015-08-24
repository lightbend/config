/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

import com.twitter_typesafe.config.parser.ConfigNode;
import java.util.Collection;

abstract class AbstractConfigNode implements ConfigNode {
    abstract Collection<Token> tokens();
    final public String render() {
        StringBuilder origText = new StringBuilder();
        Iterable<Token> tokens = tokens();
        for (Token t : tokens) {
            origText.append(t.tokenText());
        }
        return origText.toString();
    }

    @Override
    final public boolean equals(Object other) {
        return other instanceof AbstractConfigNode && render().equals(((AbstractConfigNode)other).render());
    }

    @Override
    final public int hashCode() {
        return render().hashCode();
    }
}
