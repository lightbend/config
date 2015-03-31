/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.*;

abstract class ConfigNodeComplexValue extends AbstractConfigNodeValue {
    final protected ArrayList<AbstractConfigNode> children;

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

    protected ConfigNodeComplexValue indentText(AbstractConfigNode indentation) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(children);
        for (int i = 0; i < childrenCopy.size(); i++) {
            if (childrenCopy.get(i) instanceof ConfigNodeSingleToken &&
                    Tokens.isNewline(((ConfigNodeSingleToken) childrenCopy.get(i)).token())) {
                childrenCopy.add(i + 1, indentation);
                i++;
            } else if (childrenCopy.get(i) instanceof ConfigNodeField) {
                AbstractConfigNode value = ((ConfigNodeField) childrenCopy.get(i)).value();
                if (value instanceof ConfigNodeComplexValue) {
                    childrenCopy.set(i, ((ConfigNodeField) childrenCopy.get(i)).replaceValue(((ConfigNodeComplexValue) value).indentText(indentation)));
                }
            } else if (childrenCopy.get(i) instanceof ConfigNodeComplexValue) {
                childrenCopy.set(i, ((ConfigNodeComplexValue) childrenCopy.get(i)).indentText(indentation));
            }
        }
        if (this instanceof ConfigNodeArray) {
            return new ConfigNodeArray(childrenCopy);
        } else if (this instanceof ConfigNodeObject) {
            return new ConfigNodeObject(childrenCopy);
        } else {
            return new ConfigNodeConcatenation(childrenCopy);
        }
    }
}
