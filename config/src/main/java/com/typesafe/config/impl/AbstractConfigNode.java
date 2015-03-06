package com.typesafe.config.impl;

import com.typesafe.config.ConfigNode;

abstract class AbstractConfigNode implements ConfigNode {
    final private Token token;

    AbstractConfigNode(Token t) {
        token = t;
    }

    public String render() {
        return token.tokenText();
    }
}
