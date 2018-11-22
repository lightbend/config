/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType

import scala.annotation.varargs

object AbstractConfigObject {

    private def peekPath(
        self: AbstractConfigObject,
        path: Path): AbstractConfigValue =
        try { // we'll fail if anything along the path can't
            // be looked at without resolving.
            val next = path.remainder
            val v = self.attemptPeekWithPartialResolve(path.first)
            if (next == null) v
            else if (v.isInstanceOf[AbstractConfigObject])
                peekPath(v.asInstanceOf[AbstractConfigObject], next)
            else null
        } catch {
            case e: ConfigException.NotResolved =>
                throw ConfigImpl.improveNotResolved(path, e)
        }

    private[impl] def mergeOrigins(
        stack: ju.Collection[_ <: AbstractConfigValue]): ConfigOrigin = {
        if (stack.isEmpty)
            throw new ConfigException.BugOrBroken(
                "can't merge origins on empty list")
        val origins = new ju.ArrayList[ConfigOrigin]
        var firstOrigin: ConfigOrigin = null
        var numMerged = 0
        import scala.collection.JavaConverters._
        for (v <- stack.asScala) {
            if (firstOrigin == null) firstOrigin = v.origin
            if (v.isInstanceOf[AbstractConfigObject] && (v
                .asInstanceOf[AbstractConfigObject]
                .resolveStatus eq ResolveStatus.RESOLVED) && v
                .asInstanceOf[ConfigObject]
                .isEmpty) {
                // don't include empty files or the .empty()
                // config in the description, since they arex
                // likely to be "implementation details"
            } else {
                origins.add(v.origin)
                numMerged += 1
            }
        }
        if (numMerged == 0) { // the configs were all empty, so just use the first one
            origins.add(firstOrigin)
        }
        SimpleConfigOrigin.mergeOrigins(origins)
    }

    @varargs private[impl] def mergeOrigins(
        stack: AbstractConfigObject*): ConfigOrigin = {
        import scala.collection.JavaConverters._
        val javaColl = stack.asJavaCollection
        mergeOrigins(javaColl)
        //throws NotPossibleToResolve;
    }

    private def weAreImmutable(method: String) =
        new UnsupportedOperationException(
            "ConfigObject is immutable, you can't call Map." + method)
}

abstract class AbstractConfigObject(origin: ConfigOrigin)
    extends AbstractConfigValue(origin)
    with ConfigObject
    with Container {

    final private val config = new SimpleConfig(this)

    override def toConfig: SimpleConfig = config

    override def toFallbackValue: AbstractConfigObject = this

    override def withOnlyKey(key: String): AbstractConfigObject

    override def withoutKey(key: String): AbstractConfigObject

    override def withValue(key: String, value: ConfigValue): AbstractConfigObject

    private[impl] def withOnlyPathOrNull(path: Path): AbstractConfigObject

    private[impl] def withOnlyPath(path: Path): AbstractConfigObject

    private[impl] def withoutPath(path: Path): AbstractConfigObject

    private[impl] def withValue(path: Path, value: ConfigValue): AbstractConfigObject

    /**
     * This looks up the key with no transformation or type conversion of any
     * kind, and returns null if the key is not present. The object must be
     * resolved along the nodes needed to get the key or
     * ConfigException.NotResolved will be thrown.
     *
     * @param key
     * @return the unmodified raw value or null
     */
    final protected def peekAssumingResolved(key: String, originalPath: Path): AbstractConfigValue =
        try {
            attemptPeekWithPartialResolve(key)
        } catch {
            case e: ConfigException.NotResolved =>
                throw ConfigImpl.improveNotResolved(originalPath, e)
        }

    /**
     * Look up the key on an only-partially-resolved object, with no
     * transformation or type conversion of any kind; if 'this' is not resolved
     * then try to look up the key anyway if possible.
     *
     * @param key
     *            key to look up
     * @return the value of the key, or null if known not to exist
     * @throws ConfigException.NotResolved
     *             if can't figure out key's value (or existence) without more
     *             resolving
     */
    private[impl] def attemptPeekWithPartialResolve(key: String): AbstractConfigValue

    /**
     * Looks up the path with no transformation or type conversion. Returns null
     * if the path is not found; throws ConfigException.NotResolved if we need
     * to go through an unresolved node to look up the path.
     */
    protected def peekPath(path: Path): AbstractConfigValue = AbstractConfigObject.peekPath(this, path)

    override def valueType: ConfigValueType = ConfigValueType.OBJECT

    protected def newCopy(
        status: ResolveStatus,
        origin: ConfigOrigin): AbstractConfigObject

    override def newCopy(origin: ConfigOrigin): AbstractConfigObject = newCopy(resolveStatus, origin)

    override def constructDelayedMerge(
        origin: ConfigOrigin,
        stack: ju.List[AbstractConfigValue]) = new ConfigDelayedMergeObject(origin, stack)

    override def mergedWithObject(
        fallback: AbstractConfigObject): AbstractConfigObject = null

    override def withFallback(
        mergeable: ConfigMergeable): AbstractConfigObject = super.withFallback(mergeable).asInstanceOf[AbstractConfigObject]

    override def resolveSubstitutions(
        context: ResolveContext,
        source: ResolveSource): ResolveResult[_ <: AbstractConfigObject] = null

    override def relativized(prefix: Path): AbstractConfigObject = null

    override def get(key: Any): AbstractConfigValue

    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit

    override def clear(): Unit = {
        throw AbstractConfigObject.weAreImmutable("clear")
    }

    override def put(arg0: String, arg1: ConfigValue): ConfigValue = throw AbstractConfigObject.weAreImmutable("put")

    override def putAll(arg0: ju.Map[_ <: String, _ <: ConfigValue]): Unit = {
        throw AbstractConfigObject.weAreImmutable("putAll")
    }

    override def remove(arg0: Any): ConfigValue = throw AbstractConfigObject.weAreImmutable("remove")

    override def withOrigin(origin: ConfigOrigin): AbstractConfigObject = super.withOrigin(origin).asInstanceOf[AbstractConfigObject]
}
