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

/**
 * This exists because sometimes null is not the same as missing. Specifically,
 * if a value is set to null we can give a better error message (indicating
 * where it was set to null) in case someone asks for the value. Also, null
 * overrides values set "earlier" in the search path, while missing values do
 * not.
 *
 */
@SerialVersionUID(2L)
final class ConfigNull(origin: ConfigOrigin)
    extends AbstractConfigValue(origin)
    with Serializable {

    override def valueType: ConfigValueType = ConfigValueType.NULL

    override def unwrapped: AnyRef = null

    override def transformToString = "null"

    override def render(
        sb: jl.StringBuilder,
        indent: Int,
        atRoot: Boolean,
        options: ConfigRenderOptions): Unit = {
        sb.append("null")
    }

    override def newCopy(origin: ConfigOrigin) = new ConfigNull(origin)

    // serialization all goes through SerializedConfigValue
    @throws[ObjectStreamException]
    private def writeReplace(): jl.Object = new SerializedConfigValue(this)
}
