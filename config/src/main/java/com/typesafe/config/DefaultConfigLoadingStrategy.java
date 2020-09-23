package com.typesafe.config;

/**
 * Default config loading strategy. Able to load resource, file or URL.
 * Behavior may be altered by defining one of VM properties
 * {@code config.resource}, {@code config.file} or {@code config.url}
 */
public class DefaultConfigLoadingStrategy implements ConfigLoadingStrategy {
    @Override
    public Config parseApplicationConfig(ConfigParseOptions parseOptions) {
        return ConfigFactory.parseApplicationReplacement(parseOptions)
            .orElseGet(() -> ConfigFactory.parseResourcesAnySyntax("application", parseOptions));
    }
}
