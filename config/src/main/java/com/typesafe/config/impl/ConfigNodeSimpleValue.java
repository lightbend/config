package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Collections;

/**
 * This class represents a leaf ConfigNode. This type of ConfigNode has no children.
 */
class ConfigNodeSimpleValue extends AbstractConfigNodeValue {
    Token token;
    ConfigNodeSimpleValue(Token value) {
        token = value;
    }

    protected Collection<Token> tokens() {
        return Collections.singletonList(token);
    }
}
