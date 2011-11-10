package com.typesafe.config.impl;

import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * A ConfigSubstitution represents a value with one or more substitutions in it;
 * it can resolve to a value of any type, though if the substitution has more
 * than one piece it always resolves to a string via value concatenation.
 */
final class ConfigSubstitution extends AbstractConfigValue {

    // this is a list of String and Path where the Path
    // have to be resolved to values, then if there's more
    // than one piece everything is stringified and concatenated
    private List<Object> pieces;

    ConfigSubstitution(ConfigOrigin origin, List<Object> pieces) {
        super(origin);
        this.pieces = pieces;
    }

    @Override
    public ConfigValueType valueType() {
        throw new ConfigException.BugOrBroken(
                "tried to get value type on a ConfigSubstitution; need to resolve substitution first");
    }

    @Override
    public Object unwrapped() {
        throw new ConfigException.BugOrBroken(
                "tried to unwrap a ConfigSubstitution; need to resolve substitution first");
    }

    List<Object> pieces() {
        return pieces;
    }

    // larger than anyone would ever want
    private static final int MAX_DEPTH = 100;

    private ConfigValue findInObject(AbstractConfigObject root,
            SubstitutionResolver resolver, /* null if we should not have refs */
            Path subst, int depth,
            boolean withFallbacks) {
        if (depth > MAX_DEPTH) {
            throw new ConfigException.BadValue(origin(), subst.render(),
                    "Substitution ${" + subst.render()
                            + "} is part of a cycle of substitutions");
        }

        ConfigValue result = root.peekPath(subst, resolver, depth,
                    withFallbacks);

        if (result instanceof ConfigSubstitution) {
            throw new ConfigException.BugOrBroken(
                    "peek or peekPath returned an unresolved substitution");
        }

        if (result != null && result.valueType() == ConfigValueType.NULL) {
            result = null;
        }

        return result;
    }

    private ConfigValue resolve(SubstitutionResolver resolver, Path subst,
            int depth, boolean withFallbacks) {
        ConfigValue result = findInObject(resolver.root(), resolver, subst,
                depth, withFallbacks);
        if (withFallbacks) {
            if (result == null) {
                result = findInObject(ConfigImpl.systemPropertiesConfig(),
                        null,
                        subst, depth, withFallbacks);
            }
            if (result == null) {
                result = findInObject(ConfigImpl.envVariablesConfig(), null,
                        subst,
                        depth, withFallbacks);
            }
        }
        if (result == null) {
            result = new ConfigNull(origin());
        }
        return result;
    }

    private ConfigValue resolve(SubstitutionResolver resolver, int depth,
            boolean withFallbacks) {
        if (pieces.size() > 1) {
            // need to concat everything into a string
            StringBuilder sb = new StringBuilder();
            for (Object p : pieces) {
                if (p instanceof String) {
                    sb.append((String) p);
                } else {
                    ConfigValue v = resolve(resolver, (Path) p,
                            depth, withFallbacks);
                    switch (v.valueType()) {
                    case NULL:
                        // nothing; becomes empty string
                        break;
                    case LIST:
                    case OBJECT:
                        // cannot substitute lists and objects into strings
                        throw new ConfigException.WrongType(v.origin(),
                                ((Path) p).render(),
                                "not a list or object", v.valueType().name());
                    default:
                        sb.append(((AbstractConfigValue) v).transformToString());
                    }
                }
            }
            return new ConfigString(origin(), sb.toString());
        } else {
            if (!(pieces.get(0) instanceof Path))
                throw new ConfigException.BugOrBroken(
                        "ConfigSubstitution should never contain a single String piece");
            return resolve(resolver, (Path) pieces.get(0), depth,
                    withFallbacks);
        }
    }

    @Override
    AbstractConfigValue resolveSubstitutions(SubstitutionResolver resolver,
            int depth,
            boolean withFallbacks) {
        // only ConfigSubstitution adds to depth here, because the depth
        // is the substitution depth not the recursion depth
        AbstractConfigValue resolved = (AbstractConfigValue) resolve(resolver,
                depth + 1, withFallbacks);
        return resolved;
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
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SUBST");
        sb.append("(");
        for (Object p : pieces) {
            sb.append(p.toString());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1); // chop comma
        sb.append(")");
        return sb.toString();
    }
}
