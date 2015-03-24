/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

import java.util.ArrayList;
import java.util.Collection;

final class ConfigNodeField extends AbstractConfigNode {
    final private ArrayList<AbstractConfigNode> children;

    public ConfigNodeField(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList(children);
    }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }

    public ConfigNodeField replaceValue(AbstractConfigNodeValue newValue) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList(children);
        for (int i = 0; i < childrenCopy.size(); i++) {
            if (childrenCopy.get(i) instanceof AbstractConfigNodeValue) {
                childrenCopy.set(i, newValue);
                return new ConfigNodeField(childrenCopy);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value");
    }

    public AbstractConfigNodeValue value() {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof AbstractConfigNodeValue) {
                return (AbstractConfigNodeValue)children.get(i);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value");
    }

    public ConfigNodePath path() {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof ConfigNodePath) {
                return (ConfigNodePath)children.get(i);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a path");
    }
}
