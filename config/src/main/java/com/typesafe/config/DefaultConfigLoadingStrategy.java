package com.typesafe.config;

/**
 * Default config loading strategy. Able to load resource, file or URL.
 * Behavior may be altered by defining one of VM properties
 * {@code config.resource}, {@code config.file} or {@code config.url}
 */
public class DefaultConfigLoadingStrategy implements ConfigLoadingStrategy {
    @Override
    public Config parseApplicationConfig(ConfigParseOptions parseOptions) {
        Config applicationReplacement = ConfigFactory.parseApplicationReplacement(parseOptions);
        if (applicationReplacement.isEmpty()) {
            return ConfigFactory.parseResourcesAnySyntax("application", parseOptions);
        } else {
            return applicationReplacement;
        }
    }
}
