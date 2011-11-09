package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

final class ConfigList extends AbstractConfigValue {

    private List<AbstractConfigValue> value;

    ConfigList(ConfigOrigin origin, List<AbstractConfigValue> value) {
        super(origin);
        this.value = value;
    }

    List<? extends ConfigValue> asJavaList() {
        return value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.LIST;
    }

    @Override
    public List<Object> unwrapped() {
        List<Object> list = new ArrayList<Object>();
        for (AbstractConfigValue v : value) {
            list.add(v.unwrapped());
        }
        return list;
    }

    @Override
    ConfigList resolveSubstitutions(SubstitutionResolver resolver, int depth,
            boolean withFallbacks) {
        // lazy-create for optimization
        List<AbstractConfigValue> changed = null;
        int i = 0;
        for (AbstractConfigValue v : value) {
            AbstractConfigValue resolved = resolver.resolve(v, depth,
                    withFallbacks);

            // lazy-create the new list if required
            if (changed == null && resolved != v) {
                changed = new ArrayList<AbstractConfigValue>();
                for (int j = 0; j < i; ++j) {
                    changed.add(value.get(j));
                }
            }

            // once the new list is created, all elements
            // have to go in it.
            if (changed != null) {
                changed.add(resolved);
            }

            i += 1;
        }

        if (changed != null) {
            if (changed.size() != value.size())
                throw new ConfigException.BugOrBroken(
                        "substituted list's size doesn't match");
            return new ConfigList(origin(), changed);
        } else {
            return this;
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof ConfigList;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigList) {
            // optimization to avoid unwrapped() for two ConfigList
            return canEqual(other) && value.equals(((ConfigList) other).value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return value.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(valueType().name());
        sb.append("(");
        for (ConfigValue e : value) {
            sb.append(e.toString());
            sb.append(",");
        }
        if (!value.isEmpty())
            sb.setLength(sb.length() - 1); // chop comma
        sb.append(")");
        return sb.toString();
    }
}
