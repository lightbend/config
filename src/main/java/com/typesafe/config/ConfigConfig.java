package com.typesafe.config;

/**
 * Configuration for a configuration!
 */
public final class ConfigConfig {

    private String rootPath;

    public ConfigConfig(String rootPath) {
        this.rootPath = rootPath;
    }

    public String rootPath() {
        return rootPath;
    }
}
