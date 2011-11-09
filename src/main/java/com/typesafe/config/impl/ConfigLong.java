package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

final class ConfigLong extends AbstractConfigValue {

    private long value;

    ConfigLong(ConfigOrigin origin, long value) {
        super(origin);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.NUMBER;
    }

    @Override
    public Long unwrapped() {
        return value;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigInt || other instanceof ConfigLong;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigLong) {
            return this.value == ((ConfigLong) other).value;
        } else if (other instanceof ConfigInt) {
            Long l = this.unwrapped();
            return l.intValue() == l
                    && ((ConfigInt) other).unwrapped() == l.intValue();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE)
            return unwrapped().intValue(); // match the ConfigInt hashCode for
                                           // any valid Integer
        else
            return unwrapped().hashCode(); // use Long.hashCode()
    }

    @Override
    String transformToString() {
        return Long.toString(value);
    }
}
