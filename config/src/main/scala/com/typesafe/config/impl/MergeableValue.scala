package com.typesafe.config.impl

import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigValue

trait MergeableValue extends ConfigMergeable { // converts a Config to its root object and a ConfigValue to itself
    def toFallbackValue(): ConfigValue
}
