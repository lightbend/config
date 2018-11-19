package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
/**
 * ConfigReference replaces ConfigReference (the older class kept for back
 * compat) and represents the ${} substitution syntax. It can resolve to any
 * kind of value.
 */
final class ConfigReference(
    origin: ConfigOrigin,
    val expression: SubstitutionExpression, // the length of any prefixes added with relativized()
    val prefixLength: Int) extends AbstractConfigValue(origin)
    with Unmergeable {

    def this(origin: ConfigOrigin, expr: SubstitutionExpression) {
        this(origin, expr, 0)
    }
    private def notResolved = new ConfigException.NotResolved(
        "need to Config#resolve(), see the API docs for Config#resolve(); substitution not resolved: " + this)
    override def valueType = throw notResolved
    override def unwrapped = throw notResolved
    override def newCopy(newOrigin: ConfigOrigin) =
        new ConfigReference(newOrigin, expression, prefixLength)
    override def ignoresFallbacks = false
    override def unmergedValues: ju.Collection[ConfigReference] =
        ju.Collections.singleton(this)
    // ConfigReference should be a firewall against NotPossibleToResolve going
    // further up the stack; it should convert everything to ConfigException.
    // This way it's impossible for NotPossibleToResolve to "escape" since
    // any failure to resolve has to start with a ConfigReference.
    override def resolveSubstitutions(
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] = {
        var newContext = context.addCycleMarker(this)
        var v: AbstractConfigValue = null
        try {
            val resultWithPath =
                source.lookupSubst(newContext, expression, prefixLength)
            newContext = resultWithPath.result.context
            if (resultWithPath.result.value != null) {
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(
                        newContext.depth,
                        "recursively resolving " + resultWithPath + " which was the resolution of " +
                            expression + " against " + source)
                val recursiveResolveSource = new ResolveSource(
                    resultWithPath.pathFromRoot.last.asInstanceOf[AbstractConfigObject],
                    resultWithPath.pathFromRoot)
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(
                        newContext.depth,
                        "will recursively resolve against " + recursiveResolveSource)
                val result = newContext
                    .resolve(resultWithPath.result.value, recursiveResolveSource)
                v = result.value
                newContext = result.context
            } else {
                val fallback =
                    context.options.getResolver.lookup(expression.path.render)
                v = fallback.asInstanceOf[AbstractConfigValue]
            }
        } catch {
            case e: AbstractConfigValue.NotPossibleToResolve =>
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(
                        newContext.depth,
                        "not possible to resolve " + expression + ", cycle involved: " + e.traceString)
                if (expression.optional) v = null
                else
                    throw new ConfigException.UnresolvedSubstitution(
                        origin,
                        expression + " was part of a cycle of substitutions involving " + e.traceString,
                        e)
        }
        if (v == null && !expression.optional)
            if (newContext.options.getAllowUnresolved)
                ResolveResult.make(newContext.removeCycleMarker(this), this)
            else throw new ConfigException.UnresolvedSubstitution(origin, expression.toString)
        else ResolveResult.make(newContext.removeCycleMarker(this), v)
    }
    override def resolveStatus: ResolveStatus = ResolveStatus.UNRESOLVED
    // when you graft a substitution into another object,
    // you have to prefix it with the location in that object
    // where you grafted it; but save prefixLength so
    // system property and env variable lookups don't get
    // broken.
    override def relativized(prefix: Path): ConfigReference = {
        val newExpr =
            expression.changePath(expression.path.prepend(prefix))
        new ConfigReference(origin, newExpr, prefixLength + prefix.length)
    }
    override def canEqual(other: Any): Boolean =
        other.isInstanceOf[ConfigReference]
    override def equals(other: Any): Boolean = {
        // note that "origin" is deliberately NOT part of equality
        if (other.isInstanceOf[ConfigReference])
            canEqual(other) && this.expression == other
                .asInstanceOf[ConfigReference]
                .expression
        else false
    }
    override def hashCode: Int = expression.hashCode
    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit = {
        sb.append(expression.toString)
    }
}
