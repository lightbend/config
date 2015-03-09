package com.typesafe.config.impl;

import com.typesafe.config.ConfigNode;

import java.util.ArrayList;
import java.util.Collection;

public class ConfigNodeKeyValue implements ConfigNode{
    final private ArrayList<ConfigNode> children;
    private int configNodeValueIndex;
    private ConfigNodeKey key;
    private ConfigNodeValue value;

    public ConfigNodeKeyValue(Collection<ConfigNode> children) {
        this.children = new ArrayList<ConfigNode>(children);
        for (int i = 0; i < this.children.size(); i++) {
            ConfigNode currNode = this.children.get(i);
            if (currNode instanceof ConfigNodeKey) {
                key = (ConfigNodeKey)currNode;
            } else if (currNode instanceof ConfigNodeValue) {
                value = (ConfigNodeValue)currNode;
                configNodeValueIndex = i;
            }
        }
    }

    public String render() {
        StringBuilder renderedText = new StringBuilder();
        for (ConfigNode child : children) {
            renderedText.append(child.render());
        }
        return renderedText.toString();
    }

    public ConfigNodeKeyValue replaceValue(ConfigNodeValue newValue) {
        ArrayList<ConfigNode> newChildren = (ArrayList<ConfigNode>)(children.clone());
        newChildren.set(configNodeValueIndex, newValue);
        return new ConfigNodeKeyValue(newChildren);
    }

    public ConfigNodeValue value() {
        return value;
    }

    public ConfigNodeKey key() {
        return key;
    }
}
