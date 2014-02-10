package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigRenderOptions;
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

    final private List<AbstractConfigValue> pieces;

    ConfigConcatenation(ConfigOrigin origin, List<AbstractConfigValue> pieces) {
        super(origin);
        this.pieces = pieces;

        if (pieces.size() < 2)
            throw new ConfigException.BugOrBroken("Created concatenation with less than 2 items: "
                    + this);

        boolean hadUnmergeable = false;
        for (AbstractConfigValue p : pieces) {
            if (p instanceof ConfigConcatenation)
                throw new ConfigException.BugOrBroken(
                        "ConfigConcatenation should never be nested: " + this);
            if (p instanceof Unmergeable)
                hadUnmergeable = true;
        }
        if (!hadUnmergeable)
            throw new ConfigException.BugOrBroken(
                    "Created concatenation without an unmergeable in it: " + this);
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
    protected ConfigConcatenation newCopy(ConfigOrigin newOrigin) {
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

    /**
     * Add left and right, or their merger, to builder.
     */
    private static void join(ArrayList<AbstractConfigValue> builder, AbstractConfigValue origRight) {
        AbstractConfigValue left = builder.get(builder.size() - 1);
        AbstractConfigValue right = origRight;

        // check for an object which can be converted to a list
        // (this will be an object with numeric keys, like foo.0, foo.1)
        if (left instanceof ConfigObject && right instanceof SimpleConfigList) {
            left = DefaultTransformer.transform(left, ConfigValueType.LIST);
        } else if (left instanceof SimpleConfigList && right instanceof ConfigObject) {
            right = DefaultTransformer.transform(right, ConfigValueType.LIST);
        }

        // Since this depends on the type of two instances, I couldn't think
        // of much alternative to an instanceof chain. Visitors are sometimes
        // used for multiple dispatch but seems like overkill.
        AbstractConfigValue joined = null;
        if (left instanceof ConfigObject && right instanceof ConfigObject) {
            joined = right.withFallback(left);
        } else if (left instanceof SimpleConfigList && right instanceof SimpleConfigList) {
            joined = ((SimpleConfigList)left).concatenate((SimpleConfigList)right);
        } else if (left instanceof ConfigConcatenation || right instanceof ConfigConcatenation) {
            throw new ConfigException.BugOrBroken("unflattened ConfigConcatenation");
        } else if (left instanceof Unmergeable || right instanceof Unmergeable) {
            // leave joined=null, cannot join
        } else {
            // handle primitive type or primitive type mixed with object or list
            String s1 = left.transformToString();
            String s2 = right.transformToString();
            if (s1 == null || s2 == null) {
                throw new ConfigException.WrongType(left.origin(),
                        "Cannot concatenate object or list with a non-object-or-list, " + left
                                + " and " + right + " are not compatible");
            } else {
                ConfigOrigin joinedOrigin = SimpleConfigOrigin.mergeOrigins(left.origin(),
                        right.origin());
                joined = new ConfigString(joinedOrigin, s1 + s2);
            }
        }

        if (joined == null) {
            builder.add(right);
        } else {
            builder.remove(builder.size() - 1);
            builder.add(joined);
        }
    }

    static List<AbstractConfigValue> consolidate(List<AbstractConfigValue> pieces) {
        if (pieces.size() < 2) {
            return pieces;
        } else {
            List<AbstractConfigValue> flattened = new ArrayList<AbstractConfigValue>(pieces.size());
            for (AbstractConfigValue v : pieces) {
                if (v instanceof ConfigConcatenation) {
                    flattened.addAll(((ConfigConcatenation) v).pieces);
                } else {
                    flattened.add(v);
                }
            }

            ArrayList<AbstractConfigValue> consolidated = new ArrayList<AbstractConfigValue>(
                    flattened.size());
            for (AbstractConfigValue v : flattened) {
                if (consolidated.isEmpty())
                    consolidated.add(v);
                else
                    join(consolidated, v);
            }

            return consolidated;
        }
    }

    static AbstractConfigValue concatenate(List<AbstractConfigValue> pieces) {
        List<AbstractConfigValue> consolidated = consolidate(pieces);
        if (consolidated.isEmpty()) {
            return null;
        } else if (consolidated.size() == 1) {
            return consolidated.get(0);
        } else {
            ConfigOrigin mergedOrigin = SimpleConfigOrigin.mergeOrigins(consolidated);
            return new ConfigConcatenation(mergedOrigin, consolidated);
        }
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
                resolved.add(r);
            }
        }

        // now need to concat everything
        List<AbstractConfigValue> joined = consolidate(resolved);
        // if unresolved is allowed we can just become another
        // ConfigConcatenation
        if (joined.size() > 1 && context.options().getAllowUnresolved())
            return new ConfigConcatenation(this.origin(), joined);
        else if (joined.size() != 1)
            throw new ConfigException.BugOrBroken(
                    "Resolved list should always join to exactly one value, not " + joined);
        else
            return joined.get(0);
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

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigConcatenation;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigConcatenation) {
            return canEqual(other) && this.pieces.equals(((ConfigConcatenation) other).pieces);
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
    protected void render(StringBuilder sb, int indent, boolean atRoot, ConfigRenderOptions options) {
        for (AbstractConfigValue p : pieces) {
            p.render(sb, indent, atRoot, options);
        }
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
