package com.typesafe.config.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigTransformer;
import com.typesafe.config.ConfigValue;

class SimpleConfigObject extends AbstractConfigObject {

    // this map should never be modified - assume immutable
    private Map<String, ConfigValue> value;

    SimpleConfigObject(ConfigOrigin origin, ConfigTransformer transformer,
            Map<String, ConfigValue> value) {
        super(origin, transformer);
        this.value = value;
    }

    @Override
    protected ConfigValue peek(String key) {
        return value.get(key);
    }

    @Override
    public Object unwrapped() {
        Map<String, Object> m = new HashMap<String, Object>();
        for (String k : value.keySet()) {
            m.put(k, value.get(k).unwrapped());
        }
        return m;
    }

    @Override
    public boolean containsKey(String key) {
        return value.containsKey(key);
    }

    @Override
    public Set<String> keySet() {
        return value.keySet();
    }
}
