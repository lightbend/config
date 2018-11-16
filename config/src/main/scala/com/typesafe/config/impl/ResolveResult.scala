package com.typesafe.config.impl

import com.typesafe.config.ConfigException

// value is allowed to be null
object ResolveResult {
    def make[V <: AbstractConfigValue](context: ResolveContext, value: V) =
        new ResolveResult[V](context, value)
}

final class ResolveResult[V <: AbstractConfigValue](val context: ResolveContext, val value: V) {

    // better option? we don't have variance
    @SuppressWarnings(Array("unchecked"))
    private[impl] def asObjectResult: ResolveResult[AbstractConfigObject] = {
        if (!value.isInstanceOf[AbstractConfigObject])
            throw new ConfigException.BugOrBroken(
                "Expecting a resolve result to be an object, but it was " + value)
        val o = this
        o.asInstanceOf[ResolveResult[AbstractConfigObject]]
    }

    @SuppressWarnings(Array("unchecked"))
    private[impl] def asValueResult: ResolveResult[AbstractConfigValue] = {
        val o = this
        o.asInstanceOf[ResolveResult[AbstractConfigValue]]
    }

    private[impl] def popTrace = ResolveResult.make(context.popTrace, value)

    override def toString: String = "ResolveResult(" + value + ")"
}
