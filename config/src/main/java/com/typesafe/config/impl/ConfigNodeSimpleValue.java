/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Collections;

class ConfigNodeSimpleValue extends AbstractConfigNodeValue {
    final Token token;
    ConfigNodeSimpleValue(Token value) {
        token = value;
    }

    protected Collection<Token> tokens() {
        return Collections.singletonList(token);
    }
}
