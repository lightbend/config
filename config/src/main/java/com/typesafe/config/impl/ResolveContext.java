package com.typesafe.config.impl;

import java.util.Set;
import java.util.LinkedHashSet;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.AbstractConfigValue.SelfReferential;

final class ResolveContext {
    // this set is unfortunately mutable and the user of ResolveContext
    // has to be sure it's only shared between ResolveContext that
    // are in the same traversal.
    final private Set<MemoKey> traversed;
    final private ConfigResolveOptions options;
    final private Path restrictToChild; // can be null

    ResolveContext(Set<MemoKey> traversed, ConfigResolveOptions options, Path restrictToChild) {
        this.traversed = traversed;
        this.options = options;
        this.restrictToChild = restrictToChild;
    }

    ResolveContext(ConfigResolveOptions options, Path restrictToChild) {
        // LinkedHashSet keeps the traversal order which is at least useful
        // in error messages if nothing else
        this(new LinkedHashSet<MemoKey>(), options, restrictToChild);
    }

    void traverse(ConfigSubstitution value, Path via) throws SelfReferential {
        MemoKey key = new MemoKey(value, restrictToChild);
        if (traversed.contains(key))
            throw new SelfReferential(value.origin(), via.render());

        traversed.add(key);
    }

    void untraverse(ConfigSubstitution value) {
        MemoKey key = new MemoKey(value, restrictToChild);
        if (!traversed.remove(key))
            throw new ConfigException.BugOrBroken(
                    "untraverse() did not find the untraversed substitution " + value);
    }

    ConfigResolveOptions options() {
        return options;
    }

    boolean isRestrictedToChild() {
        return restrictToChild != null;
    }

    Path restrictToChild() {
        return restrictToChild;
    }

    ResolveContext restrict(Path restrictTo) {
        if (restrictTo == restrictToChild)
            return this;
        else
            return new ResolveContext(traversed, options, restrictTo);
    }

    ResolveContext unrestricted() {
        return restrict(null);
    }
}
