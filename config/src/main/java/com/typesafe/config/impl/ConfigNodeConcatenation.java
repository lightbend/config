package com.typesafe.config.impl;

import java.util.Collection;

import com.typesafe.config.parser.ConfigNodeVisitor;

final class ConfigNodeConcatenation extends ConfigNodeComplexValue
        implements com.typesafe.config.parser.ConfigNodeConcatenation {
    ConfigNodeConcatenation(Collection<AbstractConfigNode> children) {
        super(children);
    }

    @Override
    protected ConfigNodeConcatenation newNode(Collection<AbstractConfigNode> nodes) {
        return new ConfigNodeConcatenation(nodes);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitConcatenation(this);
    }

}
