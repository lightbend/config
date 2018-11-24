/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }
import ju.Collections

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve

/**
 *
 * Trying very hard to avoid a parent reference in config values; when you have
 * a tree like this, the availability of parent() tends to result in a lot of
 * improperly-factored and non-modular code. Please don't add parent().
 *
 */
object AbstractConfigValue {

    /**
     * This exception means that a value is inherently not resolveable, at the
     * moment the only known cause is a cycle of substitutions. This is a
     * checked exception since it's internal to the library and we want to be
     * sure we handle it before passing it out to public API. This is only
     * supposed to be thrown by the target of a cyclic reference and it's
     * supposed to be caught by the ConfigReference looking up that reference,
     * so it should be impossible for an outermost resolve() to throw this.
     *
     * Contrast with ConfigException.NotResolved which just means nobody called
     * resolve().
     */
    @SerialVersionUID(1L)
    class NotPossibleToResolve private[impl] (
        val context: ResolveContext) extends RuntimeException("was not possible to resolve") {

        private[impl] val traceString: String = context.traceString
    }

    def replaceChildInList(
        list: ju.List[AbstractConfigValue],
        child: AbstractConfigValue,
        replacement: AbstractConfigValue): ju.List[AbstractConfigValue] = {
        var i = 0
        while (i < list.size && (list.get(i) != child)) {
            i += 1
        }
        if (i == list.size)
            throw new ConfigException.BugOrBroken(
                "tried to replace " + child + " which is not in " + list)
        val newStack =
            new ju.ArrayList[AbstractConfigValue](list)
        if (replacement != null) newStack.set(i, replacement) else newStack.remove(i)
        if (newStack.isEmpty) null else newStack
    }

    def hasDescendantInList(
        list: ju.List[AbstractConfigValue],
        descendant: AbstractConfigValue): Boolean = {
        import scala.collection.JavaConverters._
        for (v <- list.asScala) {
            if (v == descendant) return true
        }
        // now the expensive traversal
        for (v <- list.asScala) {
            if (v.isInstanceOf[Container] && v
                .asInstanceOf[Container]
                .hasDescendant(descendant)) return true
        }
        false
    }

    def indent(
        sb: jl.StringBuilder,
        indent: Int,
        options: ConfigRenderOptions): Unit = {
        if (options.getFormatted) {
            var remaining = indent
            while (remaining > 0) {
                sb.append("    ")
                remaining -= 1
            }
        }
    }

    private[impl] trait Modifier {
        // keyOrNull is null for non-objects
        @throws[Exception]
        def modifyChildMayThrow(
            keyOrNull: String,
            v: AbstractConfigValue): AbstractConfigValue
    }

    private[impl] abstract class NoExceptionsModifier
        extends AbstractConfigValue.Modifier {

        @throws[Exception]
        override final def modifyChildMayThrow(
            keyOrNull: String,
            v: AbstractConfigValue): AbstractConfigValue =
            try modifyChild(keyOrNull, v)
            catch {
                case e: RuntimeException =>
                    throw e
                case e: Exception =>
                    throw new ConfigException.BugOrBroken("Unexpected exception", e)
            }

        private[impl] def modifyChild(
            keyOrNull: String,
            v: AbstractConfigValue): AbstractConfigValue

    }
}

abstract class AbstractConfigValue private[impl] (val _origin: ConfigOrigin)
    extends ConfigValue
    with MergeableValue {

    override def origin(): SimpleConfigOrigin = this._origin.asInstanceOf[SimpleConfigOrigin]

    /**
     * Called only by ResolveContext.resolve().
     *
     * @param context
     *            state of the current resolve
     * @param source
     *            where to look up values
     * @return a new value if there were changes, or this if no changes
     */
    @throws[NotPossibleToResolve]
    def resolveSubstitutions(
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] =
        ResolveResult.make(context, this)

    private[impl] def resolveStatus = ResolveStatus.RESOLVED

    /**
     * This is used when including one file in another; the included file is
     * relativized to the path it's included into in the parent file. The point
     * is that if you include a file at foo.bar in the parent, and the included
     * file as a substitution ${a.b.c}, the included substitution now needs to
     * be ${foo.bar.a.b.c} because we resolve substitutions globally only after
     * parsing everything.
     *
     * @param prefix
     * @return value relativized to the given path or the same value if nothing
     *         to do
     */
    private[impl] def relativized(prefix: Path) = this

    override def toFallbackValue: AbstractConfigValue = this

    protected def newCopy(origin: ConfigOrigin): AbstractConfigValue

    // this is virtualized rather than a field because only some subclasses
    // really need to store the boolean, and they may be able to pack it
    // with another boolean to save space.
    private[impl] def ignoresFallbacks: Boolean = { // if we are not resolved, then somewhere in this value there's
        // a substitution that may need to look at the fallbacks.
        resolveStatus == ResolveStatus.RESOLVED
    }

    protected def withFallbacksIgnored: AbstractConfigValue = if (ignoresFallbacks) this
    else
        throw new ConfigException.BugOrBroken(
            "value class doesn't implement forced fallback-ignoring " + this)

    // the withFallback() implementation is supposed to avoid calling
    // mergedWith* if we're ignoring fallbacks.
    final protected def requireNotIgnoringFallbacks(): Unit = {
        if (ignoresFallbacks) throw new ConfigException.BugOrBroken(
            "method should not have been called with ignoresFallbacks=true " + getClass.getSimpleName)
    }

    protected def constructDelayedMerge(
        origin: ConfigOrigin,
        stack: ju.List[AbstractConfigValue]): AbstractConfigValue = new ConfigDelayedMerge(origin, stack)

    final protected def mergedWithTheUnmergeable(
        stack: ju.Collection[AbstractConfigValue],
        fallback: Unmergeable): AbstractConfigValue = {
        requireNotIgnoringFallbacks()
        // if we turn out to be an object, and the fallback also does,
        // then a merge may be required; delay until we resolve.
        val newStack = new ju.ArrayList[AbstractConfigValue]
        newStack.addAll(stack)
        newStack.addAll(fallback.unmergedValues)
        constructDelayedMerge(
            AbstractConfigObject.mergeOrigins(newStack),
            newStack)
    }

    final private def delayMerge(
        stack: ju.Collection[AbstractConfigValue],
        fallback: AbstractConfigValue) = { // then a merge may be required.
        // if we contain a substitution, resolving it may need to look
        // back to the fallback.
        val newStack = new ju.ArrayList[AbstractConfigValue]
        newStack.addAll(stack)
        newStack.add(fallback)
        constructDelayedMerge(
            AbstractConfigObject.mergeOrigins(newStack),
            newStack)
    }

    final protected def mergedWithObject(
        stack: ju.Collection[AbstractConfigValue],
        fallback: AbstractConfigObject): AbstractConfigValue = {
        requireNotIgnoringFallbacks()
        if (this.isInstanceOf[AbstractConfigObject]) throw new ConfigException.BugOrBroken(
            "Objects must reimplement mergedWithObject")
        mergedWithNonObject(stack, fallback)
    }

    final protected def mergedWithNonObject(
        stack: ju.Collection[AbstractConfigValue],
        fallback: AbstractConfigValue): AbstractConfigValue = {
        requireNotIgnoringFallbacks()
        if (resolveStatus == ResolveStatus.RESOLVED) { // falling back to a non-object doesn't merge anything, and also
            // prohibits merging any objects that we fall back to later.
            // so we have to switch to ignoresFallbacks mode.
            withFallbacksIgnored
        } else { // if unresolved, we may have to look back to fallbacks as part of
            // the resolution process, so always delay
            delayMerge(stack, fallback)
        }
    }

    protected def mergedWithTheUnmergeable(
        fallback: Unmergeable): AbstractConfigValue = {
        requireNotIgnoringFallbacks()
        mergedWithTheUnmergeable(Collections.singletonList(this), fallback)
    }

    protected def mergedWithObject(
        fallback: AbstractConfigObject): AbstractConfigValue = {
        requireNotIgnoringFallbacks()
        mergedWithObject(Collections.singletonList(this), fallback)
    }

    protected def mergedWithNonObject(
        fallback: AbstractConfigValue): AbstractConfigValue = {
        requireNotIgnoringFallbacks()
        mergedWithNonObject(Collections.singletonList(this), fallback)
    }

    override def withOrigin(origin: ConfigOrigin): AbstractConfigValue = if (this.origin eq origin) this else newCopy(origin)

    // this is only overridden to change the return type
    override def withFallback(mergeable: ConfigMergeable): AbstractConfigValue = if (ignoresFallbacks) this
    else {
        val other =
            mergeable.asInstanceOf[MergeableValue].toFallbackValue
        if (other.isInstanceOf[Unmergeable])
            mergedWithTheUnmergeable(other.asInstanceOf[Unmergeable])
        else if (other.isInstanceOf[AbstractConfigObject])
            mergedWithObject(other.asInstanceOf[AbstractConfigObject])
        else mergedWithNonObject(other.asInstanceOf[AbstractConfigValue])
    }

    protected def canEqual(other: Any): Boolean = other.isInstanceOf[ConfigValue]

    override def equals(other: Any): Boolean = { // note that "origin" is deliberately NOT part of equality
        if (other.isInstanceOf[ConfigValue]) canEqual(other) && this.valueType == (other
            .asInstanceOf[ConfigValue])
            .valueType && ConfigImplUtil.equalsHandlingNull(
                this.unwrapped,
                other.asInstanceOf[ConfigValue].unwrapped)
        else false
    }

    override def hashCode: Int = {
        val o = this.unwrapped
        if (o == null) 0 else o.hashCode
    }

    override def toString: String = {
        val sb = new jl.StringBuilder
        render(
            sb,
            0,
            true /* atRoot */ ,
            null /* atKey */ ,
            ConfigRenderOptions.concise)
        getClass.getSimpleName + "(" + sb.toString + ")"
    }

    private[impl] def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        atKey: String,
        options: ConfigRenderOptions): Unit = {
        if (atKey != null) {
            val renderedKey =
                if (options.getJson) ConfigImplUtil.renderJsonString(atKey)
                else ConfigImplUtil.renderStringUnquotedIfPossible(atKey)
            sb.append(renderedKey)
            if (options.getJson) {
                if (options.getFormatted)
                    sb.append(" : ")
                else
                    sb.append(":")
            } else { // in non-JSON we can omit the colon or equals before an object
                if (this.isInstanceOf[ConfigObject]) {
                    if (options.getFormatted) sb.append(' ')
                } else {
                    sb.append("=")
                }
            }
        }
        render(sb, indent, atRoot, options)
    }
    def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit = {
        val u = unwrapped
        sb.append(u.toString)
    }
    override final def render: String = render(ConfigRenderOptions.defaults)

    override final def render(options: ConfigRenderOptions): String = {
        val sb = new jl.StringBuilder
        render(sb, 0, true, null, options)
        sb.toString
    }

    // toString() is a debugging-oriented string but this is defined
    // to create a string that would parse back to the value in JSON.
    // It only works for primitive values (that would be a single token)
    // which are auto-converted to strings when concatenating with
    // other strings or by the DefaultTransformer.
    private[impl] def transformToString: String = null

    private[impl] def atKey(origin: ConfigOrigin, key: String): SimpleConfig = {
        val m =
            Collections.singletonMap(key, this)
        new SimpleConfigObject(origin, m).toConfig
    }

    override def atKey(key: String): SimpleConfig = atKey(SimpleConfigOrigin.newSimple("atKey(" + key + ")"), key)

    private[impl] def atPath(origin: ConfigOrigin, path: Path): SimpleConfig = {
        var parent = path.parent
        var result = atKey(origin, path.last)
        while (parent != null) {
            val key = parent.last
            result = result.atKey(origin, key)
            parent = parent.parent
        }
        result
    }

    override def atPath(pathExpression: String): SimpleConfig = {
        val origin =
            SimpleConfigOrigin.newSimple("atPath(" + pathExpression + ")")
        atPath(origin, Path.newPath(pathExpression))
    }
}
