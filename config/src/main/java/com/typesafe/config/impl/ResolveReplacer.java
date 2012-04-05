package com.typesafe.config.impl;

import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve;

/** Callback that generates a replacement to use for resolving a substitution. */
abstract class ResolveReplacer {
    // this is a "lazy val" in essence (we only want to
    // make the replacement one time). Making it volatile
    // is good enough for thread safety as long as this
    // cache is only an optimization and making the replacement
    // twice has no side effects, which it should not...
    private volatile AbstractConfigValue replacement = null;

    final AbstractConfigValue replace(ResolveContext context) throws NotPossibleToResolve {
        if (replacement == null)
            replacement = makeReplacement(context);
        return replacement;
    }

    protected abstract AbstractConfigValue makeReplacement(ResolveContext context)
            throws NotPossibleToResolve;

    static final ResolveReplacer cycleResolveReplacer = new ResolveReplacer() {
        @Override
        protected AbstractConfigValue makeReplacement(ResolveContext context)
                throws NotPossibleToResolve {
            throw new NotPossibleToResolve(context);
        }
    };
}
