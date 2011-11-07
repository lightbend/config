package com.typesafe.config;

/**
 * Interface implemented by any configuration value. From the perspective of
 * users of this interface, the object should be immutable.
 */
public interface ConfigValue {
    ConfigOrigin origin();

    ConfigValueType valueType();

    /**
     * Returns the config value as a plain Java value, should be a String,
     * Number, etc. matching the valueType() of the ConfigValue.
     */
    Object unwrapped();
}
