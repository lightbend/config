package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

final class ConfigInt extends AbstractConfigValue {

    private int value;

    ConfigInt(ConfigOrigin origin, int value) {
        super(origin);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.NUMBER;
    }

    @Override
    public Integer unwrapped() {
        return value;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigInt || other instanceof ConfigLong;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigInt) {
            return this.value == ((ConfigInt) other).value;
        } else if (other instanceof ConfigLong) {
            Long l = ((ConfigLong) other).unwrapped();
            return l.intValue() == l && this.value == l.intValue();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return value;
    }

    @Override
    String transformToString() {
        return Integer.toString(value);
    }
}
