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
    public Object unwrapped() {
        return value;
    }
}
