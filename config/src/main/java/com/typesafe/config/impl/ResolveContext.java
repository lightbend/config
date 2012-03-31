package com.typesafe.config.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve;
import com.typesafe.config.impl.AbstractConfigValue.SelfReferential;
import com.typesafe.config.impl.ResolveReplacer.Undefined;

final class ResolveContext {
    // this is unfortunately mutable so should only be shared among
    // ResolveContext in the same traversal.
    final private ResolveSource source;

    // this is unfortunately mutable so should only be shared among
    // ResolveContext in the same traversal.
    final private ResolveMemos memos;

    // Resolves that we have already begun (for cycle detection).
    // SubstitutionResolver separately memoizes completed resolves.
    // this set is unfortunately mutable and the user of ResolveContext
    // has to be sure it's only shared between ResolveContext that
    // are in the same traversal.
    final private LinkedList<Set<MemoKey>> traversedStack;
    final private ConfigResolveOptions options;
    // the current path restriction, used to ensure lazy
    // resolution and avoid gratuitous cycles.
    // CAN BE NULL for a full resolve.
    final private Path restrictToChild;
    // if we try to resolve something in here, use the
    // given replacement instead.
    final private Map<MemoKey, LinkedList<ResolveReplacer>> replacements;

    ResolveContext(ResolveSource source, ResolveMemos memos,
            LinkedList<Set<MemoKey>> traversedStack, ConfigResolveOptions options,
            Path restrictToChild, Map<MemoKey, LinkedList<ResolveReplacer>> replacements) {
        this.source = source;
        this.memos = memos;
        this.traversedStack = traversedStack;
        this.options = options;
        this.restrictToChild = restrictToChild;
        this.replacements = replacements;
    }

    ResolveContext(AbstractConfigObject root, ConfigResolveOptions options, Path restrictToChild) {
        // LinkedHashSet keeps the traversal order which is at least useful
        // in error messages if nothing else
        this(new ResolveSource(root), new ResolveMemos(), new LinkedList<Set<MemoKey>>(
                Collections.singletonList(new LinkedHashSet<MemoKey>())), options, restrictToChild,
                new HashMap<MemoKey, LinkedList<ResolveReplacer>>());
    }

    private void traverse(ConfigSubstitution value, SubstitutionExpression via)
            throws SelfReferential {
        Set<MemoKey> traversed = traversedStack.peekFirst();

        MemoKey key = new MemoKey(value, restrictToChild);
        if (traversed.contains(key)) {
            throw new SelfReferential(value.origin(), via.path().render());
        }

        traversed.add(key);
    }

    private void untraverse(ConfigSubstitution value) {
        Set<MemoKey> traversed = traversedStack.peekFirst();

        MemoKey key = new MemoKey(value, restrictToChild);
        if (!traversed.remove(key))
            throw new ConfigException.BugOrBroken(
                    "untraverse() did not find the untraversed substitution " + value);
    }

    // this just exists to fix the "throws Exception" on Callable
    interface Resolver extends Callable<AbstractConfigValue> {
        @Override
        AbstractConfigValue call() throws NotPossibleToResolve;
    }

    AbstractConfigValue traversing(ConfigSubstitution value, SubstitutionExpression subst,
            Resolver callable) throws NotPossibleToResolve {
        try {
            traverse(value, subst);
        } catch (SelfReferential e) {
            if (subst.optional())
                return null;
            else
                throw e;
        }

        try {
            return callable.call();
        } finally {
            untraverse(value);
        }
    }

    void replace(AbstractConfigValue value, ResolveReplacer replacer) {
        MemoKey key = new MemoKey(value, null /* restrictToChild */);
        LinkedList<ResolveReplacer> stack = replacements.get(key);
        if (stack == null) {
            stack = new LinkedList<ResolveReplacer>();
            replacements.put(key, stack);
        }
        stack.addFirst(replacer);
        // we have to reset the cycle detection because with the
        // replacement, a cycle may be broken
        traversedStack.addFirst(new LinkedHashSet<MemoKey>());
    }

    void unreplace(AbstractConfigValue value) {
        MemoKey key = new MemoKey(value, null /* restrictToChild */);
        LinkedList<ResolveReplacer> stack = replacements.get(key);
        if (stack == null)
            throw new ConfigException.BugOrBroken("unreplace() without replace(): " + value);

        stack.removeFirst();
        Set<MemoKey> oldTraversed = traversedStack.removeFirst();
        if (!oldTraversed.isEmpty())
            throw new ConfigException.BugOrBroken(
                    "unreplace() with stuff still in the traverse set: " + oldTraversed);
    }

    AbstractConfigValue replacement(MemoKey key) throws Undefined {
        LinkedList<ResolveReplacer> stack = replacements.get(new MemoKey(key.value(), null));
        if (stack == null || stack.isEmpty())
            return key.value();
        else
            return stack.peek().replace();
    }

    ResolveSource source() {
        return source;
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
            return new ResolveContext(source, memos, traversedStack, options, restrictTo,
                    replacements);
    }

    ResolveContext unrestricted() {
        return restrict(null);
    }

    AbstractConfigValue resolve(SubstitutionResolver resolver, AbstractConfigValue original)
            throws NotPossibleToResolve {

        // a fully-resolved (no restrictToChild) object can satisfy a
        // request for a restricted object, so always check that first.
        final MemoKey fullKey = new MemoKey(original, null);
        MemoKey restrictedKey = null;

        AbstractConfigValue cached = memos.get(fullKey);

        // but if there was no fully-resolved object cached, we'll only
        // compute the restrictToChild object so use a more limited
        // memo key
        if (cached == null && isRestrictedToChild()) {
            restrictedKey = new MemoKey(original, restrictToChild());
            cached = memos.get(restrictedKey);
        }

        if (cached != null) {
            return cached;
        } else {
            MemoKey key = restrictedKey != null ? restrictedKey : fullKey;

            AbstractConfigValue replacement;
            boolean forceUndefined = false;
            try {
                replacement = replacement(key);
            } catch (Undefined e) {
                replacement = original;
                forceUndefined = true;
            }

            if (replacement != original) {
                // start over, checking if replacement was memoized
                return resolve(resolver, replacement);
            } else {
                AbstractConfigValue resolved;

                if (forceUndefined)
                    resolved = null;
                else
                    resolved = original.resolveSubstitutions(resolver, this);

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
                    if (isRestrictedToChild()) {
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
    }
}
