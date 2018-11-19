/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.io.ObjectStreamException
import java.io.Serializable
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigValueType

@SerialVersionUID(2L)
final class ConfigBoolean(
    origin: ConfigOrigin,
    val value: Boolean)
    extends AbstractConfigValue(origin)
    with Serializable {

    override def valueType: ConfigValueType = ConfigValueType.BOOLEAN

    override def unwrapped: jl.Boolean = value

    override def transformToString: String = if (value) "true" else "false"

    override def newCopy(origin: ConfigOrigin) = new ConfigBoolean(origin, value)

    // serialization all goes through SerializedConfigValue
    @throws[ObjectStreamException]
    private def writeReplace(): jl.Object = new SerializedConfigValue(this)
}
