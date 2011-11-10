package com.typesafe.config.impl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;

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
    public Map<String, Object> unwrapped() {
        Map<String, Object> m = new HashMap<String, Object>();
        for (Map.Entry<String, AbstractConfigValue> e : value.entrySet()) {
            m.put(e.getKey(), e.getValue().unwrapped());
        }
        return m;
    }

    @Override
    public boolean containsKey(Object key) {
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
        // the keys have to be sorted, otherwise we could be equal
        // to another map but have a different hashcode.
        List<String> keys = new ArrayList<String>();
        keys.addAll(m.keySet());
        Collections.sort(keys);

        int valuesHash = 0;
        for (String k : keys) {
            valuesHash += m.get(k).hashCode();
        }
        return 41 * (41 + keys.hashCode()) + valuesHash;
    }

    @Override
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

    @Override
    public boolean containsValue(Object v) {
        return value.containsValue(v);
    }

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {
        // total bloat just to work around lack of type variance

        HashSet<java.util.Map.Entry<String, ConfigValue>> entries = new HashSet<Map.Entry<String, ConfigValue>>();
        for (Map.Entry<String, AbstractConfigValue> e : value.entrySet()) {
            entries.add(new AbstractMap.SimpleImmutableEntry<String, ConfigValue>(
                    e.getKey(), e
                    .getValue()));
        }
        return entries;
    }

    @Override
    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public int size() {
        return value.size();
    }

    @Override
    public Collection<ConfigValue> values() {
        return new HashSet<ConfigValue>(value.values());
    }
}
