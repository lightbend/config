package com.typesafe.config;

/**
 * Configuration for a configuration!
 */
public final class ConfigConfig {

    private String rootPath;

    /**
     * Creates a new configuration configuration.
     *
     * @param rootPath
     *            the root path as described in Config.load() docs
     */
    public ConfigConfig(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Get the configured root path. This method would be used by code
     * implementing a configuration backend; don't worry about it.
     *
     * @return the root path
     */
    public String rootPath() {
        return rootPath;
    }
}
