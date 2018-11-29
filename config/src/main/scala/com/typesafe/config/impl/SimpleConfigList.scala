/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.ObjectStreamException
import java.io.Serializable
import java.{ lang => jl }
import java.{ util => ju }

import scala.collection.JavaConverters._
import com.typesafe.config._
import com.typesafe.config.impl.AbstractConfigValue._

@SerialVersionUID(2L)
object SimpleConfigList {

    private[impl] class ResolveModifier private[impl] (
        var context: ResolveContext,
        val source: ResolveSource)
        extends AbstractConfigValue.Modifier {
        @throws[AbstractConfigValue.NotPossibleToResolve]
        override def modifyChildMayThrow(key: String, v: AbstractConfigValue): AbstractConfigValue = {
            val result = context.resolve(v, source)
            context = result.context
            result.value
        }
    }
    private def wrapListIterator(i: ju.ListIterator[AbstractConfigValue]): ju.ListIterator[ConfigValue] =
        new ju.ListIterator[ConfigValue]() {
            override def hasNext: Boolean = i.hasNext
            override def next: ConfigValue = i.next
            override def remove(): Unit = throw weAreImmutable("listIterator().remove")
            override def add(arg0: ConfigValue): Unit = throw weAreImmutable("listIterator().add")
            override def hasPrevious: Boolean = i.hasPrevious
            override def nextIndex: Int = i.nextIndex
            override def previous: ConfigValue = i.previous
            override def previousIndex: Int = i.previousIndex
            override def set(arg0: ConfigValue): Unit = {
                throw weAreImmutable("listIterator().set")
            }
        }
    private def weAreImmutable(method: String) =
        new UnsupportedOperationException("ConfigList is immutable, you can't call List.'" + method + "'")
}

@SerialVersionUID(2L) /*final*/
class SimpleConfigList(
    origin: ConfigOrigin,
    val value: ju.List[AbstractConfigValue],
    status: ResolveStatus)
    extends AbstractConfigValue(origin) with ConfigList with Container with Serializable {
    final private val resolved: Boolean = status == ResolveStatus.RESOLVED
    // kind of an expensive debug check (makes this constructor pointless)
    if (status != ResolveStatus.fromValues(value))
        throw new ConfigException.BugOrBroken("SimpleConfigList created with wrong resolve status: " + this)

    def this(origin: ConfigOrigin, value: ju.List[AbstractConfigValue]) =
        this(origin, value, ResolveStatus.fromValues(value))

    override def valueType: ConfigValueType = ConfigValueType.LIST

    override def unwrapped: ju.List[AnyRef] = {
        val list = new ju.ArrayList[AnyRef]
        for (v <- value.asScala) {
            list.add(v.unwrapped.asInstanceOf[AnyRef])
        }
        list
    }
    override def resolveStatus: ResolveStatus =
        ResolveStatus.fromBoolean(resolved)
    override def replaceChild(child: AbstractConfigValue, replacement: AbstractConfigValue): SimpleConfigList = {
        val newList = AbstractConfigValue.replaceChildInList(value, child, replacement)
        if (newList == null) null
        else {
            // we use the constructor flavor that will recompute the resolve status
            new SimpleConfigList(origin, newList)
        }
    }
    override def hasDescendant(descendant: AbstractConfigValue): Boolean =
        AbstractConfigValue.hasDescendantInList(value, descendant)
    private def modify(
        modifier: AbstractConfigValue.NoExceptionsModifier,
        newResolveStatus: ResolveStatus): SimpleConfigList =
        try modifyMayThrow(modifier, newResolveStatus)
        catch {
            case e: RuntimeException =>
                throw e
            case e: Exception =>
                throw new ConfigException.BugOrBroken("unexpected checked exception", e)
        }
    @throws[Exception]
    private def modifyMayThrow(
        modifier: AbstractConfigValue.Modifier,
        newResolveStatus: ResolveStatus): SimpleConfigList = {
        // lazy-create for optimization
        var changed: ju.List[AbstractConfigValue] = null
        var i = 0
        for (v <- value.asScala) {
            val modified = modifier.modifyChildMayThrow(null /* key */ , v)
            // lazy-create the new list if required
            if (changed == null && (modified ne v)) {
                changed = new ju.ArrayList[AbstractConfigValue]
                var j = 0
                while (j < i) {
                    changed.add(value.get(j))
                    j += 1
                }
            }
            // once the new list is created, all elements
            // have to go in it. if modifyChild returned
            // null, we drop that element.
            if (changed != null && modified != null) changed.add(modified)
            i += 1
        }
        if (changed != null) if (newResolveStatus != null) new SimpleConfigList(origin, changed, newResolveStatus)
        else new SimpleConfigList(origin, changed)
        else this
    }
    @throws[AbstractConfigValue.NotPossibleToResolve]
    override def resolveSubstitutions(
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: SimpleConfigList] = {
        if (resolved) return ResolveResult.make(context, this)
        if (context.isRestrictedToChild) {
            // if a list restricts to a child path, then it has no child paths, so nothing to do.
            ResolveResult.make(context, this)
        } else try {
            val modifier = new SimpleConfigList.ResolveModifier(context, source.pushParent(this))
            val value = modifyMayThrow(modifier, if (context.options.getAllowUnresolved) null
            else ResolveStatus.RESOLVED)
            ResolveResult.make(modifier.context, value)
        } catch {
            case e: AbstractConfigValue.NotPossibleToResolve =>
                throw e
            case e: RuntimeException =>
                throw e
            case e: Exception =>
                throw new ConfigException.BugOrBroken("unexpected checked exception", e)
        }
    }
    override def relativized(prefix: Path): SimpleConfigList = modify(
        new NoExceptionsModifier() {
            override def modifyChild(key: String, v: AbstractConfigValue): AbstractConfigValue = v.relativized(prefix)
        },
        resolveStatus)
    override def canEqual(other: Any): Boolean = other.isInstanceOf[SimpleConfigList]
    override def equals(other: Any): Boolean = {
        // note that "origin" is deliberately NOT part of equality
        if (other.isInstanceOf[SimpleConfigList]) {
            // optimization to avoid unwrapped() for two ConfigList
            canEqual(other) && ((value eq other.asInstanceOf[SimpleConfigList].value) ||
                value == other.asInstanceOf[SimpleConfigList].value)
        } else false
    }
    override def hashCode: Int = value.hashCode
    override def render(sb: jl.StringBuilder, indentVal: Int, atRoot: Boolean, options: ConfigRenderOptions): Unit = {
        if (value.isEmpty) sb.append("[]")
        else {
            sb.append("[")
            if (options.getFormatted) sb.append('\n')
            for (v <- value.asScala) {
                if (options.getOriginComments) {
                    val lines = v.origin.description.split("\n")
                    for (l <- lines) {
                        indent(sb, indentVal + 1, options)
                        sb.append('#')
                        if (!l.isEmpty) sb.append(' ')
                        sb.append(l)
                        sb.append("\n")
                    }
                }
                if (options.getComments) {
                    for (comment <- v.origin.comments.asScala) {
                        indent(sb, indentVal + 1, options)
                        sb.append("# ")
                        sb.append(comment)
                        sb.append("\n")
                    }
                }
                indent(sb, indentVal + 1, options)
                v.render(sb, indentVal + 1, atRoot, options)
                sb.append(",")
                if (options.getFormatted) sb.append('\n')
            }
            sb.setLength(sb.length - 1) // chop or newline

            if (options.getFormatted) {
                sb.setLength(sb.length - 1) // also chop comma

                sb.append('\n')
                indent(sb, indentVal, options)
            }
            sb.append("]")
        }
    }
    override def contains(o: Any): Boolean = value.contains(o)
    override def containsAll(c: ju.Collection[_]): Boolean = value.containsAll(c)
    override def get(index: Int): AbstractConfigValue = value.get(index)
    override def indexOf(o: Any): Int = value.indexOf(o)
    override def isEmpty: Boolean = value.isEmpty
    override def iterator: ju.Iterator[ConfigValue] = {
        val i = value.iterator
        new ju.Iterator[ConfigValue]() {
            override def hasNext: Boolean = return i.hasNext
            override def next: ConfigValue = return i.next
            override def remove(): Unit = throw SimpleConfigList.weAreImmutable("iterator().remove")
        }
    }
    override def lastIndexOf(o: Any): Int = value.lastIndexOf(o)

    override def listIterator: ju.ListIterator[ConfigValue] = SimpleConfigList.wrapListIterator(value.listIterator)
    override def listIterator(index: Int): ju.ListIterator[ConfigValue] =
        SimpleConfigList.wrapListIterator(value.listIterator(index))
    override def size: Int = value.size
    override def subList(fromIndex: Int, toIndex: Int): ju.List[ConfigValue] = {
        val list = new ju.ArrayList[ConfigValue]
        // yay bloat caused by lack of type variance
        for (v <- value.subList(fromIndex, toIndex).asScala) {
            list.add(v)
        }
        list
    }
    override def toArray: Array[Object] = value.toArray
    override def toArray[T](a: Array[T with Object]): Array[T with Object] = value.toArray[T](a)
    override def add(e: ConfigValue) = throw SimpleConfigList.weAreImmutable("add")
    override def add(index: Int, element: ConfigValue): Unit = {
        throw SimpleConfigList.weAreImmutable("add")
    }
    override def addAll(c: ju.Collection[_ <: ConfigValue]) = throw SimpleConfigList.weAreImmutable("addAll")
    override def addAll(index: Int, c: ju.Collection[_ <: ConfigValue]) =
        throw SimpleConfigList.weAreImmutable("addAll")
    override def clear(): Unit = {
        throw SimpleConfigList.weAreImmutable("clear")
    }
    override def remove(o: Any) = throw SimpleConfigList.weAreImmutable("remove")
    override def remove(index: Int) = throw SimpleConfigList.weAreImmutable("remove")
    override def removeAll(c: ju.Collection[_]) = throw SimpleConfigList.weAreImmutable("removeAll")
    override def retainAll(c: ju.Collection[_]) = throw SimpleConfigList.weAreImmutable("retainAll")
    override def set(index: Int, element: ConfigValue) = throw SimpleConfigList.weAreImmutable("set")
    override def newCopy(newOrigin: ConfigOrigin) = new SimpleConfigList(newOrigin, value)
    final private[impl] def concatenate(other: SimpleConfigList) = {
        val combinedOrigin = SimpleConfigOrigin.mergeOrigins(origin, other.origin)
        val combined = new ju.ArrayList[AbstractConfigValue](value.size + other.value.size)
        combined.addAll(value)
        combined.addAll(other.value)
        new SimpleConfigList(combinedOrigin, combined)
    }
    // serialization all goes through SerializedConfigValue
    @throws[ObjectStreamException]
    private def writeReplace(): AnyRef = new SerializedConfigValue(this)
    override def withOrigin(origin: ConfigOrigin): SimpleConfigList =
        super.withOrigin(origin).asInstanceOf[SimpleConfigList]
}
