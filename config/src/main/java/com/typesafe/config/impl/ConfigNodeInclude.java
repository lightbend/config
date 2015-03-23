package com.typesafe.config.impl;

import java.util.Collection;

final class ConfigNodeInclude extends ConfigNodeComplexValue {
    ConfigNodeInclude(Collection<AbstractConfigNode> children) {
        super(children);
    }

    protected String kind() {
        for (AbstractConfigNode n : children) {
            if (n instanceof ConfigNodeSingleToken) {
                Token t = ((ConfigNodeSingleToken) n).token();
                if (Tokens.isUnquotedText(t) && !t.tokenText().equals("include") &&
                        t.tokenText().matches(".*\\w.*")) {
                    return t.tokenText();
                }
            }
        }
        return null;
    }

    protected String name() {
        for (AbstractConfigNode n : children) {
            if (n instanceof ConfigNodeSimpleValue) {
                return (String)Tokens.getValue(((ConfigNodeSimpleValue) n).token()).unwrapped();
            }
        }
        return null;
    }
}
