package com.typesafe.config.impl;

import com.typesafe.config.ConfigRoot;
import com.typesafe.config.ConfigValue;

interface ConfigRootImpl extends ConfigRoot {
    Path rootPathObject();

    @Override
    ConfigRootImpl withFallbacks(ConfigValue... fallbacks);
}
