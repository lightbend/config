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
    public Object unwrapped() {
        List<Object> list = new ArrayList<Object>();
        for (ConfigValue v : value) {
            list.add(v.unwrapped());
        }
        return list;
    }
}
