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
 * A ConfigConcatenation represents a list of values to be concatenated (see the
 * spec). It only has to exist if at least one value is an unresolved
 * substitution, otherwise we could go ahead and collapse the list into a single
 * value.
 *
 * Right now this is always a list of strings and ${} references, but in the
 * future should support a list of ConfigList. We may also support
 * concatenations of objects, but ConfigDelayedMerge should be used for that
 * since a concat of objects really will merge, not concatenate.
 */
final class ConfigConcatenation extends AbstractConfigValue implements Unmergeable {
    private static final long serialVersionUID = 1L;

    final private List<AbstractConfigValue> pieces;

    ConfigConcatenation(ConfigOrigin origin, List<AbstractConfigValue> pieces) {
        super(origin);
        this.pieces = pieces;
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
    protected ConfigConcatenation newCopy(boolean ignoresFallbacks, ConfigOrigin newOrigin) {
        return new ConfigConcatenation(newOrigin, pieces);
    }

    @Override
    protected boolean ignoresFallbacks() {
        // we can never ignore fallbacks because if a child ConfigReference
        // is self-referential we have to look lower in the merge stack
        // for its value.
        return false;
    }

    @Override
    public Collection<ConfigConcatenation> unmergedValues() {
        return Collections.singleton(this);
    }

    @Override
    AbstractConfigValue resolveSubstitutions(ResolveContext context) throws NotPossibleToResolve {
        List<AbstractConfigValue> resolved = new ArrayList<AbstractConfigValue>(pieces.size());
        for (AbstractConfigValue p : pieces) {
            // to concat into a string we have to do a full resolve,
            // so unrestrict the context
            AbstractConfigValue r = context.unrestricted().resolve(p);
            if (r == null) {
                // it was optional... omit
            } else {
                switch (r.valueType()) {
                case LIST:
                case OBJECT:
                    // cannot substitute lists and objects into strings
                    // we know p was a ConfigReference since it wasn't
                    // a ConfigString
                    String pathString = ((ConfigReference) p).expression().toString();
                    throw new ConfigException.WrongType(r.origin(), pathString,
                            "not a list or object", r.valueType().name());
                default:
                    resolved.add(r);
                }
            }
        }

        // now need to concat everything
        StringBuilder sb = new StringBuilder();
        for (AbstractConfigValue r : resolved) {
            sb.append(r.transformToString());
        }

        return new ConfigString(origin(), sb.toString());
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
    ConfigConcatenation relativized(Path prefix) {
        List<AbstractConfigValue> newPieces = new ArrayList<AbstractConfigValue>();
        for (AbstractConfigValue p : pieces) {
            newPieces.add(p.relativized(prefix));
        }
        return new ConfigConcatenation(origin(), newPieces);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigConcatenation || other instanceof ConfigSubstitution;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigConcatenation) {
            return canEqual(other) && this.pieces.equals(((ConfigConcatenation) other).pieces);
        } else if (other instanceof ConfigSubstitution) {
            return equals(((ConfigSubstitution) other).delegate());
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
        for (AbstractConfigValue p : pieces) {
            p.render(sb, indent, formatted);
        }
    }

    // This ridiculous hack is because some JDK versions apparently can't
    // serialize an array, which is used to implement ArrayList and EmptyList.
    // maybe
    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6446627
    private Object writeReplace() throws ObjectStreamException {
        // switch to LinkedList
        return new ConfigConcatenation(origin(), new java.util.LinkedList<AbstractConfigValue>(
                pieces));
    }

    static List<AbstractConfigValue> valuesFromPieces(ConfigOrigin origin, List<Object> pieces) {
        List<AbstractConfigValue> values = new ArrayList<AbstractConfigValue>(pieces.size());
        for (Object p : pieces) {
            if (p instanceof SubstitutionExpression) {
                values.add(new ConfigReference(origin, (SubstitutionExpression) p));
            } else if (p instanceof String) {
                values.add(new ConfigString(origin, (String) p));
            } else {
                throw new ConfigException.BugOrBroken("Unexpected piece " + p);
            }
        }

        return values;
    }
}
