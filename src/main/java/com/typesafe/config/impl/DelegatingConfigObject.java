package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.ConfigValue;

abstract class DelegatingConfigObject extends AbstractConfigObject {
    final private AbstractConfigObject underlying;

    DelegatingConfigObject(ConfigTransformer transformer,
            AbstractConfigObject underlying) {
        super(underlying.origin(), transformer);
        this.underlying = underlying;
    }

    @Override
    public boolean containsKey(Object key) {
        return underlying.containsKey(key);
    }

    @Override
    public Set<String> keySet() {
        return underlying.keySet();
    }

    @Override
    public Map<String, Object> unwrapped() {
        return underlying.unwrapped();
    }

    @Override
    protected AbstractConfigValue peek(String key) {
        return underlying.peek(key);
    }

    @Override
    public AbstractConfigObject withFallback(ConfigValue value) {
        return underlying.withFallback(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return underlying.containsValue(value);
    }

    @Override
    public Set<java.util.Map.Entry<String, ConfigValue>> entrySet() {
        return underlying.entrySet();
    }

    @Override
    public boolean isEmpty() {
        return underlying.isEmpty();
    }

    @Override
    public int size() {
        return underlying.size();
    }

    @Override
    public Collection<ConfigValue> values() {
        return underlying.values();
    }
}
