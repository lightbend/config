package com.typesafe.config.impl;

import java.util.List;

import com.typesafe.config.ConfigValueType;

final class StackTransformer implements ConfigTransformer {

    final private List<ConfigTransformer> stack;

    StackTransformer(List<ConfigTransformer> stack) {
        this.stack = stack;
    }

    @Override
    public AbstractConfigValue transform(AbstractConfigValue value,
            ConfigValueType requested) {
        AbstractConfigValue current = value;
        for (ConfigTransformer t : stack) {
            current = t.transform(current, requested);
        }
        return current;
    }

}
