package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

final class TransformedConfigObject extends DelegatingConfigObject {

    TransformedConfigObject(ConfigTransformer transformer,
            AbstractConfigObject underlying) {
        super(transformer, underlying);
        if (transformer == underlying.transformer)
            throw new ConfigException.BugOrBroken(
                    "Created unnecessary TransformedConfigObject");
    }
}
