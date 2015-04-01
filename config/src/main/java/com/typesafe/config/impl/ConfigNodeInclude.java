package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

import java.util.Collection;

final class ConfigNodeInclude extends ConfigNodeComplexValue {
    final private ConfigIncludeKind kind;

    ConfigNodeInclude(Collection<AbstractConfigNode> children, ConfigIncludeKind kind) {
        super(children);
        this.kind = kind;
    }

    @Override
    protected ConfigNodeInclude newNode(Collection<AbstractConfigNode> nodes) {
        throw new ConfigException.BugOrBroken("Tried to indent an include node");
    }

    protected ConfigIncludeKind kind() {
        return kind;
    }

    protected String name() {
        for (AbstractConfigNode n : children) {
            if (n instanceof ConfigNodeSimpleValue) {
                return (String)Tokens.getValue(((ConfigNodeSimpleValue) n).token()).unwrapped();
            }
        }
        return null;
    }
}
