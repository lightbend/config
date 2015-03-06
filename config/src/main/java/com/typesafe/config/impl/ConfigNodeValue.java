package com.typesafe.config.impl;

import com.typesafe.config.ConfigNode;

public interface ConfigNodeValue extends ConfigNode {
    public String render();
}
