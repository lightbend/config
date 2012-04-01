package com.typesafe.config.impl;

import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;

import com.typesafe.config.ConfigException;
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve;
import com.typesafe.config.impl.ResolveReplacer.Undefined;

/**
 * This class is the source for values for a substitution like ${foo}.
 */
final class ResolveSource {
    final private AbstractConfigObject root;
    // Conceptually, we transform the ResolveSource whenever we traverse
    // a substitution or delayed merge stack, in order to remove the
    // traversed node and therefore avoid circular dependencies.
    // We implement it with this somewhat hacky "patch a replacement"
    // mechanism instead of actually transforming the tree.
    final private Map<AbstractConfigValue, LinkedList<ResolveReplacer>> replacements;

    ResolveSource(AbstractConfigObject root) {
        this.root = root;
        this.replacements = new IdentityHashMap<AbstractConfigValue, LinkedList<ResolveReplacer>>();
    }

    static private AbstractConfigValue findInObject(final AbstractConfigObject obj,
            final ResolveContext context, ConfigReference traversed,
            final SubstitutionExpression subst) throws NotPossibleToResolve {
        return context.traversing(traversed, subst, new ResolveContext.Resolver() {
            @Override
            public AbstractConfigValue call() throws NotPossibleToResolve {
                return obj.peekPath(subst.path(), context);
            }
        });
    }

    AbstractConfigValue lookupSubst(final ResolveContext context, ConfigReference traversed,
            final SubstitutionExpression subst, int prefixLength) throws NotPossibleToResolve {
        // First we look up the full path, which means relative to the
        // included file if we were not a root file
        AbstractConfigValue result = findInObject(root, context, traversed, subst);

        if (result == null) {
            // Then we want to check relative to the root file. We don't
            // want the prefix we were included at to be used when looking
            // up env variables either.
            SubstitutionExpression unprefixed = subst
                    .changePath(subst.path().subPath(prefixLength));

            if (result == null && prefixLength > 0) {
                result = findInObject(root, context, traversed, unprefixed);
            }

            if (result == null && context.options().getUseSystemEnvironment()) {
                result = findInObject(ConfigImpl.envVariablesAsConfigObject(), context, traversed,
                        unprefixed);
            }
        }

        if (result != null) {
            final AbstractConfigValue unresolved = result;
            result = context.traversing(traversed, subst, new ResolveContext.Resolver() {
                @Override
                public AbstractConfigValue call() throws NotPossibleToResolve {
                    return context.resolve(unresolved);
                }
            });
        }

        return result;
    }

    void replace(AbstractConfigValue value, ResolveReplacer replacer) {
        LinkedList<ResolveReplacer> stack = replacements.get(value);
        if (stack == null) {
            stack = new LinkedList<ResolveReplacer>();
            replacements.put(value, stack);
        }
        stack.addFirst(replacer);
    }

    void unreplace(AbstractConfigValue value) {
        LinkedList<ResolveReplacer> stack = replacements.get(value);
        if (stack == null)
            throw new ConfigException.BugOrBroken("unreplace() without replace(): " + value);

        stack.removeFirst();
    }

    private AbstractConfigValue replacement(AbstractConfigValue value) throws Undefined {
        LinkedList<ResolveReplacer> stack = replacements.get(value);
        if (stack == null || stack.isEmpty())
            return value;
        else
            return stack.peek().replace();
    }

    /**
     * Conceptually, this is key.value().resolveSubstitutions() but using the
     * replacement for key.value() if any.
     */
    AbstractConfigValue resolveCheckingReplacement(ResolveContext context,
            AbstractConfigValue original) throws NotPossibleToResolve {
        AbstractConfigValue replacement;
        boolean forceUndefined = false;
        try {
            replacement = replacement(original);
        } catch (Undefined e) {
            replacement = original;
            forceUndefined = true;
        }

        if (replacement != original) {
            // start over, checking if replacement was memoized
            return context.resolve(replacement);
        } else {
            AbstractConfigValue resolved;

            if (forceUndefined)
                resolved = null;
            else
                resolved = original.resolveSubstitutions(context);

            return resolved;
        }
    }
}
