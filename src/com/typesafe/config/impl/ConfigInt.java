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
    public Object unwrapped() {
        return value;
    }
}
