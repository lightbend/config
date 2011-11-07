package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;

abstract class AbstractConfigValue implements ConfigValue {

    private ConfigOrigin origin;

    AbstractConfigValue(ConfigOrigin origin) {
        this.origin = origin;
    }

    @Override
    public ConfigOrigin origin() {
        return this.origin;
    }
}
