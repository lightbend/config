/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class ConfigNodeSimpleValue extends AbstractConfigNodeValue {
    final Token token;
    ConfigNodeSimpleValue(Token value) {
        token = value;
    }

    @Override
    protected Collection<Token> tokens() {
        return Collections.singletonList(token);
    }

    protected Token token() { return token; }

    protected AbstractConfigValue value() {
        if (Tokens.isValue(token))
            return Tokens.getValue(token);
        else if (Tokens.isUnquotedText(token))
            return new ConfigString.Unquoted(token.origin(), Tokens.getUnquotedText(token));
        else if (Tokens.isSubstitution(token)) {
            List<Token> expression = Tokens.getSubstitutionPathExpression(token);
            boolean optional = Tokens.getSubstitutionOptional(token);

            // Detect and strip trailing [] for list expansion syntax.
            // Inside a substitution, [] is tokenized as OPEN_SQUARE + CLOSE_SQUARE.
            boolean listExpansion = false;
            List<Token> pathExpression = expression;
            int size = expression.size();
            if (size >= 2) {
                Token secondLast = expression.get(size - 2);
                Token last = expression.get(size - 1);
                if (secondLast == Tokens.OPEN_SQUARE && last == Tokens.CLOSE_SQUARE) {
                    listExpansion = true;
                    pathExpression = expression.subList(0, size - 2);
                }
            }

            Path path = PathParser.parsePathExpression(pathExpression.iterator(), token.origin());

            return new ConfigReference(token.origin(), new SubstitutionExpression(path, optional, listExpansion));
        }
        throw new ConfigException.BugOrBroken("ConfigNodeSimpleValue did not contain a valid value token");
    }
}
