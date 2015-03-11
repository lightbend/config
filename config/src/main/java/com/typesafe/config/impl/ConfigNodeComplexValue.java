package com.typesafe.config.impl;

import com.typesafe.config.ConfigNode;

import java.util.*;

final class ConfigNodeComplexValue extends AbstractConfigNodeValue {
    final private ArrayList<AbstractConfigNode> children;

    ConfigNodeComplexValue(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList(children);
    }

    public Iterable<AbstractConfigNode> children() {
        return children;
    }

    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }

    protected ConfigNodeComplexValue changeValueOnPath(Path desiredPath, AbstractConfigNodeValue value) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList(children);
        boolean replaced = value == null;
        ConfigNodeKeyValue node;
        Path key;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (!(children.get(i) instanceof ConfigNodeKeyValue)) {
                continue;
            }
            node = (ConfigNodeKeyValue)children.get(i);
            key = node.key().value();
            if (key.equals(desiredPath)) {
                if (!replaced) {
                    childrenCopy.set(i, node.replaceValue(value));
                    replaced = true;
                }
                else
                    childrenCopy.remove(i);
            } else if (desiredPath.startsWith(key)) {
                if (node.value() instanceof ConfigNodeComplexValue) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    if (!replaced) {
                        node = node.replaceValue(((ConfigNodeComplexValue) node.value()).changeValueOnPath(remainingPath, value));
                        if (node.render() != children.get(i).render())
                            replaced = true;
                        childrenCopy.set(i, node);
                    } else {
                        node = node.replaceValue(((ConfigNodeComplexValue) node.value()).removeValueOnPath(remainingPath));
                        childrenCopy.set(i, node);
                    }
                }
            }
        }
        return new ConfigNodeComplexValue(childrenCopy);
    }

    public ConfigNodeComplexValue setValueOnPath(Path desiredPath, AbstractConfigNodeValue value) {
        ConfigNodeComplexValue node = changeValueOnPath(desiredPath, value);

        // If the desired Path did not exist, add it
        if (node.render().equals(render())) {
            ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(children);
            ArrayList<AbstractConfigNode> newNodes = new ArrayList();
            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
            newNodes.add(new ConfigNodeKey(desiredPath));
            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(new ConfigNodeSingleToken(Tokens.COLON));
            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(value);
            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
            childrenCopy.add(new ConfigNodeKeyValue(newNodes));
            node = new ConfigNodeComplexValue(childrenCopy);
        }
        return node;
    }

    public ConfigNodeComplexValue removeValueOnPath(Path desiredPath) {
        return changeValueOnPath(desiredPath, null);
    }

}
