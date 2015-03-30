package com.typesafe.config.impl;

import com.typesafe.config.ConfigSyntax;

import java.util.ArrayList;
import java.util.Collection;

final class ConfigNodeObject extends ConfigNodeComplexValue {
    ConfigNodeObject(Collection<AbstractConfigNode> children) {
        super(children);
    }

    public boolean hasValue(Path desiredPath) {
        for (AbstractConfigNode node : children) {
            if (node instanceof ConfigNodeField) {
                Path key = ((ConfigNodeField) node).path().value();
                if (key.equals(desiredPath) || key.startsWith(desiredPath)) {
                    return true;
                } else if (desiredPath.startsWith(key)) {
                    if (((ConfigNodeField) node).value() instanceof ConfigNodeObject) {
                        Path remainingPath = desiredPath.subPath(key.length());
                        if (((ConfigNodeObject) ((ConfigNodeField) node).value()).hasValue(remainingPath)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    protected ConfigNodeObject changeValueOnPath(Path desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(super.children);
        boolean seenNonMatching = false;
        // Copy the value so we can change it to null but not modify the original parameter
        AbstractConfigNodeValue valueCopy = value;
        for (int i = childrenCopy.size() - 1; i >= 0; i--) {
            if (childrenCopy.get(i) instanceof ConfigNodeSingleToken) {
                Token t = ((ConfigNodeSingleToken) childrenCopy.get(i)).token();
                // Ensure that, when we are removing settings in JSON, we don't end up with a trailing comma
                if (flavor == ConfigSyntax.JSON && !seenNonMatching && t == Tokens.COMMA) {
                    childrenCopy.remove(i);
                }
                continue;
            } else if (!(childrenCopy.get(i) instanceof ConfigNodeField)) {
                continue;
            }
            ConfigNodeField node = (ConfigNodeField) childrenCopy.get(i);
            Path key = node.path().value();

            // Delete all multi-element paths that start with the desired path, since technically they are duplicates
            if ((valueCopy == null && key.equals(desiredPath))|| (key.startsWith(desiredPath) && !key.equals(desiredPath))) {
                childrenCopy.remove(i);
                // Remove any whitespace or commas after the deleted setting
                for (int j = i; j < childrenCopy.size(); j++) {
                    if (childrenCopy.get(j) instanceof ConfigNodeSingleToken) {
                        Token t = ((ConfigNodeSingleToken) childrenCopy.get(j)).token();
                        if (Tokens.isIgnoredWhitespace(t) || t == Tokens.COMMA) {
                            childrenCopy.remove(j);
                            j--;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            } else if (key.equals(desiredPath)) {
                seenNonMatching = true;
                childrenCopy.set(i, node.replaceValue(value));
                valueCopy = null;
            } else if (desiredPath.startsWith(key)) {
                seenNonMatching = true;
                if (node.value() instanceof ConfigNodeObject) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    childrenCopy.set(i, node.replaceValue(((ConfigNodeObject) node.value()).changeValueOnPath(remainingPath, valueCopy, flavor)));
                    if (valueCopy != null && !node.equals(super.children.get(i)))
                        valueCopy = null;
                }
            } else {
                seenNonMatching = true;
            }
        }
        return new ConfigNodeObject(childrenCopy);
    }

    public ConfigNodeObject setValueOnPath(String desiredPath, AbstractConfigNodeValue value) {
        return setValueOnPath(desiredPath, value, ConfigSyntax.CONF);
    }

    public ConfigNodeObject setValueOnPath(String desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        ConfigNodePath path = PathParser.parsePathNode(desiredPath, flavor);
        return setValueOnPath(path, value, flavor);
    }

    private ConfigNodeObject setValueOnPath(ConfigNodePath desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        ConfigNodeObject node = changeValueOnPath(desiredPath.value(), value, flavor);

        // If the desired Path did not exist, add it
        if (!node.hasValue(desiredPath.value())) {
            return node.addValueOnPath(desiredPath, value, flavor);
        }
        return node;
    }

    protected ConfigNodeObject addValueOnPath(ConfigNodePath desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        Path path = desiredPath.value();
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(super.children);
        if (path.length() > 1) {
            for (int i = super.children.size() - 1; i >= 0; i--) {
                if (!(super.children.get(i) instanceof ConfigNodeField)) {
                    continue;
                }
                ConfigNodeField node = (ConfigNodeField) super.children.get(i);
                Path key = node.path().value();
                if (path.startsWith(key) && node.value() instanceof ConfigNodeObject) {
                    ConfigNodePath remainingPath = desiredPath.subPath(key.length());
                    ConfigNodeObject newValue = (ConfigNodeObject) node.value();
                    childrenCopy.set(i, node.replaceValue(newValue.addValueOnPath(remainingPath, value, flavor)));
                    return new ConfigNodeObject(childrenCopy);
                }
            }
        }
        boolean startsWithBrace = super.children.get(0) instanceof ConfigNodeSingleToken &&
                ((ConfigNodeSingleToken) super.children.get(0)).token() == Tokens.OPEN_CURLY;
        ArrayList<AbstractConfigNode> newNodes = new ArrayList<AbstractConfigNode>();
        newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
        newNodes.add(desiredPath.first());
        newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
        newNodes.add(new ConfigNodeSingleToken(Tokens.COLON));
        newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));

        if (path.length() == 1) {
            newNodes.add(value);
        } else {
            ArrayList<AbstractConfigNode> newObjectNodes = new ArrayList<AbstractConfigNode>();
            newObjectNodes.add(new ConfigNodeSingleToken(Tokens.OPEN_CURLY));
            newObjectNodes.add(new ConfigNodeSingleToken(Tokens.CLOSE_CURLY));
            ConfigNodeObject newObject = new ConfigNodeObject(newObjectNodes);
            newNodes.add(newObject.addValueOnPath(desiredPath.subPath(1), value, flavor));
        }
        newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));

        // Combine these two cases so that we only have to iterate once
        if (flavor == ConfigSyntax.JSON || startsWithBrace) {
            for (int i = childrenCopy.size() - 1; i >= 0; i--) {

                // Valid JSON requires all key-value pairs except the last one to be succeeded by a comma,
                // so we'll need to add a comma when adding a value
                if (flavor == ConfigSyntax.JSON && childrenCopy.get(i) instanceof ConfigNodeField) {
                    childrenCopy.add(i+1, new ConfigNodeSingleToken(Tokens.COMMA));
                    break;
                }
                if (startsWithBrace && childrenCopy.get(i) instanceof ConfigNodeSingleToken &&
                        ((ConfigNodeSingleToken) childrenCopy.get(i)).token == Tokens.CLOSE_CURLY) {
                    childrenCopy.add(i, new ConfigNodeField(newNodes));
                }
            }
        }
        if (!startsWithBrace) {
            childrenCopy.add(new ConfigNodeField(newNodes));
        }
        return new ConfigNodeObject(childrenCopy);
    }

    public ConfigNodeObject removeValueOnPath(String desiredPath, ConfigSyntax flavor) {
        Path path = PathParser.parsePathNode(desiredPath, flavor).value();
        return changeValueOnPath(path, null, flavor);
    }
}
