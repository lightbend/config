package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

/**
 * ConfigReference replaces ConfigReference (the older class kept for back
 * compat) and represents the ${} substitution syntax. It can resolve to any
 * kind of value.
 */
final class ConfigReference extends AbstractConfigValue implements Unmergeable {
    private static final long serialVersionUID = 1L;

    final private SubstitutionExpression expr;
    // the length of any prefixes added with relativized()
    final private int prefixLength;

    ConfigReference(ConfigOrigin origin, SubstitutionExpression expr) {
        this(origin, expr, 0);
    }

    private ConfigReference(ConfigOrigin origin, SubstitutionExpression expr, int prefixLength) {
        super(origin);
        this.expr = expr;
        this.prefixLength = prefixLength;
    }

    private ConfigException.NotResolved notResolved() {
        return new ConfigException.NotResolved(
                "need to Config#resolve(), see the API docs for Config#resolve(); substitution not resolved: "
                        + this);
    }

    @Override
    public ConfigValueType valueType() {
        throw notResolved();
    }

    @Override
    public Object unwrapped() {
        throw notResolved();
    }

    @Override
    protected ConfigReference newCopy(boolean ignoresFallbacks, ConfigOrigin newOrigin) {
        if (ignoresFallbacks)
            throw new ConfigException.BugOrBroken("Cannot ignore fallbacks for " + this);
        return new ConfigReference(newOrigin, expr, prefixLength);
    }

    @Override
    protected boolean ignoresFallbacks() {
        return false;
    }

    @Override
    protected AbstractConfigValue mergedWithTheUnmergeable(Unmergeable fallback) {
        // if we turn out to be an object, and the fallback also does,
        // then a merge may be required; delay until we resolve.
        List<AbstractConfigValue> newStack = new ArrayList<AbstractConfigValue>();
        newStack.add(this);
        newStack.addAll(fallback.unmergedValues());
        return new ConfigDelayedMerge(AbstractConfigObject.mergeOrigins(newStack), newStack,
                ((AbstractConfigValue) fallback).ignoresFallbacks());
    }

    protected AbstractConfigValue mergedLater(AbstractConfigValue fallback) {
        List<AbstractConfigValue> newStack = new ArrayList<AbstractConfigValue>();
        newStack.add(this);
        newStack.add(fallback);
        return new ConfigDelayedMerge(AbstractConfigObject.mergeOrigins(newStack), newStack,
                fallback.ignoresFallbacks());
    }

    @Override
    protected AbstractConfigValue mergedWithObject(AbstractConfigObject fallback) {
        // if we turn out to be an object, and the fallback also does,
        // then a merge may be required; delay until we resolve.
        return mergedLater(fallback);
    }

    @Override
    protected AbstractConfigValue mergedWithNonObject(AbstractConfigValue fallback) {
        // We may need the fallback for two reasons:
        // 1. if an optional substitution ends up getting deleted
        // because it is not defined
        // 2. if the substitution is self-referential
        //
        // we can't easily detect the self-referential case since the cycle
        // may involve more than one step, so we have to wait and
        // merge later when resolving.
        return mergedLater(fallback);
    }

    @Override
    public Collection<ConfigReference> unmergedValues() {
        return Collections.singleton(this);
    }

    @Override
    AbstractConfigValue resolveSubstitutions(ResolveContext context) throws NotPossibleToResolve {
        AbstractConfigValue v = context.source().lookupSubst(context, this, expr, prefixLength);

        if (v == null && !expr.optional()) {
            throw new ConfigException.UnresolvedSubstitution(origin(), expr.toString());
        }
        return v;
    }

    @Override
    ResolveStatus resolveStatus() {
        return ResolveStatus.UNRESOLVED;
    }

    // when you graft a substitution into another object,
    // you have to prefix it with the location in that object
    // where you grafted it; but save prefixLength so
    // system property and env variable lookups don't get
    // broken.
    @Override
    ConfigReference relativized(Path prefix) {
        SubstitutionExpression newExpr = expr.changePath(expr.path().prepend(prefix));
        return new ConfigReference(origin(), newExpr, prefixLength + prefix.length());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigReference || other instanceof ConfigSubstitution;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigReference) {
            return canEqual(other) && this.expr.equals(((ConfigReference) other).expr);
        } else if (other instanceof ConfigSubstitution) {
            return equals(((ConfigSubstitution) other).delegate());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return expr.hashCode();
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean formatted) {
        sb.append(expr.toString());
    }

    SubstitutionExpression expression() {
        return expr;
    }
}
