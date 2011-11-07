package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

final class ConfigList extends AbstractConfigValue {

    private List<ConfigValue> value;

    ConfigList(ConfigOrigin origin, List<ConfigValue> value) {
        super(origin);
        this.value = value;
    }

    List<ConfigValue> asJavaList() {
        return value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.LIST;
    }

    @Override
    public List<Object> unwrapped() {
        List<Object> list = new ArrayList<Object>();
        for (ConfigValue v : value) {
            list.add(v.unwrapped());
        }
        return list;
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
