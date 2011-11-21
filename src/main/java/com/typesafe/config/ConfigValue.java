/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

/**
 * An immutable value, following the <a href="http://json.org">JSON</a> type
 * schema.
 *
 * <p>
 * Because this object is immutable, it is safe to use from multiple threads and
 * there's no need for "defensive copies."
 *
 * <p>
 * <em>Do not implement {@code ConfigValue}</em>; it should only be implemented
 * by the config library. Arbitrary implementations will not work because the
 * library internals assume a specific concrete implementation. Also, this
 * interface is likely to grow new methods over time, so third-party
 * implementations will break.
 */
public interface ConfigValue extends ConfigMergeable {
    /**
     * The origin of the value (file, line number, etc.), for debugging and
     * error messages.
     *
     * @return where the value came from
     */
    ConfigOrigin origin();

    /**
     * The {@link ConfigValueType} of the value; matches the JSON type schema.
     *
     * @return value's type
     */
    ConfigValueType valueType();

    /**
     * Returns the value as a plain Java boxed value, that is, a {@code String},
     * {@code Number}, {@code Boolean}, {@code Map<String,Object>},
     * {@code List<Object>}, or {@code null}, matching the {@link #valueType()}
     * of this {@code ConfigValue}. If the value is a {@link ConfigObject} or
     * {@link ConfigList}, it is recursively unwrapped.
     */
    Object unwrapped();

    @Override
    ConfigValue withFallback(ConfigMergeable other);
}
