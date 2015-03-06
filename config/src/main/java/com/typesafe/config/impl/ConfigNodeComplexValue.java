package com.typesafe.config.impl;

import com.typesafe.config.ConfigNode;
import java.util.ArrayList;
import java.util.Collection;

final class ConfigNodeComplexValue implements ConfigNode, ConfigNodeValue {
    final private ArrayList<ConfigNode> children;

    ConfigNodeComplexValue(Collection<ConfigNode> children) {
        this.children = new ArrayList<ConfigNode>(children);
    }

    public ArrayList<ConfigNode> children() {
        return children;
    }

    public String render() {
        StringBuilder renderedText = new StringBuilder();
        for (ConfigNode child : children) {
            renderedText.append(child.render());
        }
        return renderedText.toString();
    }

    public ConfigNodeComplexValue replaceValueOnPath(Path desiredPath, ConfigNodeValue value) {
        boolean matchedFullPath = false;
        boolean matchedPartialPath = false;
        Path remainingPath = desiredPath;
        ArrayList<ConfigNode> childrenCopy = (ArrayList<ConfigNode>)children.clone();
        for (int i = 0; i < childrenCopy.size(); i++) {
            ConfigNode child = childrenCopy.get(i);
            if (child instanceof ConfigNodeKey) {
                Path key = Path.newPath(child.render());
                if (key.equals(desiredPath)) {
                    matchedFullPath = true;
                } else if (desiredPath.startsWith(key)) {
                    matchedPartialPath = true;
                    remainingPath = desiredPath.subPath(key.length());
                }
            } else if (child instanceof ConfigNodeValue) {
                if (matchedFullPath) {
                    childrenCopy.set(i, value);
                    return new ConfigNodeComplexValue(childrenCopy);
                } else if (matchedPartialPath) {
                    if (child instanceof ConfigNodeComplexValue) {
                        childrenCopy.set(i, ((ConfigNodeComplexValue) child).replaceValueOnPath(remainingPath, value));
                        return new ConfigNodeComplexValue(childrenCopy);
                    }
                    matchedPartialPath = false;
                }
            }
        }
        return this;
    }
}
