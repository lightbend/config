package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve

/**
 * This class is the source for values for a substitution like ${foo}.
 */
object ResolveSource {
    // as a side effect, findInObject() will have to resolve all parents of the
    // child being peeked, but NOT the child itself. Caller has to resolve
    // the child itself if needed. ValueWithPath.value can be null but
    // the ValueWithPath instance itself should not be.
    @throws[NotPossibleToResolve]
    private[impl] def findInObject(
        obj: AbstractConfigObject,
        context: ResolveContext,
        path: Path): ResultWithPath = {
        // resolve ONLY portions of the object which are along our path
        if (ConfigImpl.traceSubstitutionsEnabled)
            ConfigImpl.trace("*** finding '" + path + "' in " + obj)
        val restriction = context.restrictToChild
        val partiallyResolved =
            context.restrict(path).resolve(obj, new ResolveSource(obj))
        val newContext =
            partiallyResolved.context.restrict(restriction)
        if (partiallyResolved.value.isInstanceOf[AbstractConfigObject]) {
            val pair = findInObject(
                partiallyResolved.value.asInstanceOf[AbstractConfigObject],
                path)
            new ResultWithPath(
                ResolveResult.make(newContext, pair.value),
                pair.pathFromRoot)
        } else
            throw new ConfigException.BugOrBroken(
                "resolved object to non-object " + obj + " to " + partiallyResolved)
    }
    private[impl] def findInObject(
        obj: AbstractConfigObject,
        path: Path): ValueWithPath =
        try // we'll fail if anything along the path can't
            // be looked at without resolving.
            findInObject(obj, path, null)
        catch {
            case e: ConfigException.NotResolved =>
                throw ConfigImpl.improveNotResolved(path, e)
        }
    private def findInObject(
        obj: AbstractConfigObject,
        path: Path,
        parents: Node[Container]): ValueWithPath = {
        val key = path.first
        val next = path.remainder
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace("*** looking up '" + key + "' in " + obj)
        val v = obj.attemptPeekWithPartialResolve(key)
        val newParents = if (parents == null) new Node[Container](obj) else parents.prepend(obj)
        if (next == null) new ValueWithPath(v, newParents) else if (v.isInstanceOf[AbstractConfigObject])
            findInObject(
                v.asInstanceOf[AbstractConfigObject],
                next,
                newParents)
        else new ValueWithPath(null, newParents)
    }
    // returns null if the replacement results in deleting all the nodes.
    private def replace(
        list: ResolveSource.Node[Container],
        old: Container,
        replacement: AbstractConfigValue): Node[Container] = {
        val child: Container = list.head
        if (child ne old) throw new ConfigException.BugOrBroken(
            "Can only replace() the top node we're resolving; had " + child +
                " on top and tried to replace " + old + " overall list was " + list)
        val parent =
            if (list.tail == null) null else list.tail.head
        if (replacement == null || !replacement.isInstanceOf[Container]) if (parent == null) null
        else {
            // we are deleting the child from the stack of containers
            // because it's either going away or not a container
            val newParent =
                parent.replaceChild(old.asInstanceOf[AbstractConfigValue], null)
            replace(list.tail, parent, newParent)
        }
        else {
            /* we replaced the container with another container */
            if (parent == null) new ResolveSource.Node[Container](
                replacement.asInstanceOf[Container])
            else {
                val newParent = parent.replaceChild(
                    old.asInstanceOf[AbstractConfigValue],
                    replacement)
                val newTail =
                    replace(list.tail, parent, newParent)
                if (newTail != null) newTail.prepend(replacement.asInstanceOf[Container])
                else new ResolveSource.Node[Container](replacement.asInstanceOf[Container])
            }
        }
    }
    // a persistent list
    final private[impl] class Node[T] private[impl] (
        val value: T,
        val next: ResolveSource.Node[T]) {

        def this(value: T) = this(value, null)
        private[impl] def prepend(value: T) = new ResolveSource.Node[T](value, this)
        private[impl] def head: T = value
        private[impl] def tail: Node[T] = next
        private[impl] def last = {
            var i = this
            while ({ i.next != null }) i = i.next
            i.value
        }
        private[impl] def reverse: Node[T] = if (next == null) this
        else {
            var reversed = new ResolveSource.Node[T](value)
            var i = next
            while ({ i != null }) {
                reversed = reversed.prepend(i.value)
                i = i.next
            }
            reversed
        }
        override def toString: String = {
            val sb = new StringBuffer
            sb.append("[")
            var toAppendValue = this.reverse
            while ({ toAppendValue != null }) {
                sb.append(toAppendValue.value.toString)
                if (toAppendValue.next != null) sb.append(" <= ")
                toAppendValue = toAppendValue.next
            }
            sb.append("]")
            sb.toString
        }
    }
    // value is allowed to be null
    final private[impl] class ValueWithPath private[impl] (
        val value: AbstractConfigValue,
        val pathFromRoot: ResolveSource.Node[Container]) {
        override def toString: String = "ValueWithPath(value=" + value + ", pathFromRoot=" + pathFromRoot + ")"
    }
    final private[impl] class ResultWithPath private[impl] (
        val result: ResolveResult[_ <: AbstractConfigValue],
        val pathFromRoot: ResolveSource.Node[Container]) {
        override def toString: String = "ResultWithPath(result=" + result + ", pathFromRoot=" + pathFromRoot + ")"
    }
}

final class ResolveSource(
    val root: AbstractConfigObject,
    // This is used for knowing the chain of parents we used to get here.
    // null if we should assume we are not a descendant of the root.
    // the root itself should be a node in this if non-null.
    val pathFromRoot: ResolveSource.Node[Container]) {

    def this(root: AbstractConfigObject) = this(root, null)

    // if we replace the root with a non-object, use an empty
    // object with nothing in it instead.
    private def rootMustBeObj(value: Container) = if (value.isInstanceOf[AbstractConfigObject])
        value.asInstanceOf[AbstractConfigObject]
    else SimpleConfigObject.empty
    @throws[NotPossibleToResolve]
    private[impl] def lookupSubst(
        context: ResolveContext,
        subst: SubstitutionExpression,
        prefixLength: Int) = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(context.depth, "searching for " + subst)
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            context.depth,
            subst + " - looking up relative to file it occurred in")
        // First we look up the full path, which means relative to the
        // included file if we were not a root file
        var result =
            ResolveSource.findInObject(root, context, subst.path)
        if (result.result.value == null) {
            // Then we want to check relative to the root file. We don't
            // want the prefix we were included at to be used when looking
            // up env variables either.
            val unprefixed = subst.path.subPath(prefixLength)
            if (prefixLength > 0) {
                if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                    result.result.context.depth,
                    unprefixed + " - looking up relative to parent file")
                result =
                    ResolveSource.findInObject(root, result.result.context, unprefixed)
            }
            if (result.result.value == null && result.result.context.options.getUseSystemEnvironment) {
                if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
                    result.result.context.depth,
                    unprefixed + " - looking up in system environment")
                result = ResolveSource.findInObject(
                    ConfigImpl.envVariablesAsConfigObject,
                    context,
                    unprefixed)
            }
        }
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(result.result.context.depth, "resolved to " + result)
        result
    }
    private[impl] def pushParent(parent: Container) = {
        if (parent == null) throw new ConfigException.BugOrBroken("can't push null parent")
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            "pushing parent " + parent + " ==root " + (parent eq root) + " onto " + this)
        if (pathFromRoot == null) if (parent eq root)
            new ResolveSource(
                root,
                new ResolveSource.Node[Container](parent))
        else {
            if (ConfigImpl.traceSubstitutionsEnabled) {
                // this hasDescendant check is super-expensive so it's a
                // trace message rather than an assertion
                if (root.hasDescendant(parent.asInstanceOf[AbstractConfigValue]))
                    ConfigImpl.trace(
                        "***** BUG ***** tried to push parent " + parent + " without having a path to it in " + this)
            }
            // ignore parents if we aren't proceeding from the root
            this
        }
        else {
            val parentParent = pathFromRoot.head
            if (ConfigImpl.traceSubstitutionsEnabled) if (parentParent != null && !parentParent
                .hasDescendant(parent.asInstanceOf[AbstractConfigValue]))
                ConfigImpl.trace(
                    "***** BUG ***** trying to push non-child of " + parentParent + ", non-child was " + parent)
            new ResolveSource(root, pathFromRoot.prepend(parent))
        }
    }
    private[impl] def resetParents = if (pathFromRoot == null) this else new ResolveSource(root)
    private[impl] def replaceCurrentParent(
        old: Container,
        replacement: Container) = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            "replaceCurrentParent old " + old + "@" + System
                .identityHashCode(old) + " replacement " + replacement + "@" + System
                .identityHashCode(old) + " in " + this)
        if (old eq replacement) this
        else if (pathFromRoot != null) {
            val newPath = ResolveSource.replace(
                pathFromRoot,
                old,
                replacement.asInstanceOf[AbstractConfigValue])
            if (ConfigImpl.traceSubstitutionsEnabled) {
                ConfigImpl.trace(
                    "replaced " + old + " with " + replacement + " in " + this)
                ConfigImpl.trace("path was: " + pathFromRoot + " is now " + newPath)
            }
            // if we end up nuking the root object itself, we replace it with an empty root
            if (newPath != null)
                new ResolveSource(
                    newPath.last.asInstanceOf[AbstractConfigObject],
                    newPath)
            else new ResolveSource(SimpleConfigObject.empty)
        } else if (old eq root) new ResolveSource(rootMustBeObj(replacement))
        else {
            throw new ConfigException.BugOrBroken(
                "attempt to replace root " + root + " with " + replacement)
            // return this
        }
    }
    // replacement may be null to delete
    private[impl] def replaceWithinCurrentParent(
        old: AbstractConfigValue,
        replacement: AbstractConfigValue) = {
        if (ConfigImpl.traceSubstitutionsEnabled) ConfigImpl.trace(
            "replaceWithinCurrentParent old " + old + "@" + System
                .identityHashCode(old) + " replacement " + replacement + "@" + System
                .identityHashCode(old) + " in " + this)
        if (old eq replacement) this
        else if (pathFromRoot != null) {
            val parent = pathFromRoot.head
            val newParent =
                parent.replaceChild(old, replacement)
            replaceCurrentParent(
                parent,
                if (newParent.isInstanceOf[Container]) newParent.asInstanceOf[Container]
                else null)
        } else if ((old eq root) && replacement.isInstanceOf[Container])
            new ResolveSource(
                rootMustBeObj(replacement.asInstanceOf[Container]))
        else
            throw new ConfigException.BugOrBroken(
                "replace in parent not possible " + old + " with " + replacement + " in " + this)
    }
    override def toString: String = "ResolveSource(root=" + root + ", pathFromRoot=" + pathFromRoot + ")"
}
