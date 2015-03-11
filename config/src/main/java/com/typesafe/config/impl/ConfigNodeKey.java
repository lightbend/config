package com.typesafe.config.impl;

import java.util.Collection;

final class ConfigNodeKey extends AbstractConfigNode {
    final private Path key;
    ConfigNodeKey(Path key) {
        this.key = key;
    }

    protected Collection<Token> tokens() {
        return key.tokens();
    }

    protected Path value() {
        return key;
    }
}
