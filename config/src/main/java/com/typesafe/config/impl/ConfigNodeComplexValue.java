/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.*;

final class ConfigNodeComplexValue extends AbstractConfigNodeValue {
    final private ArrayList<AbstractConfigNode> children;

    ConfigNodeComplexValue(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList(children);
    }

    final public Collection<AbstractConfigNode> children() {
        return children;
    }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }

    protected ConfigNodeComplexValue changeValueOnPath(Path desiredPath, AbstractConfigNodeValue value) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList(children);
        // Copy the value so we can change it to null but not modify the original parameter
        AbstractConfigNodeValue valueCopy = value;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (!(children.get(i) instanceof ConfigNodeField)) {
                continue;
            }
            ConfigNodeField node = (ConfigNodeField)children.get(i);
            Path key = node.path().value();
            if (key.equals(desiredPath)) {
                if (valueCopy == null)
                    childrenCopy.remove(i);
                else {
                    childrenCopy.set(i, node.replaceValue(value));
                    valueCopy = null;
                }
            } else if (desiredPath.startsWith(key)) {
                if (node.value() instanceof ConfigNodeComplexValue) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    childrenCopy.set(i, node.replaceValue(((ConfigNodeComplexValue) node.value()).changeValueOnPath(remainingPath, valueCopy)));
                    if (valueCopy != null && node.render() != children.get(i).render())
                        valueCopy = null;
                }
            }
        }
        return new ConfigNodeComplexValue(childrenCopy);
    }

    public ConfigNodeComplexValue setValueOnPath(String desiredPath, AbstractConfigNodeValue value) {
        ConfigNodePath path = PathParser.parsePathNode(desiredPath);
        return setValueOnPath(path, value);
    }

    private ConfigNodeComplexValue setValueOnPath(ConfigNodePath desiredPath, AbstractConfigNodeValue value) {
        ConfigNodeComplexValue node = changeValueOnPath(desiredPath.value(), value);

        // If the desired Path did not exist, add it
        if (node.render().equals(render())) {
            ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(children);
            ArrayList<AbstractConfigNode> newNodes = new ArrayList();
            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
            newNodes.add(desiredPath);
            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(new ConfigNodeSingleToken(Tokens.COLON));
            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
            newNodes.add(value);
            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
            childrenCopy.add(new ConfigNodeField(newNodes));
            node = new ConfigNodeComplexValue(childrenCopy);
        }
        return node;
    }

    public ConfigNodeComplexValue removeValueOnPath(String desiredPath) {
        Path path = PathParser.parsePath(desiredPath);
        return changeValueOnPath(path, null);
    }

}
