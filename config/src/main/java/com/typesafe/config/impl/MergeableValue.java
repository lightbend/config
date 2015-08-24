package com.twitter_typesafe.config.impl;

import com.twitter_typesafe.config.ConfigMergeable;
import com.twitter_typesafe.config.ConfigValue;

interface MergeableValue extends ConfigMergeable {
    // converts a Config to its root object and a ConfigValue to itself
    ConfigValue toFallbackValue();
}
