/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.ObjectStreamException;
import java.io.Serializable;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;
import com.typesafe.config.parser.ConfigNodeBoolean;
import com.typesafe.config.parser.ConfigNodeVisitor;

final class ConfigBoolean extends AbstractConfigValue implements Serializable, ConfigNodeBoolean {

    private static final long serialVersionUID = 2L;

    final private boolean value;

    ConfigBoolean(ConfigOrigin origin, boolean value) {
        super(origin);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.BOOLEAN;
    }

    @Override
    public Boolean unwrapped() {
        return value;
    }

    @Override
    String transformToString() {
        return value ? "true" : "false";
    }

    @Override
    protected ConfigBoolean newCopy(ConfigOrigin origin) {
        return new ConfigBoolean(origin, value);
    }

    // serialization all goes through SerializedConfigValue
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitBoolean(this);
    }

    @Override
    public boolean getValue() {
        return value;
    }
}
