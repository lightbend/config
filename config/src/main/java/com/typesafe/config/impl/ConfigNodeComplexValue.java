package com.typesafe.config.impl;

import com.typesafe.config.ConfigNode;

import java.util.*;

final class ConfigNodeComplexValue implements ConfigNode, ConfigNodeValue {
    private ArrayList<ConfigNode> children;
    final private LinkedHashMap<Path, Integer> map = new LinkedHashMap<>();
    final ArrayList<Integer> keyValueIndexes;

    ConfigNodeComplexValue(Collection<ConfigNode> children) {
        this.children = new ArrayList(children);
        keyValueIndexes = new ArrayList<Integer>();

        // Construct the list of indexes of Key-Value nodes. Do this
        // in reverse order, since all but the final duplicate will be removed.
        for (int i = this.children.size() - 1; i >= 0; i--) {
            ConfigNode currNode = this.children.get(i);
            if (currNode instanceof ConfigNodeKeyValue) {
                keyValueIndexes.add(i);
            }
        }
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

    protected ConfigNodeComplexValue changeValueOnPath(Path desiredPath, ConfigNodeValue value) {
        ArrayList<ConfigNode> childrenCopy = (ArrayList<ConfigNode>)(children.clone());
        boolean replaced = value == null;
        ConfigNodeKeyValue node;
        Path key;
        for (Integer keyValIndex : keyValueIndexes) {
            node = (ConfigNodeKeyValue)children.get(keyValIndex.intValue());
            key = Path.newPath(node.key().render());
            if (key.equals(desiredPath)) {
                if (!replaced) {
                    childrenCopy.set(keyValIndex.intValue(), node.replaceValue(value));
                    replaced = true;
                }
                else
                    childrenCopy.remove(keyValIndex.intValue());
            } else if (desiredPath.startsWith(key)) {
                if (node.value() instanceof ConfigNodeComplexValue) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    if (!replaced) {
                        node = node.replaceValue(((ConfigNodeComplexValue) node.value()).changeValueOnPath(remainingPath, value));
                        if (node.render() != children.get(keyValIndex.intValue()).render())
                            replaced = true;
                        childrenCopy.set(keyValIndex.intValue(), node);
                    } else {
                        node = node.replaceValue(((ConfigNodeComplexValue) node.value()).removeValueOnPath(remainingPath));
                        childrenCopy.set(keyValIndex.intValue(), node);
                    }
                }
            }
        }
        return new ConfigNodeComplexValue(childrenCopy);
    }

    public ConfigNodeComplexValue setValueOnPath(Path desiredPath, ConfigNodeValue value) {
        ConfigNodeComplexValue node = changeValueOnPath(desiredPath, value);

        // If the desired Path did not exist, add it
        if (node.render().equals(render())) {
            ArrayList<ConfigNode> childrenCopy = (ArrayList<ConfigNode>)children.clone();
            ArrayList<ConfigNode> newNodes = new ArrayList<ConfigNode>();
            newNodes.add(new ConfigNodeBasic(Tokens.newLine(null)));
            newNodes.add(new ConfigNodeKey(Tokens.newUnquotedText(null, desiredPath.render())));
            newNodes.add(new ConfigNodeBasic(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(new ConfigNodeBasic(Tokens.COLON));
            newNodes.add(new ConfigNodeBasic(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(value);
            newNodes.add(new ConfigNodeBasic(Tokens.newLine(null)));
            childrenCopy.add(new ConfigNodeKeyValue(newNodes));
            node = new ConfigNodeComplexValue(childrenCopy);
        }
        return node;
    }

    public ConfigNodeComplexValue removeValueOnPath(Path desiredPath) {
        return changeValueOnPath(desiredPath, null);
    }

}
