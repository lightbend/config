/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.{ lang => jl }
import java.{ util => ju }
import java.util.Collections
import java.util.Comparator
import scala.collection.JavaConverters._
import scala.util.control.Breaks._
import com.typesafe.config.ConfigValueType
import com.typesafe.config.ConfigValueType._

/**
 * Default automatic type transformations.
 */
object DefaultTransformer {
    def transform(value: AbstractConfigValue, requested: ConfigValueType): AbstractConfigValue = {
        var retVal: AbstractConfigValue = value
        if (value.valueType == STRING) {
            val s = value.unwrapped.asInstanceOf[String]

            requested match {
                case NUMBER =>
                    try {
                        val v = jl.Long.parseLong(s)
                        retVal = new ConfigLong(value.origin, v, s)
                    } catch {
                        case e: NumberFormatException =>
                        // try Double
                    }
                    try {
                        val v = jl.Double.parseDouble(s)
                        retVal = new ConfigDouble(value.origin, v, s)
                    } catch {
                        case e: NumberFormatException =>
                        // oh well.
                    }
                    value
                case NULL =>
                    if (s == "null") // this case needs test
                        retVal = new ConfigNull(value.origin)
                case BOOLEAN =>
                    if (s == "true" || s == "yes" || s == "on")
                        retVal = new ConfigBoolean(value.origin, true)
                    else if (s == "false" || s == "no" || s == "off")
                        retVal = new ConfigBoolean(value.origin, false)
                case LIST | OBJECT | STRING => ()
                // can't go STRING to LIST automatically
                // can't go STRING to OBJECT automatically
                // no-op STRING to STRING
            }
        } else if (requested == ConfigValueType.STRING) {
            // if we converted null to string here, then you wouldn't properly
            // get a missing-value error if you tried to get a null value
            // as a string.
            value.valueType match {
                //case NUMBER => // FALL THROUGH
                case NUMBER | BOOLEAN =>
                    retVal = new ConfigString.Quoted(value.origin, value.transformToString)
                case NULL | OBJECT | LIST | STRING => ()
                // want to be sure this throws instead of returning "null" as a string
                // no OBJECT to STRING automatically
                // no LIST to STRING automatically
            }
        } else if ((requested == ConfigValueType.LIST) && (value.valueType == ConfigValueType.OBJECT)) {
            // attempt to convert an array-like (numeric indices) object to a
            // list. This would be used with .properties syntax for example:
            // -Dfoo.0=bar -Dfoo.1=baz
            // To ensure we still throw type errors for objects treated
            // as lists in most cases, we'll refuse to convert if the object
            // does not contain any numeric keys. This means we don't allow
            // empty objects here though :-/
            val o = value.asInstanceOf[AbstractConfigObject]
            val values = new ju.HashMap[Integer, AbstractConfigValue]
            for (key <- o.keySet.asScala) {
                breakable {
                    var i = 0
                    try {
                        i = Integer.parseInt(key, 10)
                        if (i < 0) {
                            break // continue
                        } else {
                            values.put(i, o.get(key))
                        }
                    } catch {
                        case e: NumberFormatException =>
                            break // continue
                    }
                }
            }
            if (!values.isEmpty) {
                val entryList =
                    new ju.ArrayList[ju.Map.Entry[Integer, AbstractConfigValue]](
                        values.entrySet)
                // sort by numeric index
                Collections.sort(
                    entryList,
                    new Comparator[ju.Map.Entry[Integer, AbstractConfigValue]]() {
                        override def compare(
                            a: ju.Map.Entry[Integer, AbstractConfigValue],
                            b: ju.Map.Entry[Integer, AbstractConfigValue]): Int =
                            Integer.compare(a.getKey, b.getKey)
                    })
                // drop the indices (we allow gaps in the indices, for better or
                // worse)
                val list = new ju.ArrayList[AbstractConfigValue]
                for (entry <- entryList.asScala) {
                    list.add(entry.getValue)
                }
                retVal = new SimpleConfigList(value.origin, list)
            }
        }
        retVal
    }
}
