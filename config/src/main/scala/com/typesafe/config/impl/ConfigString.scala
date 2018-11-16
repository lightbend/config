/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.io.ObjectStreamException
import java.io.Serializable
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueType

@SerialVersionUID(2L)
object ConfigString {

    final private[impl] class Quoted private[impl] (
        origin: ConfigOrigin,
        value: String)
        extends ConfigString(origin, value) {

        override def newCopy(origin: ConfigOrigin) =
            new ConfigString.Quoted(origin, value)

        // serialization all goes through SerializedConfigValue
        @throws[ObjectStreamException]
        private def writeReplace(): jl.Object = new SerializedConfigValue(this)
    }
    // this is sort of a hack; we want to preserve whether whitespace
    // was quoted until we process substitutions, so we can ignore
    // unquoted whitespace when concatenating lists or objects.
    // We dump this distinction when serializing and deserializing,
    // but that's OK because it isn't in equals/hashCode, and we
    // don't allow serializing unresolved objects which is where
    // quoted-ness matters. If we later make ConfigOrigin point
    // to the original token range, we could use that to implement
    // wasQuoted()
    final private[impl] class Unquoted private[impl] (
        origin: ConfigOrigin,
        value: String)
        extends ConfigString(origin, value) {

        override def newCopy(origin: ConfigOrigin) =
            new ConfigString.Unquoted(origin, value)

        @throws[ObjectStreamException]
        private def writeReplace(): jl.Object = new SerializedConfigValue(this)
    }
}

@SerialVersionUID(2L)
abstract class ConfigString(
    origin: ConfigOrigin,
    val value: String)
    extends AbstractConfigValue(origin)
    with Serializable {

    private[impl] def wasQuoted = this.isInstanceOf[ConfigString.Quoted]

    override def valueType: ConfigValueType = ConfigValueType.STRING

    override def unwrapped: String = value

    override def transformToString: String = value

    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit = {
        var rendered =
            if (options.getJson) ConfigImplUtil.renderJsonString(value)
            else ConfigImplUtil.renderStringUnquotedIfPossible(value)
        sb.append(rendered)
    }
}
