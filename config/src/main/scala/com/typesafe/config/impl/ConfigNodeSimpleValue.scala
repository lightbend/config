/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import java.{ util => ju }

final class ConfigNodeSimpleValue private[impl] (val token: Token)
    extends AbstractConfigNodeValue {

    override def tokens: ju.Collection[Token] = ju.Collections.singletonList(token)

    private[impl] def value: AbstractConfigValue = {
        if (Tokens.isValue(token)) return Tokens.getValue(token)
        else if (Tokens.isUnquotedText(token))
            return new ConfigString.Unquoted(
                token.origin,
                Tokens.getUnquotedText(token))
        else if (Tokens.isSubstitution(token)) {
            val expression =
                Tokens.getSubstitutionPathExpression(token)
            val path =
                PathParser.parsePathExpression(expression.iterator, token.origin)
            val optional = Tokens.getSubstitutionOptional(token)
            return new ConfigReference(
                token.origin,
                new SubstitutionExpression(path, optional))
        }
        throw new ConfigException.BugOrBroken(
            "ConfigNodeSimpleValue did not contain a valid value token")
    }
}
