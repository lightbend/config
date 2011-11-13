package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;

abstract class ConfigNumber extends AbstractConfigValue {
    // This is so when we concatenate a number into a string (say it appears in
    // a sentence) we always have it exactly as the person typed it into the
    // config file. It's purely cosmetic; equals/hashCode don't consider this
    // for example.
    final private String originalText;

    protected ConfigNumber(ConfigOrigin origin, String originalText) {
        super(origin);
        this.originalText = originalText;
    }

    @Override
    String transformToString() {
        return originalText;
    }
}
