package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;

final class SimpleConfigOrigin implements ConfigOrigin {

    final private String description;

    SimpleConfigOrigin(String description) {
        this.description = description;
    }

    @Override
    public String description() {
        return description;
    }

}
