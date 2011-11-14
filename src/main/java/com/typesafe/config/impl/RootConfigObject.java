package com.typesafe.config.impl;

import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;

final class RootConfigObject extends DelegatingConfigObject implements
        ConfigRootImpl {

    final private Path rootPath;

    RootConfigObject(AbstractConfigObject underlying, Path rootPath) {
        super(underlying.transformer, underlying);
        this.rootPath = rootPath;
    }

    @Override
    protected ConfigRootImpl asRoot(AbstractConfigObject underlying,
            Path newRootPath) {
        if (newRootPath.equals(this.rootPath))
            return this;
        else
            return new RootConfigObject(underlying, newRootPath);
    }

    @Override
    public RootConfigObject newCopy(AbstractConfigObject underlying,
            ConfigTransformer newTransformer, ResolveStatus newStatus) {
        return new RootConfigObject(underlying.newCopy(newTransformer,
                newStatus), rootPath);
    }

    @Override
    public ConfigRootImpl resolve() {
        return resolve(this);
    }

    @Override
    public ConfigRootImpl resolve(ConfigResolveOptions options) {
        return resolve(this, options);
    }

    @Override
    public RootConfigObject withFallback(ConfigValue value) {
        return new RootConfigObject(super.withFallback(value), rootPath);
    }

    @Override
    public RootConfigObject withFallbacks(ConfigValue... values) {
        return new RootConfigObject(super.withFallbacks(values), rootPath);
    }

    @Override
    public Path rootPathObject() {
        return rootPath;
    }

    @Override
    public String rootPath() {
        return rootPath.render();
    }
}
