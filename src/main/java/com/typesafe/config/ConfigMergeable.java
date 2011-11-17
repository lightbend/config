package com.typesafe.config;

/**
 * This is a marker for types that can be merged as a fallback into a Config or
 * a ConfigValue. Both Config and ConfigValue are mergeable.
 */
public interface ConfigMergeable {
    /**
     * Converts the mergeable to a ConfigValue to be merged.
     * 
     * @return
     */
    ConfigValue toValue();

    /**
     * Returns a new value computed by merging this value with another, with
     * keys in this value "winning" over the other one. Only ConfigObject and
     * Config instances do anything in this method (they need to merge the
     * fallback keys into themselves). All other values just return the original
     * value, since they automatically override any fallback.
     *
     * @param other
     *            an object whose keys should be used if the keys are not
     *            present in this one
     * @return a new object (or the original one, if the fallback doesn't get
     *         used)
     */
    ConfigMergeable withFallback(ConfigMergeable other);

    /**
     * Convenience method just calls withFallback() on each of the values;
     * earlier values in the list win over later ones.
     *
     * @param fallbacks
     * @return a version of the object with the requested fallbacks merged in
     */
    ConfigMergeable withFallbacks(ConfigMergeable... others);
}
