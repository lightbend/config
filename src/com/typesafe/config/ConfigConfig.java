package com.typesafe.config;

/**
 * Configuration for a configuration!
 */
public final class ConfigConfig {

    private String rootPath;
    private ConfigTransformer extraTransformer;

    public ConfigConfig(String rootPath, ConfigTransformer extraTransformer) {
        this.rootPath = rootPath;
    }

    public String rootPath() {
        return rootPath;
    }

    public ConfigTransformer extraTransformer() {
        return extraTransformer;
    }
}
