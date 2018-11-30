/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.io.ObjectStreamException
import java.io.Serializable
import java.{ util => ju }
import scala.collection.JavaConverters._
import scala.util.control.Breaks._
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve

@SerialVersionUID(2L)
object SimpleConfigObject {

    final private class ResolveModifier private[impl] (var context: ResolveContext, val source: ResolveSource)
        extends AbstractConfigValue.Modifier {

        final private[impl] var originalRestrict = context.restrictToChild

        @throws[NotPossibleToResolve]
        override def modifyChildMayThrow(key: String, v: AbstractConfigValue): AbstractConfigValue =
            if (context.isRestrictedToChild)
                if (key == context.restrictToChild.first) {
                    val remainder = context.restrictToChild.remainder
                    if (remainder != null) {
                        val result = context.restrict(remainder).resolve(v, source)
                        context = result.context.unrestricted.restrict(originalRestrict)
                        result.value
                    } else { // we don't want to resolve the leaf child.
                        v
                    }
                } else { // not in the restrictToChild path
                    v
                }
            else { // no restrictToChild, resolve everything
                val result = context.unrestricted.resolve(v, source)
                context = result.context.unrestricted.restrict(originalRestrict)
                result.value
            }
    }
    // this is only Serializable to chill out a findbugs warning
    @SerialVersionUID(1L)
    private object RenderComparator {
        private def isAllDigits(s: String): Boolean = {
            val length = s.length
            // empty string doesn't count as a number
            if (length == 0) return false
            var i = 0
            var allDigits = true
            while (i < length) {
                breakable {
                    val c = s.charAt(i)
                    if (Character.isDigit(c)) {
                        break // continue
                    } else {
                        allDigits = false
                        break // continue to end loop
                    }
                }
                if (allDigits) i += 1
                else i = length // exit loop
            }
            allDigits
        }
    }

    @SerialVersionUID(1L)
    final private class RenderComparator extends ju.Comparator[String] with Serializable {
        // This is supposed to sort numbers before strings,
        // and sort the numbers numerically. The point is
        // to make objects which are really list-like
        // (numeric indices) appear in order.
        override def compare(a: String, b: String): Int = {
            val aDigits = RenderComparator.isAllDigits(a)
            val bDigits = RenderComparator.isAllDigits(b)
            if (aDigits && bDigits) Integer.compare(a.toInt, b.toInt)
            else if (aDigits) -1
            else if (bDigits) 1
            else a.compareTo(b)
        }
    }

    private def mapEquals(a: ju.Map[String, ConfigValue], b: ju.Map[String, ConfigValue]): Boolean = {
        if (a eq b) return true
        val aKeys = a.keySet
        val bKeys = b.keySet
        if (!(aKeys == bKeys)) return false

        for (key <- aKeys.asScala) {
            if (!(a.get(key) == b.get(key))) return false
        }
        true
    }
    private def mapHash(m: ju.Map[String, ConfigValue]) = {
        // the keys have to be sorted, otherwise we could be equal
        // to another map but have a different hashcode.
        val keys = new ju.ArrayList[String]
        keys.addAll(m.keySet)
        ju.Collections.sort(keys)
        var valuesHash = 0
        for (k <- keys.asScala) {
            valuesHash += m.get(k).hashCode
        }
        41 * (41 + keys.hashCode) + valuesHash
    }
    private val EMPTY_NAME = "empty config"
    private val emptyInstance = empty(SimpleConfigOrigin.newSimple(EMPTY_NAME))
    // commented out temporarily, used in Java
    private[impl] def empty: SimpleConfigObject = emptyInstance
    private[impl] def empty(origin: ConfigOrigin): SimpleConfigObject =
        if (origin == null) empty
        else new SimpleConfigObject(origin, ju.Collections.emptyMap[String, AbstractConfigValue])
    private[impl] def emptyMissing(baseOrigin: ConfigOrigin) =
        new SimpleConfigObject(SimpleConfigOrigin.newSimple(baseOrigin.description + " (not found)"),
            ju.Collections.emptyMap[String, AbstractConfigValue])
}

@SerialVersionUID(2L)
final class SimpleConfigObject(
    origin: ConfigOrigin,
    // this map should never be modified - assume immutable
    val value: ju.Map[String, AbstractConfigValue],
    val status: ResolveStatus,
    override val ignoresFallbacks: Boolean)
    extends AbstractConfigObject(origin) with Serializable {

    if (value == null) throw new ConfigException.BugOrBroken("creating config object with null map")
    final private var resolved = status eq ResolveStatus.RESOLVED
    // Kind of an expensive debug check. Comment out?
    if (status ne ResolveStatus.fromValues(value.values))
        throw new ConfigException.BugOrBroken("Wrong resolved status on " + this)

    def this(origin: ConfigOrigin, value: ju.Map[String, AbstractConfigValue]) =
        this(origin, value, ResolveStatus.fromValues(value.values), false /* ignoresFallbacks */ )

    override def withOnlyKey(key: String): SimpleConfigObject =
        withOnlyPath(Path.newKey(key))
    override def withoutKey(key: String): SimpleConfigObject =
        withoutPath(Path.newKey(key))
    // gets the object with only the path if the path
    // exists, otherwise null if it doesn't. this ensures
    // that if we have { a : { b : 42 } } and do
    // withOnlyPath("a.b.c") that we don't keep an empty
    // "a" object.
    override def withOnlyPathOrNull(path: Path): SimpleConfigObject = {
        val key = path.first
        val next = path.remainder
        var v: AbstractConfigValue = value.get(key)
        if (next != null) {
            if (v != null && v.isInstanceOf[AbstractConfigObject]) {
                v = v.asInstanceOf[AbstractConfigObject].withOnlyPathOrNull(next)
            } else {
                // if the path has more elements but we don't have an object,
                // then the rest of the path does not exist.
                v = null
            }
        }
        if (v == null) null
        else
            new SimpleConfigObject(origin, ju.Collections.singletonMap(key, v), v.resolveStatus, ignoresFallbacks)
    }
    override def withOnlyPath(path: Path): SimpleConfigObject = {
        val o = withOnlyPathOrNull(path)
        if (o == null)
            new SimpleConfigObject(origin, ju.Collections.emptyMap[String, AbstractConfigValue], ResolveStatus.RESOLVED, ignoresFallbacks)
        else o
    }
    override def withoutPath(path: Path): SimpleConfigObject = {
        val key = path.first
        val next = path.remainder
        var v = value.get(key)
        if (v != null && next != null && v.isInstanceOf[AbstractConfigObject]) {
            v = v.asInstanceOf[AbstractConfigObject].withoutPath(next)
            val updated = new ju.HashMap[String, AbstractConfigValue](value)
            updated.put(key, v)
            new SimpleConfigObject(origin, updated, ResolveStatus.fromValues(updated.values), ignoresFallbacks)
        } else if (next != null || v == null) { // can't descend, nothing to remove
            this
        } else {
            val smaller = new ju.HashMap[String, AbstractConfigValue](value.size - 1)
            for (old <- value.entrySet.asScala) {
                if (!(old.getKey == key)) smaller.put(old.getKey, old.getValue)
            }
            new SimpleConfigObject(origin, smaller, ResolveStatus.fromValues(smaller.values), ignoresFallbacks)
        }
    }
    override def withValue(key: String, v: ConfigValue): SimpleConfigObject = {
        if (v == null)
            throw new ConfigException.BugOrBroken("Trying to store null ConfigValue in a ConfigObject")
        var newMap: ju.Map[String, AbstractConfigValue] = null
        if (value.isEmpty)
            newMap = ju.Collections.singletonMap(key, v.asInstanceOf[AbstractConfigValue])
        else {
            newMap = new ju.HashMap[String, AbstractConfigValue](value)
            newMap.put(key, v.asInstanceOf[AbstractConfigValue])
        }
        new SimpleConfigObject(origin, newMap, ResolveStatus.fromValues(newMap.values), ignoresFallbacks)
    }
    override def withValue(path: Path, v: ConfigValue): SimpleConfigObject = {
        val key = path.first
        val next = path.remainder
        if (next == null) withValue(key, v)
        else {
            val child = value.get(key)
            if (child != null && child.isInstanceOf[AbstractConfigObject]) { // if we have an object, add to it
                withValue(key, child.asInstanceOf[AbstractConfigObject].withValue(next, v))
            } else { // as soon as we have a non-object, replace it entirely
                val subtree = v.asInstanceOf[AbstractConfigValue].atPath(SimpleConfigOrigin.newSimple(
                    "withValue(" + next.render + ")"),
                    next)
                withValue(key, subtree.root)
            }
        }
    }
    override def attemptPeekWithPartialResolve(key: String): AbstractConfigValue = value.get(key)
    private def newCopy(newStatus: ResolveStatus, newOrigin: ConfigOrigin, newIgnoresFallbacks: Boolean) =
        new SimpleConfigObject(newOrigin, value, newStatus, newIgnoresFallbacks)
    override def newCopy(newStatus: ResolveStatus, newOrigin: ConfigOrigin): SimpleConfigObject =
        newCopy(newStatus, newOrigin, ignoresFallbacks)
    override def withFallbacksIgnored: SimpleConfigObject =
        if (ignoresFallbacks) this
        else newCopy(resolveStatus, origin, true)
    override def resolveStatus: ResolveStatus = ResolveStatus.fromBoolean(resolved)
    override def replaceChild(child: AbstractConfigValue, replacement: AbstractConfigValue): SimpleConfigObject = {
        val newChildren = new ju.HashMap[String, AbstractConfigValue](value)
        for (old <- newChildren.entrySet.asScala) {
            if (old.getValue eq child) {
                if (replacement != null) old.setValue(replacement)
                else newChildren.remove(old.getKey)
                return new SimpleConfigObject(origin, newChildren, ResolveStatus.fromValues(newChildren.values), ignoresFallbacks)
            }
        }
        throw new ConfigException.BugOrBroken("SimpleConfigObject.replaceChild did not find " + child + " in " + this)
    }
    override def hasDescendant(descendant: AbstractConfigValue): Boolean = {
        for (child <- value.values.asScala) {
            if (child eq descendant) return true
        }
        // now do the expensive search
        for (child <- value.values.asScala) {
            if (child.isInstanceOf[Container] && child.asInstanceOf[Container].hasDescendant(descendant)) return true
        }
        false
    }

    override def unwrapped: ju.Map[String, AnyRef] = {
        val m = new ju.HashMap[String, AnyRef]

        for (e <- value.entrySet.asScala) {
            m.put(e.getKey, e.getValue.unwrapped)
        }
        m
    }
    override def mergedWithObject(abstractFallback: AbstractConfigObject): SimpleConfigObject = {
        requireNotIgnoringFallbacks()
        if (!abstractFallback.isInstanceOf[SimpleConfigObject])
            throw new ConfigException.BugOrBroken("should not be reached (merging non-SimpleConfigObject)")
        val fallback = abstractFallback.asInstanceOf[SimpleConfigObject]
        var changed = false
        var allResolved = true
        val merged = new ju.HashMap[String, AbstractConfigValue]
        val allKeys = new ju.HashSet[String]
        allKeys.addAll(this.keySet)
        allKeys.addAll(fallback.keySet)

        for (key <- allKeys.asScala) {
            val first = this.value.get(key)
            val second = fallback.value.get(key)
            var kept: AbstractConfigValue = null
            if (first == null) kept = second
            else if (second == null) kept = first
            else kept = first.withFallback(second)
            merged.put(key, kept)
            if (first ne kept) changed = true
            if (kept.resolveStatus eq ResolveStatus.UNRESOLVED) allResolved = false
        }
        val newResolveStatus = ResolveStatus.fromBoolean(allResolved)
        val newIgnoresFallbacks = fallback.ignoresFallbacks
        if (changed)
            new SimpleConfigObject(AbstractConfigObject.mergeOrigins(this, fallback), merged, newResolveStatus, newIgnoresFallbacks)
        else if ((newResolveStatus ne resolveStatus) || newIgnoresFallbacks != ignoresFallbacks)
            newCopy(newResolveStatus, origin, newIgnoresFallbacks)
        else this
    }
    private def modify(modifier: AbstractConfigValue.NoExceptionsModifier) =
        try modifyMayThrow(modifier)
        catch {
            case e: RuntimeException =>
                throw e
            case e: Exception =>
                throw new ConfigException.BugOrBroken("unexpected checked exception", e)
        }
    @throws[Exception]
    private def modifyMayThrow(modifier: AbstractConfigValue.Modifier) = {
        var changes: ju.Map[String, AbstractConfigValue] = null
        for (k <- keySet.asScala) {
            val v = value.get(k)
            // "modified" may be null, which means remove the child;
            // to do that we put null in the "changes" map.
            val modified = modifier.modifyChildMayThrow(k, v)
            if (modified ne v) {
                if (changes == null) changes = new ju.HashMap[String, AbstractConfigValue]
                changes.put(k, modified)
            }
        }
        if (changes == null) this
        else {
            val modified = new ju.HashMap[String, AbstractConfigValue]
            var sawUnresolved = false
            for (k <- keySet.asScala) {
                if (changes.containsKey(k)) {
                    val newValue = changes.get(k)
                    if (newValue != null) {
                        modified.put(k, newValue)
                        if (newValue.resolveStatus eq ResolveStatus.UNRESOLVED) sawUnresolved = true
                    } else {
                        //remove this child; don't put it in the new map.
                    }
                } else {
                    val newValue = value.get(k)
                    modified.put(k, newValue)
                    if (newValue.resolveStatus eq ResolveStatus.UNRESOLVED) sawUnresolved = true
                }
            }
            new SimpleConfigObject(origin, modified, if (sawUnresolved) ResolveStatus.UNRESOLVED
            else ResolveStatus.RESOLVED, ignoresFallbacks)
        }
    }
    @throws[NotPossibleToResolve]
    override def resolveSubstitutions(context: ResolveContext, source: ResolveSource): ResolveResult[_ <: AbstractConfigObject] = {
        if (resolveStatus eq ResolveStatus.RESOLVED) return ResolveResult.make(context, this)
        val sourceWithParent = source.pushParent(this)
        try {
            val modifier = new SimpleConfigObject.ResolveModifier(context, sourceWithParent)
            val value = modifyMayThrow(modifier)
            ResolveResult.make(modifier.context, value).asObjectResult
        } catch {
            case e: AbstractConfigValue.NotPossibleToResolve =>
                throw e
            case e: RuntimeException =>
                throw e
            case e: Exception =>
                throw new ConfigException.BugOrBroken("unexpected checked exception", e)
        }
    }
    override def relativized(prefix: Path): SimpleConfigObject = modify(
        new AbstractConfigValue.NoExceptionsModifier() {
            override def modifyChild(key: String, v: AbstractConfigValue): AbstractConfigValue = v.relativized(prefix)
        })
    override def render(sb: jl.StringBuilder, indentVal: Int, atRoot: Boolean, options: ConfigRenderOptions): Unit = {
        if (isEmpty) sb.append("{}")
        else {
            val outerBraces = options.getJson || !atRoot
            var innerIndent = 0
            if (outerBraces) {
                innerIndent = indentVal + 1
                sb.append("{")
                if (options.getFormatted) sb.append('\n')
            } else innerIndent = indentVal
            var separatorCount = 0
            val keys = new ju.ArrayList[String]
            keys.addAll(keySet)
            ju.Collections.sort(keys, new SimpleConfigObject.RenderComparator)
            //            val keys: Array[String] = keySet.toArray(new Array[String](size))
            //            ju.Arrays.sort(keys, new SimpleConfigObject.RenderComparator)
            for (k <- keys.asScala) {
                var v: AbstractConfigValue = null
                v = value.get(k)
                if (options.getOriginComments) {
                    val lines = v.origin.description.split("\n")
                    for (l <- lines) {
                        AbstractConfigValue.indent(sb, indentVal + 1, options)
                        sb.append('#')
                        if (!l.isEmpty) sb.append(' ')
                        sb.append(l)
                        sb.append("\n")
                    }
                }
                if (options.getComments) {
                    for (comment <- v.origin.comments.asScala) {
                        AbstractConfigValue.indent(sb, innerIndent, options)
                        sb.append("#")
                        if (!comment.startsWith(" ")) sb.append(' ')
                        sb.append(comment)
                        sb.append("\n")
                    }
                }
                AbstractConfigValue.indent(sb, innerIndent, options)
                v.render(sb, innerIndent, false /* atRoot */ , k, options)
                if (options.getFormatted) {
                    if (options.getJson) {
                        sb.append(",")
                        separatorCount = 2
                    } else separatorCount = 1
                    sb.append('\n')
                } else {
                    sb.append(",")
                    separatorCount = 1
                }
            }
            // chop last commas/newlines
            sb.setLength(sb.length - separatorCount)
            if (outerBraces) {
                if (options.getFormatted) {
                    sb.append('\n') // put a newline back

                    if (outerBraces) AbstractConfigValue.indent(sb, indentVal, options)
                }
                sb.append("}")
            }
        }
        if (atRoot && options.getFormatted) sb.append('\n')
    }
    override def get(key: Any): AbstractConfigValue = value.get(key)
    override def canEqual(other: Any): Boolean = other.isInstanceOf[ConfigObject]
    override def equals(other: Any): Boolean = {
        // note that "origin" is deliberately NOT part of equality.
        // neither are other "extras" like ignoresFallbacks or resolve status.
        if (other.isInstanceOf[ConfigObject]) {
            // optimization to avoid unwrapped() for two ConfigObject,
            // which is what AbstractConfigValue does.
            canEqual(other) && SimpleConfigObject.mapEquals(this, other.asInstanceOf[ConfigObject])
        } else false
    }
    override def hashCode: Int = { // note that "origin" is deliberately NOT part of equality
        SimpleConfigObject.mapHash(this)
    }
    override def containsKey(key: Any): Boolean = value.containsKey(key)
    override def keySet: ju.Set[String] = value.keySet
    override def containsValue(v: Any): Boolean = value.containsValue(v)
    override def entrySet: ju.Set[ju.Map.Entry[String, ConfigValue]] = {
        // total bloat just to work around lack of type variance
        val entries = new ju.HashSet[ju.Map.Entry[String, ConfigValue]]
        import scala.collection.JavaConversions._
        for (e <- value.entrySet) {
            entries.add(new ju.AbstractMap.SimpleImmutableEntry[String, ConfigValue](e.getKey, e.getValue))
        }
        entries
    }
    override def isEmpty: Boolean = value.isEmpty
    override def size: Int = value.size
    override def values = new ju.HashSet[ConfigValue](value.values)
    // serialization all goes through SerializedConfigValue
    @throws[ObjectStreamException]
    private def writeReplace(): Object = new SerializedConfigValue(this)
}
