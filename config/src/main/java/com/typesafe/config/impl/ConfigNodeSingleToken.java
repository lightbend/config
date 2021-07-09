/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Collections;

import com.typesafe.config.parser.ConfigNode;
import com.typesafe.config.parser.ConfigNodeSyntax;
import com.typesafe.config.parser.ConfigNodeVisitor;

class ConfigNodeSingleToken extends AbstractConfigNode implements ConfigNodeSyntax {
    final Token token;

    ConfigNodeSingleToken(Token t) {
        token = t;
    }

    @Override
    protected Collection<Token> tokens() {
        return Collections.singletonList(token);
    }

    protected Token token() {
        return token;
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        if (visitor.includeSyntax())
            return visitor.visitSyntax(this);
        else
            return null;
    }

    @Override
    public String getText() {
        return token.tokenText();
    }

    @Override
    public ConfigNode getAnyValue() {
        if (Tokens.isValue(token))
            return (ConfigNode) Tokens.getValue(token);
        else
            return null;
    }
}