package com.typesafe.config.impl;

/**
 * This class represents a leaf ConfigNode. This type of ConfigNode has no children.
 */
class ConfigNodeSimpleValue extends AbstractConfigNode implements ConfigNodeValue {
    ConfigNodeSimpleValue(Token value) {
        super(value);
    }
}
