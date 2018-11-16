/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.io.ObjectStreamException
import java.io.Serializable
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin

@SerialVersionUID(2L)
object ConfigNumber {
    private[impl] def newNumber( // used ?
        origin: ConfigOrigin,
        number: Long,
        originalText: String): ConfigNumber =
        if (number <= Integer.MAX_VALUE && number >= Integer.MIN_VALUE)
            new ConfigInt(origin, number.toInt, originalText)
        else new ConfigLong(origin, number, originalText)

    def newNumber(
        origin: ConfigOrigin,
        number: Double,
        originalText: String): ConfigNumber = {
        val asLong = number.toLong
        if (asLong == number) newNumber(origin, asLong, originalText)
        else new ConfigDouble(origin, number, originalText)
    }
}

@SerialVersionUID(2L)
abstract class ConfigNumber(
    origin: ConfigOrigin,
    // This is so when we concatenate a number into a string (say it appears in
    // a sentence) we always have it exactly as the person typed it into the
    // config file. It's purely cosmetic; equals/hashCode don't consider this
    // for example.
    val originalText: String) extends AbstractConfigValue(origin)
    with Serializable {

    override def unwrapped: Number

    override def transformToString: String = originalText

    private[impl] def intValueRangeChecked(path: String) = {
        val l = longValue
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE)
            throw new ConfigException.WrongType(
                origin,
                path,
                "32-bit integer",
                "out-of-range value " + l)
        l.toInt
    }

    protected def longValue: Long

    protected def doubleValue: Double

    private def isWhole = {
        val asLong = longValue
        asLong == doubleValue
    }

    override def canEqual(other: Any): Boolean = other.isInstanceOf[ConfigNumber]

    override def equals(other: Any): Boolean = { // note that "origin" is deliberately NOT part of equality
        if (other.isInstanceOf[ConfigNumber] && canEqual(other)) {
            val n = other.asInstanceOf[ConfigNumber]
            if (isWhole) n.isWhole && this.longValue == n.longValue
            else (!n.isWhole) && this.doubleValue == n.doubleValue
        } else false
    }
    override def hashCode: Int = { // this matches what standard Long.hashCode and Double.hashCode
        // do, though I don't think it really matters.
        var asLong = 0L
        if (isWhole) asLong = longValue
        else asLong = jl.Double.doubleToLongBits(doubleValue)
        (asLong ^ (asLong >>> 32)).toInt
    }
    // serialization all goes through SerializedConfigValue
    @throws[ObjectStreamException]
    private def writeReplace = new SerializedConfigValue(this)
}
