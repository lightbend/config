package com.typesafe.config.impl;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This exists because we have to memoize resolved substitutions as we go
 * through the config tree; otherwise we could end up creating multiple copies
 * of values or whole trees of values as we follow chains of substitutions.
 */
final class SubstitutionResolver {
    private AbstractConfigObject root;
    private Map<AbstractConfigValue, AbstractConfigValue> memos;

    SubstitutionResolver(AbstractConfigObject root) {
        this.root = root;
        // note: the memoization is by object identity, not object value
        this.memos = new IdentityHashMap<AbstractConfigValue, AbstractConfigValue>();
    }

    AbstractConfigValue resolve(AbstractConfigValue original, int depth,
            boolean withFallbacks) {
        if (memos.containsKey(original)) {
            return memos.get(original);
        } else {
            AbstractConfigValue resolved = original.resolveSubstitutions(this,
                    depth,
                    withFallbacks);
            memos.put(original, resolved);
            return resolved;
        }
    }

    AbstractConfigObject root() {
        return this.root;
    }

    private static AbstractConfigValue resolve(AbstractConfigValue value,
            AbstractConfigObject root, boolean withFallbacks) {
        SubstitutionResolver resolver = new SubstitutionResolver(root);
        return resolver.resolve(value, 0, withFallbacks);
    }

    static AbstractConfigValue resolve(AbstractConfigValue value,
            AbstractConfigObject root) {
        return resolve(value, root, true /* withFallbacks */);
    }

    static AbstractConfigValue resolveWithoutFallbacks(
            AbstractConfigValue value, AbstractConfigObject root) {
        return resolve(value, root, false /* withFallbacks */);
    }
}
