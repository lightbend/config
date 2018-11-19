/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.impl.AbstractConfigValue._

/**
 * The issue here is that we want to first merge our stack of config files, and
 * then we want to evaluate substitutions. But if two substitutions both expand
 * to an object, we might need to merge those two objects. Thus, we can't ever
 * "override" a substitution when we do a merge; instead we have to save the
 * stack of values that should be merged, and resolve the merge when we evaluate
 * substitutions.
 */
object ConfigDelayedMerge {
    // static method also used by ConfigDelayedMergeObject
    @throws[AbstractConfigValue.NotPossibleToResolve]
    def resolveSubstitutions(
        replaceable: ReplaceableMergeStack,
        stack: ju.List[AbstractConfigValue],
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] = {
        if (ConfigImpl.traceSubstitutionsEnabled) {
            ConfigImpl.trace(
                context.depth,
                "delayed merge stack has " + stack.size + " items:")
            var count = 0
            for (v <- stack.asScala) {
                ConfigImpl.trace(context.depth + 1, count + ": " + v)
                count += 1
            }
        }
        // to resolve substitutions, we need to recursively resolve
        // the stack of stuff to merge, and merge the stack so
        // we won't be a delayed merge anymore. If restrictToChildOrNull
        // is non-null, or resolve options allow partial resolves,
        // we may remain a delayed merge though.
        var newContext = context
        var count = 0
        var merged: AbstractConfigValue = null
        for (end <- stack.asScala) { // the end value may or may not be resolved already
            var sourceForEnd: ResolveSource = null
            if (end.isInstanceOf[ReplaceableMergeStack])
                throw new ConfigException.BugOrBroken(
                    "A delayed merge should not contain another one: " + replaceable)
            else if (end.isInstanceOf[Unmergeable]) { // the remainder could be any kind of value, including another
                // ConfigDelayedMerge
                val remainder =
                    replaceable.makeReplacement(context, count + 1)
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(
                        newContext.depth,
                        "remainder portion: " + remainder)
                // If, while resolving 'end' we come back to the same
                // merge stack, we only want to look _below_ 'end'
                // in the stack. So we arrange to replace the
                // ConfigDelayedMerge with a value that is only
                // the remainder of the stack below this one.
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(newContext.depth, "building sourceForEnd")
                // we resetParents() here because we'll be resolving "end"
                // against a root which does NOT contain "end"
                sourceForEnd = source.replaceWithinCurrentParent(
                    replaceable.asInstanceOf[AbstractConfigValue],
                    remainder)
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(
                        newContext.depth,
                        "  sourceForEnd before reset parents but after replace: " + sourceForEnd)
                sourceForEnd = sourceForEnd.resetParents
            } else {
                if (ConfigImpl.traceSubstitutionsEnabled)
                    ConfigImpl.trace(
                        newContext.depth,
                        "will resolve end against the original source with parent pushed")
                sourceForEnd = source.pushParent(replaceable)
            }
            if (ConfigImpl.traceSubstitutionsEnabled)
                ConfigImpl.trace(newContext.depth, "sourceForEnd      =" + sourceForEnd)
            if (ConfigImpl.traceSubstitutionsEnabled)
                ConfigImpl.trace(
                    newContext.depth,
                    "Resolving highest-priority item in delayed merge " + end
                        + " against " + sourceForEnd + " endWasRemoved=" + (source != sourceForEnd))
            val result =
                newContext.resolve(end, sourceForEnd)
            val resolvedEnd = result.value
            newContext = result.context
            if (resolvedEnd != null)
                if (merged == null) merged = resolvedEnd
                else {
                    if (ConfigImpl.traceSubstitutionsEnabled)
                        ConfigImpl.trace(
                            newContext.depth + 1,
                            "merging " + merged + " with fallback " + resolvedEnd)
                    merged = merged.withFallback(resolvedEnd)
                }
            count += 1
            if (ConfigImpl.traceSubstitutionsEnabled)
                ConfigImpl.trace(newContext.depth, "stack merged, yielding: " + merged)
        }
        ResolveResult.make(newContext, merged)
    }
    // static method also used by ConfigDelayedMergeObject; end may be null
    def makeReplacement(
        context: ResolveContext,
        stack: ju.List[AbstractConfigValue],
        skipping: Int): AbstractConfigValue = {
        val subStack =
            stack.subList(skipping, stack.size)
        if (subStack.isEmpty) {
            if (ConfigImpl.traceSubstitutionsEnabled)
                ConfigImpl.trace(
                    context.depth,
                    "Nothing else in the merge stack, replacing with null")
            null
        } else { // generate a new merge stack from only the remaining items
            var merged: AbstractConfigValue = null
            for (v <- subStack.asScala) {
                if (merged == null) merged = v else merged = merged.withFallback(v)
            }
            merged
        }
    }
    // static utility shared with ConfigDelayedMergeObject
    def stackIgnoresFallbacks(
        stack: ju.List[AbstractConfigValue]): Boolean = {
        val last = stack.get(stack.size - 1)
        last.ignoresFallbacks
    }
    // static method also used by ConfigDelayedMergeObject.
    def render(
        stack: ju.List[AbstractConfigValue],
        sb: jl.StringBuilder,
        indentVal: Int,
        atRoot: Boolean,
        atKey: String,
        options: ConfigRenderOptions): Unit = {
        val commentMerge = options.getComments
        if (commentMerge) {
            sb.append("# unresolved merge of " + stack.size + " values follows (\n")
            if (atKey == null) {
                indent(sb, indentVal, options)
                sb.append(
                    "# this unresolved merge will not be parseable because it's at the root of the object\n")
                indent(sb, indentVal, options)
                sb.append(
                    "# the HOCON format has no way to list multiple root objects in a single file\n")
            }
        }
        val reversed = new ju.ArrayList[AbstractConfigValue]
        reversed.addAll(stack)
        ju.Collections.reverse(reversed)
        var i = 0
        for (v <- reversed.asScala) {
            if (commentMerge) {
                indent(sb, indentVal, options)
                if (atKey != null)
                    sb.append(
                        "#     unmerged value " + i + " for key " + ConfigImplUtil
                            .renderJsonString(atKey) + " from ")
                else sb.append("#     unmerged value " + i + " from ")
                i += 1
                sb.append(v.origin.description)
                sb.append("\n")
                for (comment <- v.origin.comments.asScala) {
                    indent(sb, indentVal, options)
                    sb.append("# ")
                    sb.append(comment)
                    sb.append("\n")
                }
            }
            indent(sb, indentVal, options)
            if (atKey != null) {
                sb.append(ConfigImplUtil.renderJsonString(atKey))
                if (options.getFormatted) sb.append(" : ") else sb.append(":")
            }
            v.render(sb, indentVal, atRoot, options)
            sb.append(",")
            if (options.getFormatted) sb.append('\n')
        }
        // chop comma or newline
        sb.setLength(sb.length - 1)
        if (options.getFormatted) {
            sb.setLength(sb.length - 1) // also chop comma
            sb.append("\n") // put a newline back
        }
        if (commentMerge) {
            indent(sb, indentVal, options)
            sb.append("# ) end of unresolved merge\n")
        }
    }
}

final class ConfigDelayedMerge(
    origin: ConfigOrigin, // earlier items in the stack win
    val stack: ju.List[AbstractConfigValue]) extends AbstractConfigValue(origin)
    with Unmergeable
    with ReplaceableMergeStack {

    if (stack.isEmpty)
        throw new ConfigException.BugOrBroken("creating empty delayed merge value")

    for (v <- stack.asScala) {
        if (v.isInstanceOf[ConfigDelayedMerge] || v
            .isInstanceOf[ConfigDelayedMergeObject])
            throw new ConfigException.BugOrBroken(
                "placed nested DelayedMerge in a ConfigDelayedMerge, should have consolidated stack")
    }

    override def valueType = throw new ConfigException.NotResolved(
        "called valueType() on value with unresolved substitutions, need to Config#resolve() first, see API docs")

    override def unwrapped = throw new ConfigException.NotResolved(
        "called unwrapped() on value with unresolved substitutions, need to Config#resolve() first, see API docs")

    @throws[AbstractConfigValue.NotPossibleToResolve]
    override def resolveSubstitutions(
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] =
        ConfigDelayedMerge.resolveSubstitutions(this, stack, context, source)

    override def makeReplacement(
        context: ResolveContext,
        skipping: Int): AbstractConfigValue =
        ConfigDelayedMerge.makeReplacement(context, stack, skipping)

    override def resolveStatus: ResolveStatus = ResolveStatus.UNRESOLVED

    override def replaceChild(
        child: AbstractConfigValue,
        replacement: AbstractConfigValue): AbstractConfigValue = {
        val newStack =
            AbstractConfigValue.replaceChildInList(stack, child, replacement)
        if (newStack == null) null else new ConfigDelayedMerge(origin, newStack)
    }
    override def hasDescendant(descendant: AbstractConfigValue): Boolean =
        AbstractConfigValue.hasDescendantInList(stack, descendant)

    override def relativized(prefix: Path): ConfigDelayedMerge = {
        val newStack = new ju.ArrayList[AbstractConfigValue]
        for (o <- stack.asScala) {
            newStack.add(o.relativized(prefix))
        }
        new ConfigDelayedMerge(origin, newStack)
    }

    override def ignoresFallbacks: Boolean =
        ConfigDelayedMerge.stackIgnoresFallbacks(stack)

    override def newCopy(newOrigin: ConfigOrigin) =
        new ConfigDelayedMerge(newOrigin, stack)

    override final def mergedWithTheUnmergeable(
        fallback: Unmergeable): ConfigDelayedMerge =
        mergedWithTheUnmergeable(stack, fallback)
            .asInstanceOf[ConfigDelayedMerge]

    override final def mergedWithObject(
        fallback: AbstractConfigObject): ConfigDelayedMerge =
        mergedWithObject(stack, fallback).asInstanceOf[ConfigDelayedMerge]

    override def mergedWithNonObject(
        fallback: AbstractConfigValue): ConfigDelayedMerge =
        mergedWithNonObject(stack, fallback).asInstanceOf[ConfigDelayedMerge]

    override def unmergedValues: ju.Collection[AbstractConfigValue] = stack

    override def canEqual(other: Any): Boolean =
        other.isInstanceOf[ConfigDelayedMerge]

    override def equals(other: Any): Boolean = {
        // note that "origin" is deliberately NOT part of equality
        if (other.isInstanceOf[ConfigDelayedMerge])
            canEqual(other) && ((this.stack eq other
                .asInstanceOf[ConfigDelayedMerge]
                .stack) || this.stack == other.asInstanceOf[ConfigDelayedMerge].stack)
        else false
    }

    override def hashCode: Int = stack.hashCode

    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        atKey: String,
        options: ConfigRenderOptions): Unit = {
        ConfigDelayedMerge.render(stack, sb, indent, atRoot, atKey, options)
    }

    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit = {
        render(sb, indent, atRoot, null, options)
    }
}
