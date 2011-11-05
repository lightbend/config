package com.typesafe.config;

import com.typesafe.config.impl.ConfigFactory;

public class Config {
    public static ConfigObject load(ConfigConfig configConfig) {
        return ConfigFactory.getConfig(configConfig);
    }

    public static ConfigObject load(String rootPath) {
        return ConfigFactory.getConfig(new ConfigConfig(rootPath, null));
    }
}
