package com.typesafe.config.impl;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve;

final class ResolveContext {
    // this is unfortunately mutable so should only be shared among
    // ResolveContext in the same traversal.
    final private ResolveMemos memos;

    final private ConfigResolveOptions options;
    // the current path restriction, used to ensure lazy
    // resolution and avoid gratuitous cycles. without this,
    // any sibling of an object we're traversing could
    // cause a cycle "by side effect"
    // CAN BE NULL for a full resolve.
    final private Path restrictToChild;

    // This is used for tracing and debugging and nice error messages;
    // contains every node as we call resolve on it.
    final private List<AbstractConfigValue> resolveStack;

    final private Set<AbstractConfigValue> cycleMarkers;

    ResolveContext(ResolveMemos memos, ConfigResolveOptions options, Path restrictToChild,
            List<AbstractConfigValue> resolveStack, Set<AbstractConfigValue> cycleMarkers) {
        this.memos = memos;
        this.options = options;
        this.restrictToChild = restrictToChild;
        this.resolveStack = resolveStack;
        this.cycleMarkers = cycleMarkers;
    }

    ResolveContext(ConfigResolveOptions options, Path restrictToChild) {
        // LinkedHashSet keeps the traversal order which is at least useful
        // in error messages if nothing else
        this(new ResolveMemos(), options, restrictToChild, new ArrayList<AbstractConfigValue>(), Collections
                        .newSetFromMap(new IdentityHashMap<AbstractConfigValue, Boolean>()));
        if (ConfigImpl.traceSubstitutionsEnabled())
            ConfigImpl.trace(depth(), "ResolveContext restrict to child " + restrictToChild);
    }

    void addCycleMarker(AbstractConfigValue value) {
        if (ConfigImpl.traceSubstitutionsEnabled())
            ConfigImpl.trace(depth(), "++ Cycle marker " + value + "@" + System.identityHashCode(value));
        if (cycleMarkers.contains(value))
            throw new ConfigException.BugOrBroken("Added cycle marker twice " + value);
        cycleMarkers.add(value);
    }

    void removeCycleMarker(AbstractConfigValue value) {
        cycleMarkers.remove(value);
        if (ConfigImpl.traceSubstitutionsEnabled())
            ConfigImpl.trace(depth(), "-- Cycle marker " + value + "@" + System.identityHashCode(value));
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
            return new ResolveContext(memos, options, restrictTo, resolveStack, cycleMarkers);
    }

    ResolveContext unrestricted() {
        return restrict(null);
    }

    String traceString() {
        String separator = ", ";
        StringBuilder sb = new StringBuilder();
        for (AbstractConfigValue value : resolveStack) {
            if (value instanceof ConfigReference) {
                sb.append(((ConfigReference) value).expression().toString());
                sb.append(separator);
            }
        }
        if (sb.length() > 0)
            sb.setLength(sb.length() - separator.length());
        return sb.toString();
    }

    private void pushTrace(AbstractConfigValue value) {
        if (ConfigImpl.traceSubstitutionsEnabled())
            ConfigImpl.trace(depth(), "pushing trace " + value);
        resolveStack.add(value);
    }

    private void popTrace() {
        AbstractConfigValue old = resolveStack.remove(resolveStack.size() - 1);
        if (ConfigImpl.traceSubstitutionsEnabled())
            ConfigImpl.trace(depth(), "popped trace " + old);
    }

    int depth() {
        if (resolveStack.size() > 30)
            throw new ConfigException.BugOrBroken("resolve getting too deep");
        return resolveStack.size();
    }

    AbstractConfigValue resolve(AbstractConfigValue original, ResolveSource source) throws NotPossibleToResolve {
        if (ConfigImpl.traceSubstitutionsEnabled())
            ConfigImpl
                    .trace(depth(), "resolving " + original + " restrictToChild=" + restrictToChild + " in " + source);
        AbstractConfigValue resolved;
        pushTrace(original);
        try {
            resolved = realResolve(original, source);
        } finally {
            popTrace();
        }
        return resolved;
    }

    private AbstractConfigValue realResolve(AbstractConfigValue original, ResolveSource source)
            throws NotPossibleToResolve {
        // a fully-resolved (no restrictToChild) object can satisfy a
        // request for a restricted object, so always check that first.
        final MemoKey fullKey = new MemoKey(source.root, original, null);
        MemoKey restrictedKey = null;

        AbstractConfigValue cached = memos.get(fullKey);

        // but if there was no fully-resolved object cached, we'll only
        // compute the restrictToChild object so use a more limited
        // memo key
        if (cached == null && isRestrictedToChild()) {
            restrictedKey = new MemoKey(source.root, original, restrictToChild());
            cached = memos.get(restrictedKey);
        }

        if (cached != null) {
            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(depth(), "using cached resolution " + cached + " for " + original
                        + " restrictToChild " + restrictToChild());
            return cached;
        } else {
            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(depth(),
                        "not found in cache, resolving " + original + "@" + System.identityHashCode(original));

            if (cycleMarkers.contains(original)) {
                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(depth(),
                            "Cycle detected, can't resolve; " + original + "@" + System.identityHashCode(original));
                throw new NotPossibleToResolve(this);
            }

            AbstractConfigValue resolved = original.resolveSubstitutions(this, source);

            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(depth(), "resolved to " + resolved + "@" + System.identityHashCode(resolved)
                        + " from " + original + "@" + System.identityHashCode(resolved));

            if (resolved == null || resolved.resolveStatus() == ResolveStatus.RESOLVED) {
                // if the resolved object is fully resolved by resolving
                // only the restrictToChildOrNull, then it can be cached
                // under fullKey since the child we were restricted to
                // turned out to be the only unresolved thing.
                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(depth(), "caching " + fullKey + " result " + resolved);

                memos.put(fullKey, resolved);
            } else {
                // if we have an unresolved object then either we did a
                // partial resolve restricted to a certain child, or we are
                // allowing incomplete resolution, or it's a bug.
                if (isRestrictedToChild()) {
                    if (restrictedKey == null) {
                        throw new ConfigException.BugOrBroken(
                                "restrictedKey should not be null here");
                    }
                    if (ConfigImpl.traceSubstitutionsEnabled())
                        ConfigImpl.trace(depth(), "caching " + restrictedKey + " result " + resolved);

                    memos.put(restrictedKey, resolved);
                } else if (options().getAllowUnresolved()) {
                    if (ConfigImpl.traceSubstitutionsEnabled())
                        ConfigImpl.trace(depth(), "caching " + fullKey + " result " + resolved);

                    memos.put(fullKey, resolved);
                } else {
                    throw new ConfigException.BugOrBroken(
                            "resolveSubstitutions() did not give us a resolved object");
                }
            }

            return resolved;
        }
    }

    static AbstractConfigValue resolve(AbstractConfigValue value, AbstractConfigObject root,
            ConfigResolveOptions options) {
        ResolveSource source = new ResolveSource(root);
        ResolveContext context = new ResolveContext(options, null /* restrictToChild */);

        try {
            return context.resolve(value, source);
        } catch (NotPossibleToResolve e) {
            // ConfigReference was supposed to catch NotPossibleToResolve
            throw new ConfigException.BugOrBroken(
                    "NotPossibleToResolve was thrown from an outermost resolve", e);
        }
    }
}
