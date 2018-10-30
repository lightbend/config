/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Collections;

class ConfigNodeSingleToken extends AbstractConfigNode {
    final Token token;
    ConfigNodeSingleToken(Token t) {
        token = t;
    }

    @Override
    public Collection<Token> tokens() {
        return Collections.singletonList(token);
    }

    protected Token token() { return token; }
}