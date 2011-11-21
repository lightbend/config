/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

/**
 * Subtype of {@link Config} which is a root rather than nested object and
 * supports {@link ConfigRoot#resolve resolving substitutions}.
 *
 * <p>
 * <em>Do not implement this interface</em>; it should only be implemented by
 * the config library. Arbitrary implementations will not work because the
 * library internals assume a specific concrete implementation. Also, this
 * interface is likely to grow new methods over time, so third-party
 * implementations will break.
 */
public interface ConfigRoot extends Config {
    /**
     * Returns a replacement root object with all substitutions (the
     * <code>${foo.bar}</code> syntax, see <a
     * href="https://github.com/havocp/config/blob/master/HOCON.md">the
     * spec</a>) resolved. Substitutions are looked up in this root object. A
     * {@link Config} must be resolved before you can use it. This method uses
     * {@link ConfigResolveOptions#defaults()}.
     *
     * @return an immutable object with substitutions resolved
     */
    ConfigRoot resolve();

    /**
     * Like {@link ConfigRoot#resolve()} but allows you to specify options.
     *
     * @param options
     *            resolve options
     * @return the resolved root config
     */
    ConfigRoot resolve(ConfigResolveOptions options);

    @Override
    ConfigRoot withFallback(ConfigMergeable fallback);

    /**
     * Gets the global app name that this root represents.
     *
     * @return the app's root config path
     */
    String rootPath();
}
