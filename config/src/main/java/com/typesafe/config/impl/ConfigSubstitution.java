/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

/**
 * A ConfigSubstitution represents a value with one or more substitutions in it;
 * it can resolve to a value of any type, though if the substitution has more
 * than one piece it always resolves to a string via value concatenation.
 */
final class ConfigSubstitution extends AbstractConfigValue implements
        Unmergeable {

    private static final long serialVersionUID = 1L;

    // this is a list of String and SubstitutionExpression where the
    // SubstitutionExpression has to be resolved to values, then if there's more
    // than one piece everything is stringified and concatenated
    final private List<Object> pieces;
    // the length of any prefixes added with relativized()
    final private int prefixLength;

    // this is just here to avoid breaking serialization;
    // it is always false at the moment.
    final private boolean ignoresFallbacks = false;

    ConfigSubstitution(ConfigOrigin origin, List<Object> pieces) {
        this(origin, pieces, 0, false);
    }

    private ConfigSubstitution(ConfigOrigin origin, List<Object> pieces,
            int prefixLength, boolean ignoresFallbacks) {
        super(origin);
        this.pieces = pieces;
        this.prefixLength = prefixLength;

        for (Object p : pieces) {
            if (p instanceof Path)
                throw new RuntimeException("broken here");
        }

        if (ignoresFallbacks)
            throw new ConfigException.BugOrBroken("ConfigSubstitution may never ignore fallbacks");
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
    protected ConfigSubstitution newCopy(boolean ignoresFallbacks, ConfigOrigin newOrigin) {
        return new ConfigSubstitution(newOrigin, pieces, prefixLength, ignoresFallbacks);
    }

    @Override
    protected boolean ignoresFallbacks() {
        return ignoresFallbacks;
    }

    @Override
    protected AbstractConfigValue mergedWithTheUnmergeable(Unmergeable fallback) {
        if (ignoresFallbacks)
            throw new ConfigException.BugOrBroken("should not be reached");

        // if we turn out to be an object, and the fallback also does,
        // then a merge may be required; delay until we resolve.
        List<AbstractConfigValue> newStack = new ArrayList<AbstractConfigValue>();
        newStack.add(this);
        newStack.addAll(fallback.unmergedValues());
        return new ConfigDelayedMerge(AbstractConfigObject.mergeOrigins(newStack), newStack,
                ((AbstractConfigValue) fallback).ignoresFallbacks());
    }

    protected AbstractConfigValue mergedLater(AbstractConfigValue fallback) {
        if (ignoresFallbacks)
            throw new ConfigException.BugOrBroken("should not be reached");

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
    public Collection<ConfigSubstitution> unmergedValues() {
        return Collections.singleton(this);
    }

    List<Object> pieces() {
        return pieces;
    }



    private static ResolveReplacer undefinedReplacer = new ResolveReplacer() {
        @Override
        protected AbstractConfigValue makeReplacement() throws Undefined {
            throw new Undefined();
        }
    };

    private AbstractConfigValue resolveValueConcat(ResolveContext context)
            throws NotPossibleToResolve {
        // need to concat everything into a string
        StringBuilder sb = new StringBuilder();
        for (Object p : pieces) {
            if (p instanceof String) {
                sb.append((String) p);
            } else {
                SubstitutionExpression exp = (SubstitutionExpression) p;

                // to concat into a string we have to do a full resolve,
                // so unrestrict the context
                AbstractConfigValue v = context.source().lookupSubst(context.unrestricted(), this,
                        exp, prefixLength);

                if (v == null) {
                    if (exp.optional()) {
                        // append nothing to StringBuilder
                    } else {
                        throw new ConfigException.UnresolvedSubstitution(origin(), exp.toString());
                    }
                } else {
                    switch (v.valueType()) {
                    case LIST:
                    case OBJECT:
                        // cannot substitute lists and objects into strings
                        throw new ConfigException.WrongType(v.origin(), exp.path().render(),
                                "not a list or object", v.valueType().name());
                    default:
                        sb.append(v.transformToString());
                    }
                }
            }
        }
        return new ConfigString(origin(), sb.toString());
    }

    private AbstractConfigValue resolveSingleSubst(ResolveContext context)
            throws NotPossibleToResolve {

        if (!(pieces.get(0) instanceof SubstitutionExpression))
            throw new ConfigException.BugOrBroken(
                    "ConfigSubstitution should never contain a single String piece");

        SubstitutionExpression exp = (SubstitutionExpression) pieces.get(0);
        AbstractConfigValue v = context.source().lookupSubst(context, this, exp,
                prefixLength);

        if (v == null && !exp.optional()) {
            throw new ConfigException.UnresolvedSubstitution(origin(), exp.toString());
        }
        return v;
    }

    @Override
    AbstractConfigValue resolveSubstitutions(ResolveContext context)
            throws NotPossibleToResolve {
        AbstractConfigValue resolved;
        if (pieces.size() > 1) {
            // if you have "foo = ${?foo}bar" then we will
            // self-referentially look up foo and we need to
            // get undefined, rather than "bar"
            context.replace(this, undefinedReplacer);
            try {
                resolved = resolveValueConcat(context);
            } finally {
                context.unreplace(this);
            }
        } else {
            resolved = resolveSingleSubst(context);
        }
        return resolved;
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
    ConfigSubstitution relativized(Path prefix) {
        List<Object> newPieces = new ArrayList<Object>();
        for (Object p : pieces) {
            if (p instanceof SubstitutionExpression) {
                SubstitutionExpression exp = (SubstitutionExpression) p;

                newPieces.add(exp.changePath(exp.path().prepend(prefix)));
            } else {
                newPieces.add(p);
            }
        }
        return new ConfigSubstitution(origin(), newPieces, prefixLength
                + prefix.length(), ignoresFallbacks);
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigSubstitution;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigSubstitution) {
            return canEqual(other)
                    && this.pieces.equals(((ConfigSubstitution) other).pieces);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return pieces.hashCode();
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean formatted) {
        for (Object p : pieces) {
            if (p instanceof SubstitutionExpression) {
                sb.append(p.toString());
            } else {
                sb.append(ConfigImplUtil.renderJsonString((String) p));
            }
        }
    }

    // This ridiculous hack is because some JDK versions apparently can't
    // serialize an array, which is used to implement ArrayList and EmptyList.
    // maybe
    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6446627
    private Object writeReplace() throws ObjectStreamException {
        // switch to LinkedList
        return new ConfigSubstitution(origin(), new java.util.LinkedList<Object>(pieces),
                prefixLength, ignoresFallbacks);
    }
}
