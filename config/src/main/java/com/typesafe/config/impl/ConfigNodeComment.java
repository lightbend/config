package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigNodeVisitor;

final class ConfigNodeComment extends ConfigNodeSingleToken implements com.typesafe.config.parser.ConfigNodeComment {
    ConfigNodeComment(Token comment) {
        super(comment);
        if (!Tokens.isComment(super.token)) {
            throw new ConfigException.BugOrBroken("Tried to create a ConfigNodeComment from a non-comment token");
        }
    }

    protected String commentText() {
        return Tokens.getCommentText(super.token);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitComment(this);
    }

    @Override
    public String getValue() {
        return commentText();
    }
}
