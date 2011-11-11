package com.typesafe.config;


/**
 * Interface implemented by any configuration value. From the perspective of
 * users of this interface, the object is immutable. It is therefore safe to use
 * from multiple threads.
 */
public interface ConfigValue {
    /**
     * The origin of the value, for debugging and error messages.
     *
     * @return where the value came from
     */
    ConfigOrigin origin();

    /**
     * The type of the value; matches the JSON type schema.
     *
     * @return value's type
     */
    ConfigValueType valueType();

    /**
     * Returns the config value as a plain Java boxed value, should be a String,
     * Number, etc. matching the valueType() of the ConfigValue. If the value is
     * a ConfigObject or ConfigList, it is recursively unwrapped.
     */
    Object unwrapped();

    /**
     * Returns a new value computed by merging this value with another, with
     * keys in this value "winning" over the other one. Only ConfigObject has
     * anything to do in this method (it merges the fallback keys into itself).
     * All other values just return the original value, since they automatically
     * override any fallback.
     *
     * @param other
     *            an object whose keys should be used if the keys are not
     *            present in this one
     * @return a new object (or the original one, if the fallback doesn't get
     *         used)
     */
    ConfigValue withFallback(ConfigValue other);
}
