package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;

final class ConfigNodeObject extends ConfigNodeComplexValue {
    ConfigNodeObject(Collection<AbstractConfigNode> children) {
        super(children);
    }

    protected ConfigNodeObject changeValueOnPath(Path desiredPath, AbstractConfigNodeValue value) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList(super.children);
        // Copy the value so we can change it to null but not modify the original parameter
        AbstractConfigNodeValue valueCopy = value;
        for (int i = super.children.size() - 1; i >= 0; i--) {
            if (!(super.children.get(i) instanceof ConfigNodeField)) {
                continue;
            }
            ConfigNodeField node = (ConfigNodeField)super.children.get(i);
            Path key = node.path().value();
            if (key.equals(desiredPath)) {
                if (valueCopy == null)
                    childrenCopy.remove(i);
                else {
                    childrenCopy.set(i, node.replaceValue(value));
                    valueCopy = null;
                }
            } else if (desiredPath.startsWith(key)) {
                if (node.value() instanceof ConfigNodeObject) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    childrenCopy.set(i, node.replaceValue(((ConfigNodeObject)node.value()).changeValueOnPath(remainingPath, valueCopy)));
                    if (valueCopy != null && node.render() != super.children.get(i).render())
                        valueCopy = null;
                }
            }
        }
        return new ConfigNodeObject(childrenCopy);
    }

    public ConfigNodeObject setValueOnPath(String desiredPath, AbstractConfigNodeValue value) {
        ConfigNodePath path = PathParser.parsePathNode(desiredPath);
        return setValueOnPath(path, value);
    }

    private ConfigNodeObject setValueOnPath(ConfigNodePath desiredPath, AbstractConfigNodeValue value) {
        ConfigNodeObject node = changeValueOnPath(desiredPath.value(), value);

        // If the desired Path did not exist, add it
        if (node.render().equals(render())) {
            ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(super.children);
            ArrayList<AbstractConfigNode> newNodes = new ArrayList();
            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
            newNodes.add(desiredPath);
            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(new ConfigNodeSingleToken(Tokens.COLON));
            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(value);
            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
            childrenCopy.add(new ConfigNodeField(newNodes));
            node = new ConfigNodeObject(childrenCopy);
        }
        return node;
    }

    public ConfigNodeComplexValue removeValueOnPath(String desiredPath) {
        Path path = PathParser.parsePath(desiredPath);
        return changeValueOnPath(path, null);
    }
}
