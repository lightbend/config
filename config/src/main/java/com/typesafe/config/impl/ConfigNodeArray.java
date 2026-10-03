package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;

import java.util.Collection;

final class ConfigNodeArray extends ConfigNodeComplexValue {
    ConfigNodeArray(Collection<AbstractConfigNode> children) {
        super(children);
    }

    ConfigNodeArray(Collection<AbstractConfigNode> children, ConfigOrigin origin) {
        super(children, origin);
    }

    @Override
    protected ConfigNodeArray newNode(Collection<AbstractConfigNode> nodes) {
        return new ConfigNodeArray(nodes, origin());
    }
}
