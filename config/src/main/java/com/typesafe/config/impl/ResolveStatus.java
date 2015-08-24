/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

import java.util.Collection;

/**
 * Status of substitution resolution.
 */
enum ResolveStatus {
    UNRESOLVED, RESOLVED;

    final static ResolveStatus fromValues(
            Collection<? extends AbstractConfigValue> values, Collection<ConfigConditional> conditionals) {
        for (AbstractConfigValue v : values) {
            if (v.resolveStatus() == ResolveStatus.UNRESOLVED)
                return ResolveStatus.UNRESOLVED;
        }
        if (conditionals.size() > 0)
            return ResolveStatus.UNRESOLVED;
        return ResolveStatus.RESOLVED;
    }

    final static ResolveStatus fromBoolean(boolean resolved) {
        return resolved ? ResolveStatus.RESOLVED : ResolveStatus.UNRESOLVED;
    }
}
