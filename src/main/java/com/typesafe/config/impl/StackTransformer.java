package com.typesafe.config.impl;

import java.util.List;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

final class StackTransformer implements ConfigTransformer {

    private List<ConfigTransformer> stack;

    StackTransformer(List<ConfigTransformer> stack) {
        this.stack = stack;
    }

    @Override
    public ConfigValue transform(ConfigValue value, ConfigValueType requested) {
        ConfigValue current = value;
        for (ConfigTransformer t : stack) {
            current = t.transform(current, requested);
        }
        return current;
    }

}
