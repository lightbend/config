package com.typesafe.config.impl;

import com.typesafe.config.ConfigRoot;
import com.typesafe.config.ConfigValue;

final class RootConfigObject extends DelegatingConfigObject implements
        ConfigRoot {

    RootConfigObject(AbstractConfigObject underlying) {
        super(underlying.transformer, underlying);
    }

    @Override
    protected ConfigRoot asRoot() {
        return this;
    }

    @Override
    public ConfigRoot resolve() {
        return ((AbstractConfigObject) SubstitutionResolver.resolve(this, this))
                .asRoot();
    }

    @Override
    public RootConfigObject withFallback(ConfigValue value) {
        return new RootConfigObject(super.withFallback(value));
    }
}
