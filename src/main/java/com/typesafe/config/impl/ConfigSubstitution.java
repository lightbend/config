package com.typesafe.config.impl;

import java.util.List;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

final class ConfigSubstitution extends AbstractConfigValue {

    private AbstractConfigObject root;
    private List<Token> tokens;

    ConfigSubstitution(ConfigOrigin origin, AbstractConfigObject root,
            List<Token> tokens) {
        super(origin);
        this.root = root;
        this.tokens = tokens;
    }

    @Override
    public ConfigValueType valueType() {
        return null; // FIXME
    }

    @Override
    public Object unwrapped() {
        // TODO Auto-generated method stub
        return null;
    }

}
