package com.typesafe.config.impl;

/** Callback that generates a replacement to use for resolving a substitution. */
abstract class ResolveReplacer {
    static final class Undefined extends Exception {
        private static final long serialVersionUID = 1L;

        Undefined() {
            super("No replacement, substitution will resolve to undefined");
        }
    }

    // this is a "lazy val" in essence (we only want to
    // make the replacement one time). Making it volatile
    // is good enough for thread safety as long as this
    // cache is only an optimization and making the replacement
    // twice has no side effects, which it should not...
    private volatile AbstractConfigValue replacement = null;

    final AbstractConfigValue replace() throws Undefined {
        if (replacement == null)
            replacement = makeReplacement();
        return replacement;
    }

    protected abstract AbstractConfigValue makeReplacement() throws Undefined;
}
