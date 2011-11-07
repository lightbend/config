package com.typesafe.config;

/**
 * A ConfigTransformer converts values in the config to other values, most often
 * it's used to parse strings and treat them as some other kind of value.
 * There's no implementation of ConfigValue included in the public API of this
 * library, you have to just create one.
 */
public interface ConfigTransformer {
    ConfigValue transform(ConfigValue value, ConfigValueType requested);
}
