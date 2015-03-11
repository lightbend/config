package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Collections;

final class ConfigNodeSingleToken extends AbstractConfigNode{
    Token token;
    ConfigNodeSingleToken(Token t) {
        token = t;
    }

    protected Collection<Token> tokens() {
        return Collections.singletonList(token);
    }
}