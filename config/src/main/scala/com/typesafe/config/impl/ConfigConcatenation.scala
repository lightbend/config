package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueType

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
object ConfigConcatenation {
    private def isIgnoredWhitespace(value: AbstractConfigValue) =
        value.isInstanceOf[ConfigString] && !(value
            .asInstanceOf[ConfigString])
            .wasQuoted

    /**
     * Add left and right, or their merger, to builder.
     */
    private def join(
        builder: ju.ArrayList[AbstractConfigValue],
        origRight: AbstractConfigValue): Unit = {
        var left = builder.get(builder.size - 1)
        var right = origRight
        // check for an object which can be converted to a list
        // (this will be an object with numeric keys, like foo.0, foo.1)
        if (left.isInstanceOf[ConfigObject] && right
            .isInstanceOf[SimpleConfigList])
            left = DefaultTransformer.transform(left, ConfigValueType.LIST)
        else if (left.isInstanceOf[SimpleConfigList] && right
            .isInstanceOf[ConfigObject])
            right = DefaultTransformer.transform(right, ConfigValueType.LIST)
        // Since this depends on the type of two instances, I couldn't think
        // of much alternative to an instanceof chain. Visitors are sometimes
        // used for multiple dispatch but seems like overkill.
        var joined: AbstractConfigValue = null
        if (left.isInstanceOf[ConfigObject] && right.isInstanceOf[ConfigObject])
            joined = right.withFallback(left)
        else if (left.isInstanceOf[SimpleConfigList] && right
            .isInstanceOf[SimpleConfigList])
            joined = left
                .asInstanceOf[SimpleConfigList]
                .concatenate(right.asInstanceOf[SimpleConfigList])
        else if ((left.isInstanceOf[SimpleConfigList] || left
            .isInstanceOf[ConfigObject]) && isIgnoredWhitespace(right)) {
            joined = left
            // it should be impossible that left is whitespace and right is a list or object
        } else if (left.isInstanceOf[ConfigConcatenation] || right
            .isInstanceOf[ConfigConcatenation])
            throw new ConfigException.BugOrBroken(
                "unflattened ConfigConcatenation")
        else if (left.isInstanceOf[Unmergeable] || right
            .isInstanceOf[Unmergeable]) {
            // leave joined=null, cannot join
        } else { // handle primitive type or primitive type mixed with object or list
            val s1 = left.transformToString
            val s2 = right.transformToString
            if (s1 == null || s2 == null)
                throw new ConfigException.WrongType(
                    left.origin,
                    "Cannot concatenate object or list with a non-object-or-list, " + left + " and " + right + " are not compatible")
            else {
                val joinedOrigin =
                    SimpleConfigOrigin.mergeOrigins(left.origin, right.origin)
                joined = new ConfigString.Quoted(joinedOrigin, s1 + s2)
            }
        }
        if (joined == null) builder.add(right)
        else {
            builder.remove(builder.size - 1)
            builder.add(joined)
        }
    }
    private[impl] def consolidate(
        pieces: ju.List[AbstractConfigValue]): ju.List[AbstractConfigValue] =
        if (pieces.size < 2) pieces
        else {
            val flattened =
                new ju.ArrayList[AbstractConfigValue](pieces.size)
            import scala.collection.JavaConverters._
            for (v <- pieces.asScala) {
                if (v.isInstanceOf[ConfigConcatenation])
                    flattened.addAll(v.asInstanceOf[ConfigConcatenation].pieces)
                else flattened.add(v)
            }
            val consolidated =
                new ju.ArrayList[AbstractConfigValue](flattened.size)
            import scala.collection.JavaConverters._
            for (v <- flattened.asScala) {
                if (consolidated.isEmpty) consolidated.add(v) else join(consolidated, v)
            }
            consolidated
        }
    def concatenate(
        pieces: ju.List[AbstractConfigValue]): AbstractConfigValue = {
        val consolidated = consolidate(pieces)
        if (consolidated.isEmpty) null
        else if (consolidated.size == 1) consolidated.get(0)
        else {
            val mergedOrigin =
                SimpleConfigOrigin.mergeOrigins(consolidated)
            new ConfigConcatenation(mergedOrigin, consolidated)
        }
    }
}

final class ConfigConcatenation(
    origin: ConfigOrigin,
    val pieces: ju.List[AbstractConfigValue]) extends AbstractConfigValue(origin)
    with Unmergeable
    with Container {

    if (pieces.size < 2)
        throw new ConfigException.BugOrBroken(
            "Created concatenation with less than 2 items: " + this)
    var hadUnmergeable = false
    import scala.collection.JavaConverters._
    for (p <- pieces.asScala) {
        if (p.isInstanceOf[ConfigConcatenation])
            throw new ConfigException.BugOrBroken(
                "ConfigConcatenation should never be nested: " + this)
        if (p.isInstanceOf[Unmergeable]) hadUnmergeable = true
    }
    if (!hadUnmergeable)
        throw new ConfigException.BugOrBroken(
            "Created concatenation without an unmergeable in it: " + this)

    private def notResolved = new ConfigException.NotResolved(
        "need to Config#resolve(), see the API docs for Config#resolve(); substitution not resolved: " + this)

    override def valueType = throw notResolved

    override def unwrapped = throw notResolved

    override def newCopy(newOrigin: ConfigOrigin) =
        new ConfigConcatenation(newOrigin, pieces)

    override def ignoresFallbacks: Boolean = {
        // we can never ignore fallbacks because if a child ConfigReference
        // is self-referential we have to look lower in the merge stack
        // for its value.
        false
    }

    override def unmergedValues: ju.Collection[ConfigConcatenation] =
        ju.Collections.singleton(this)

    @throws[AbstractConfigValue.NotPossibleToResolve]
    override def resolveSubstitutions(
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] = {

        import scala.collection.JavaConverters._

        if (ConfigImpl.traceSubstitutionsEnabled) {
            val indent = context.depth + 2
            ConfigImpl.trace(
                indent - 1,
                "concatenation has " + pieces.size + " pieces:")
            var count = 0
            for (v <- pieces.asScala) {
                ConfigImpl.trace(indent, count + ": " + v)
                count += 1
            }
        }
        // Right now there's no reason to pushParent here because the
        // content of ConfigConcatenation should not need to replaceChild,
        // but if it did we'd have to do this.
        val sourceWithParent = source // .pushParent(this)
        var newContext = context
        val resolved =
            new ju.ArrayList[AbstractConfigValue](pieces.size)
        for (p <- pieces.asScala) { // to concat into a string we have to do a full resolve,
            // so unrestrict the context, then put restriction back afterward
            val restriction = newContext.restrictToChild
            val result =
                newContext.unrestricted.resolve(p, sourceWithParent)
            val r = result.value
            newContext = result.context.restrict(restriction)
            if (ConfigImpl.traceSubstitutionsEnabled)
                ConfigImpl.trace(context.depth, "resolved concat piece to " + r)
            if (r == null) {
                // it was optional... omit
            } else resolved.add(r)
        }
        // now need to concat everything
        val joined =
            ConfigConcatenation.consolidate(resolved)
        // if unresolved is allowed we can just become another
        // ConfigConcatenation
        if (joined.size > 1 && context.options.getAllowUnresolved)
            ResolveResult.make(
                newContext,
                new ConfigConcatenation(this.origin, joined))
        else if (joined.isEmpty) { // we had just a list of optional references using ${?}
            ResolveResult.make(newContext, null)
        } else if (joined.size == 1) ResolveResult.make(newContext, joined.get(0))
        else
            throw new ConfigException.BugOrBroken(
                "Bug in the library; resolved list was joined to too many values: " + joined)
    }
    override def resolveStatus: ResolveStatus = ResolveStatus.UNRESOLVED
    override def replaceChild(
        child: AbstractConfigValue,
        replacement: AbstractConfigValue): ConfigConcatenation = {
        val newPieces = AbstractConfigValue.replaceChildInList(pieces, child, replacement)
        if (newPieces == null) null else new ConfigConcatenation(origin, newPieces)
    }
    override def hasDescendant(descendant: AbstractConfigValue): Boolean =
        AbstractConfigValue.hasDescendantInList(pieces, descendant)
    // when you graft a substitution into another object,
    // you have to prefix it with the location in that object
    // where you grafted it; but save prefixLength so
    // system property and env variable lookups don't get
    // broken.
    override def relativized(prefix: Path): ConfigConcatenation = {
        val newPieces =
            new ju.ArrayList[AbstractConfigValue]
        import scala.collection.JavaConverters._
        for (p <- pieces.asScala) {
            newPieces.add(p.relativized(prefix))
        }
        new ConfigConcatenation(origin, newPieces)
    }

    override def canEqual(other: Any): Boolean =
        other.isInstanceOf[ConfigConcatenation]

    override def equals(other: Any): Boolean = { // note that "origin" is deliberately NOT part of equality
        if (other.isInstanceOf[ConfigConcatenation])
            canEqual(other) && this.pieces == other
                .asInstanceOf[ConfigConcatenation]
                .pieces
        else false
    }

    override def hashCode: Int = pieces.hashCode

    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit = {
        import scala.collection.JavaConverters._
        for (p <- pieces.asScala) {
            p.render(sb, indent, atRoot, options)
        }
    }
}
