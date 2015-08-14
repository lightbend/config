package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class ConfigNodeConditional extends AbstractConfigNode {
    final private ArrayList<AbstractConfigNode> children;
    final private AbstractConfigNodeValue body;

    ConfigNodeConditional(Collection<AbstractConfigNode> children, AbstractConfigNodeValue body) {
        this.children = new ArrayList<AbstractConfigNode>(children);
        this.body = body;

    }

    public AbstractConfigNodeValue body() { return body; }
    public List<AbstractConfigNode> children() { return children; }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList<Token>();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        for (Token token : body.tokens()) {
            tokens.add(token);
        }
        return tokens;
    }
}

