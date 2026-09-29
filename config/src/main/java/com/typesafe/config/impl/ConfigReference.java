package com.typesafe.config.impl;

import java.util.Collection;
import java.util.Collections;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * ConfigReference replaces ConfigReference (the older class kept for back
 * compat) and represents the ${} substitution syntax. It can resolve to any
 * kind of value.
 */
final class ConfigReference extends AbstractConfigValue implements Unmergeable {

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
    protected ConfigReference newCopy(ConfigOrigin newOrigin) {
        return new ConfigReference(newOrigin, expr, prefixLength);
    }

    @Override
    protected boolean ignoresFallbacks() {
        return false;
    }

    @Override
    public Collection<ConfigReference> unmergedValues() {
        return Collections.singleton(this);
    }

    // ConfigReference should be a firewall against NotPossibleToResolve going
    // further up the stack; it should convert everything to ConfigException.
    // This way it's impossible for NotPossibleToResolve to "escape" since
    // any failure to resolve has to start with a ConfigReference.
    @Override
    ResolveResult<? extends AbstractConfigValue> resolveSubstitutions(ResolveContext context, ResolveSource source) {
        ResolveContext newContext = context.addCycleMarker(this);
        AbstractConfigValue v;
        try {
            int cyclesBefore = newContext.swallowedCycles();
            ResolveSource.ResultWithPath resultWithPath = source.lookupSubst(newContext, expr, prefixLength);
            newContext = resultWithPath.result.context;
            ResolveResult<? extends AbstractConfigValue> result = resolveFoundValue(newContext, source, resultWithPath);
            newContext = result.context;
            v = result.value;

            // HOCON.md "Include semantics: substitution": a value found in the included file that
            // resolves to nothing leaves the including root to apply. Not after a swallowed cycle:
            // then the outcome would depend on which key of the cycle resolved first.
            if (v == null && prefixLength > 0 && resultWithPath.foundAtFullPath
                    && newContext.swallowedCycles() == cyclesBefore) {
                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(newContext.depth(), expr + " found nothing in the included file, "
                            + "retrying relative to the including root");
                resultWithPath = source.lookupSubst(newContext, expr, prefixLength, true);
                newContext = resultWithPath.result.context;
                result = resolveFoundValue(newContext, source, resultWithPath);
                newContext = result.context;
                v = result.value;
            }
        } catch (NotPossibleToResolve e) {
            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(newContext.depth(),
                        "not possible to resolve " + expr + ", cycle involved: " + e.traceString());
            if (expr.optional()) {
                v = null;
                newContext = newContext.countSwallowedCycle();
            } else
                throw new ConfigException.UnresolvedSubstitution(origin(), expr
                        + " was part of a cycle of substitutions involving " + e.traceString(), e);
        }

        if (v == null && !expr.optional()) {
            if (newContext.options().getAllowUnresolved())
                return ResolveResult.make(newContext.removeCycleMarker(this), this);
            else
                throw new ConfigException.UnresolvedSubstitution(origin(), expr.toString());
        } else {
            // A pending merge that carries ignored fallbacks (partial resolve)
            // cannot drop them without losing the null the source key needs, so
            // keep this reference and substitute on a later resolve.
            if (newContext.options().getAllowUnresolved() && v instanceof Unmergeable
                    && SimpleConfigObject.carriesIgnoredFallback(v))
                return ResolveResult.make(newContext.removeCycleMarker(this), this);
            // The source key's ignored fallbacks are a merge instruction for that
            // key, not part of the value, so the substituted copy drops them.
            if (v instanceof SimpleConfigObject)
                v = ((SimpleConfigObject) v).deferPendingIgnoredFallbacks(this, expr.path()).withFallbacksNotIgnored();
            return ResolveResult.make(newContext.removeCycleMarker(this), v);
        }
    }

    // Resolves the value a lookup found, against the object it was found in;
    // if the lookup found nothing, asks the resolver instead.
    private ResolveResult<? extends AbstractConfigValue> resolveFoundValue(ResolveContext context, ResolveSource source,
            ResolveSource.ResultWithPath resultWithPath) throws NotPossibleToResolve {
        if (resultWithPath.result.value != null) {
            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(context.depth(), "recursively resolving " + resultWithPath
                        + " which was the resolution of " + expr + " against " + source);

            ResolveSource recursiveResolveSource = (new ResolveSource(
                    (AbstractConfigObject) resultWithPath.pathFromRoot.last(), resultWithPath.pathFromRoot));

            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(context.depth(), "will recursively resolve against " + recursiveResolveSource);

            return context.resolve(resultWithPath.result.value, recursiveResolveSource);
        } else {
            ConfigValue fallback = context.options().getResolver().lookup(expr.path().render());
            return ResolveResult.make(context, (AbstractConfigValue) fallback);
        }
    }

    // The same kind of reference to another path: the prefix is kept, so a
    // reference from an included file still falls back to the including root.
    ConfigReference withPath(Path path, ConfigOrigin origin) {
        return new ConfigReference(origin, expr.changePath(path), prefixLength);
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

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigReference;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigReference) {
            return canEqual(other) && this.expr.equals(((ConfigReference) other).expr);
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
    protected void render(StringBuilder sb, int indent, boolean atRoot, ConfigRenderOptions options) {
        sb.append(expr.toString());
    }

    SubstitutionExpression expression() {
        return expr;
    }
}
