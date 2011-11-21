/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

/**
 * Represents the origin (such as filename and line number) of a
 * {@link ConfigValue} for use in error messages. Obtain the origin of a value
 * with {@link ConfigValue#origin}.
 * 
 * <p>
 * <em>Do not implement this interface</em>; it should only be implemented by
 * the config library. Arbitrary implementations will not work because the
 * library internals assume a specific concrete implementation. Also, this
 * interface is likely to grow new methods over time, so third-party
 * implementations will break.
 */
public interface ConfigOrigin {
    public String description();
}
