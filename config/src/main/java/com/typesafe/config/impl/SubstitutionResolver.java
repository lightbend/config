/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.AbstractConfigValue.NeedsFullResolve;
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve;

/**
 * This exists because we have to memoize resolved substitutions as we go
 * through the config tree; otherwise we could end up creating multiple copies
 * of values or whole trees of values as we follow chains of substitutions.
 */
final class SubstitutionResolver {
    final private AbstractConfigObject root;
    // note that we can resolve things to undefined (represented as Java null,
    // rather than ConfigNull) so this map can have null values.
    final private Map<MemoKey, AbstractConfigValue> memos;

    SubstitutionResolver(AbstractConfigObject root) {
        this.root = root;
        this.memos = new HashMap<MemoKey, AbstractConfigValue>();
    }

    AbstractConfigValue resolve(AbstractConfigValue original, ResolveContext context)
            throws NotPossibleToResolve, NeedsFullResolve {

        // a fully-resolved (no restrictToChild) object can satisfy a
        // request for a restricted object, so always check that first.
        final MemoKey fullKey = new MemoKey(original, null);
        MemoKey restrictedKey = null;

        AbstractConfigValue cached = memos.get(fullKey);

        // but if there was no fully-resolved object cached, we'll only
        // compute the restrictToChild object so use a more limited
        // memo key
        if (cached == null && context.isRestrictedToChild()) {
            restrictedKey = new MemoKey(original, context.restrictToChild());
            cached = memos.get(restrictedKey);
        }

        if (cached != null) {
            return cached;
        } else {
            AbstractConfigValue resolved = original.resolveSubstitutions(this, context);

            if (resolved == null || resolved.resolveStatus() == ResolveStatus.RESOLVED) {
                // if the resolved object is fully resolved by resolving
                // only the restrictToChildOrNull, then it can be cached
                // under fullKey since the child we were restricted to
                // turned out to be the only unresolved thing.
                memos.put(fullKey, resolved);
            } else {
                // if we have an unresolved object then either we did a
                // partial resolve restricted to a certain child, or it's
                // a bug.
                if (context.isRestrictedToChild()) {
                    if (restrictedKey == null) {
                        throw new ConfigException.BugOrBroken(
                                "restrictedKey should not be null here");
                    }
                    memos.put(restrictedKey, resolved);
                } else {
                    throw new ConfigException.BugOrBroken(
                            "resolveSubstitutions() did not give us a resolved object");
                }
            }

            return resolved;
        }
    }

    AbstractConfigObject root() {
        return this.root;
    }

    static AbstractConfigValue resolve(AbstractConfigValue value, AbstractConfigObject root,
            ConfigResolveOptions options, Path restrictToChildOrNull) throws NotPossibleToResolve,
            NeedsFullResolve {
        SubstitutionResolver resolver = new SubstitutionResolver(root);

        return resolver.resolve(value, new ResolveContext(options, restrictToChildOrNull));
    }

    static AbstractConfigValue resolveWithExternalExceptions(AbstractConfigValue value,
            AbstractConfigObject root, ConfigResolveOptions options) {
        SubstitutionResolver resolver = new SubstitutionResolver(root);
        try {
            return resolver.resolve(value, new ResolveContext(options, null /* restrictToChild */));
        } catch (NotPossibleToResolve e) {
            throw e.exportException(value.origin(), null);
        } catch (NeedsFullResolve e) {
            throw new ConfigException.NotResolved(value.origin().description()
                    + ": Must resolve() config object before use", e);
        }
    }
}
