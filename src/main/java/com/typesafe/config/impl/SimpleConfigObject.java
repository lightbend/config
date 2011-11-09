package com.typesafe.config.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;

class SimpleConfigObject extends AbstractConfigObject {

    // this map should never be modified - assume immutable
    private Map<String, AbstractConfigValue> value;

    SimpleConfigObject(ConfigOrigin origin, ConfigTransformer transformer,
            Map<String, AbstractConfigValue> value) {
        super(origin, transformer);
        if (value == null)
            throw new ConfigException.BugOrBroken(
                    "creating config object with null map");
        this.value = value;
    }

    @Override
    protected AbstractConfigValue peek(String key) {
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

    private static boolean mapEquals(Map<String, AbstractConfigValue> a,
            Map<String, AbstractConfigValue> b) {
        Set<String> aKeys = a.keySet();
        Set<String> bKeys = b.keySet();

        if (!aKeys.equals(bKeys))
            return false;

        for (String key : aKeys) {
            if (!a.get(key).equals(b.get(key)))
                return false;
        }
        return true;
    }

    private static int mapHash(Map<String, AbstractConfigValue> m) {
        Set<String> keys = m.keySet();
        int valuesHash = 0;
        for (String k : keys) {
            valuesHash += m.get(k).hashCode();
        }
        return 41 * (41 + keys.hashCode()) + valuesHash;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ConfigObject;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof SimpleConfigObject) {
            // optimization to avoid unwrapped() for two SimpleConfigObject
            // note: if this included "transformer" then we could never be
            // equal to a non-SimpleConfigObject ConfigObject.
            return canEqual(other)
                    && mapEquals(value, ((SimpleConfigObject) other).value);
        } else if (other instanceof ConfigObject) {
            return super.equals(other);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return mapHash(this.value);
    }
}
