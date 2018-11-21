package com.typesafe.config.impl

import java.{ util => ju }
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve

object ResolveContext {

    private def newCycleMarkers: ju.Set[AbstractConfigValue] =
        ju.Collections.newSetFromMap(new ju.IdentityHashMap)

    def resolve(
        value: AbstractConfigValue,
        root: AbstractConfigObject,
        options: ConfigResolveOptions): AbstractConfigValue = {
        val source = new ResolveSource(root)
        val context =
            new ResolveContext(options, null /* restrictToChild */ )
        try context.resolve(value, source).value
        catch {
            case e: AbstractConfigValue.NotPossibleToResolve =>
                // ConfigReference was supposed to catch NotPossibleToResolve
                throw new ConfigException.BugOrBroken(
                    "NotPossibleToResolve was thrown from an outermost resolve",
                    e)
        }
    }
}

private[impl] final class ResolveContext(
    val memos: ResolveMemos,
    val options: ConfigResolveOptions,
    // the current path restriction, used to ensure lazy
    // resolution and avoid gratuitous cycles. without this,
    // any sibling of an object we're traversing could
    // cause a cycle "by side effect"
    // CAN BE NULL for a full resolve.
    val restrictToChild: Path,
    _resolveStack: ju.List[AbstractConfigValue],
    _cycleMarkers: ju.Set[AbstractConfigValue]) {

    // This is used for tracing and debugging and nice error messages;
    // contains every node as we call resolve on it.
    val resolveStack = ju.Collections.unmodifiableList(_resolveStack)
    val cycleMarkers = ju.Collections.unmodifiableSet(_cycleMarkers)

    def this(
        options: ConfigResolveOptions,
        restrictToChild: Path) {
        // LinkedHashSet keeps the traversal order which is at least useful
        // in error messages if nothing else
        this(
            new ResolveMemos,
            options,
            restrictToChild,
            new ju.ArrayList[AbstractConfigValue],
            ResolveContext.newCycleMarkers)
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            depth,
            "ResolveContext restrict to child " + restrictToChild)
    }
    private[impl] def addCycleMarker(
        value: AbstractConfigValue): ResolveContext = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            depth,
            "++ Cycle marker " + value + "@" + System.identityHashCode(value))
        if (cycleMarkers.contains(value)) throw new ConfigException.BugOrBroken("Added cycle marker twice " + value)
        val copy = ResolveContext.newCycleMarkers
        copy.addAll(cycleMarkers)
        copy.add(value)
        new ResolveContext(
            memos,
            options,
            restrictToChild,
            resolveStack,
            copy)
    }
    private[impl] def removeCycleMarker(
        value: AbstractConfigValue): ResolveContext = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            depth,
            "-- Cycle marker " + value + "@" + System.identityHashCode(value))
        val copy = ResolveContext.newCycleMarkers
        copy.addAll(cycleMarkers)
        copy.remove(value)
        new ResolveContext(
            memos,
            options,
            restrictToChild,
            resolveStack,
            copy)
    }
    private def memoize(
        key: MemoKey,
        value: AbstractConfigValue): ResolveContext = {
        val changed = memos.put(key, value)
        new ResolveContext(
            changed,
            options,
            restrictToChild,
            resolveStack,
            cycleMarkers)
    }

    private[impl] def isRestrictedToChild: Boolean = restrictToChild != null

    // restrictTo may be null to unrestrict
    private[impl] def restrict(restrictTo: Path): ResolveContext = if (restrictTo eq restrictToChild) this
    else
        new ResolveContext(
            memos,
            options,
            restrictTo,
            resolveStack,
            cycleMarkers)
    private[impl] def unrestricted: ResolveContext = restrict(null)
    private[impl] def traceString: String = {
        val separator = ", "
        val sb = new StringBuilder
        import scala.collection.JavaConversions._
        for (value <- resolveStack) {
            if (value.isInstanceOf[ConfigReference]) {
                sb.append(value.asInstanceOf[ConfigReference].expression.toString)
                sb.append(separator)
            }
        }
        if (sb.length > 0) sb.setLength(sb.length - separator.length)
        sb.toString
    }
    private def pushTrace(value: AbstractConfigValue): ResolveContext = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(depth, "pushing trace " + value)
        val copy =
            new ju.ArrayList[AbstractConfigValue](resolveStack)
        copy.add(value)
        new ResolveContext(
            memos,
            options,
            restrictToChild,
            copy,
            cycleMarkers)
    }
    private[impl] def popTrace: ResolveContext = {
        val copy =
            new ju.ArrayList[AbstractConfigValue](resolveStack)
        val old = copy.remove(resolveStack.size - 1)
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(depth - 1, "popped trace " + old)
        new ResolveContext(
            memos,
            options,
            restrictToChild,
            copy,
            cycleMarkers)
    }
    private[impl] def depth: Int = {
        if (resolveStack.size > 30) throw new ConfigException.BugOrBroken("resolve getting too deep")
        resolveStack.size
    }
    @throws[NotPossibleToResolve]
    private[impl] def resolve(
        original: AbstractConfigValue,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            depth,
            "resolving " + original + " restrictToChild=" + restrictToChild + " in " + source)
        pushTrace(original).realResolve(original, source).popTrace
    }
    @throws[NotPossibleToResolve]
    private def realResolve(
        original: AbstractConfigValue,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigValue] = {
        // a fully-resolved (no restrictToChild) object can satisfy a
        // request for a restricted object, so always check that first.
        val fullKey = new MemoKey(original, null)
        var restrictedKey: MemoKey = null
        var cached = memos.get(fullKey)
        // but if there was no fully-resolved object cached, we'll only
        // compute the restrictToChild object so use a more limited
        // memo key
        if (cached == null && isRestrictedToChild) {
            restrictedKey = new MemoKey(original, restrictToChild)
            cached = memos.get(restrictedKey)
        }
        if (cached != null) {
            if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                depth,
                "using cached resolution " + cached + " for " + original + " restrictToChild " + restrictToChild)
            ResolveResult.make(this, cached)
        } else {
            if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                depth,
                "not found in cache, resolving " + original + "@" + System
                    .identityHashCode(original))
            if (cycleMarkers.contains(original)) {
                if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                    depth,
                    "Cycle detected, can't resolve; " + original + "@" + System
                        .identityHashCode(original))
                throw new AbstractConfigValue.NotPossibleToResolve(this)
            }
            val result =
                original.resolveSubstitutions(this, source)
            val resolved = result.value
            if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                depth,
                "resolved to " + resolved + "@" + System
                    .identityHashCode(resolved) + " from " + original + "@" + System
                    .identityHashCode(resolved))
            var withMemo = result.context
            if (resolved == null || (resolved.resolveStatus eq ResolveStatus.RESOLVED)) { // if the resolved object is fully resolved by resolving
                // only the restrictToChildOrNull, then it can be cached
                // under fullKey since the child we were restricted to
                // turned out to be the only unresolved thing.
                if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(depth, "caching " + fullKey + " result " + resolved)
                withMemo = withMemo.memoize(fullKey, resolved)
            } else { // if we have an unresolved object then either we did a
                // partial resolve restricted to a certain child, or we are
                // allowing incomplete resolution, or it's a bug.
                if (isRestrictedToChild) {
                    if (restrictedKey == null) throw new ConfigException.BugOrBroken(
                        "restrictedKey should not be null here")
                    if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                        depth,
                        "caching " + restrictedKey + " result " + resolved)
                    withMemo = withMemo.memoize(restrictedKey, resolved)
                } else if (options.getAllowUnresolved) {
                    if (ConfigImpl.traceSubstitutionsEnabled)
                        ConfigImpl.trace(
                            depth,
                            "caching " + fullKey + " result " + resolved)
                    withMemo = withMemo.memoize(fullKey, resolved)
                } else
                    throw new ConfigException.BugOrBroken(
                        "resolveSubstitutions() did not give us a resolved object")
            }
            ResolveResult.make(withMemo, resolved)
        }
    }
}
