/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

/**
 * The syntax of a character stream (<a href="http://json.org">JSON</a>, <a
 * href="https://github.com/lightbend/config/blob/master/HOCON.md">HOCON</a>
 * aka ".conf", or <a href=
 * "http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29"
 * >Java properties</a>).
 *
 */
final class ConfigSyntax private (name: String, ordinal: Int)
    extends Enum[ConfigSyntax](name, ordinal)

object ConfigSyntax {

    /**
     * Pedantically strict <a href="http://json.org">JSON</a> format; no
     * comments, no unexpected commas, no duplicate keys in the same object.
     * Associated with the <code>.json</code> file extension and
     * <code>application/json</code> Content-Type.
     */
    final val JSON = new ConfigSyntax("JSON", 0)

    /**
     * The JSON-superset <a
     * href="https://github.com/lightbend/config/blob/master/HOCON.md"
     * >HOCON</a> format. Associated with the <code>.conf</code> file extension
     * and <code>application/hocon</code> Content-Type.
     */
    final val CONF = new ConfigSyntax("CONF", 1)

    /**
     * Standard <a href=
     * "http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29"
     * >Java properties</a> format. Associated with the <code>.properties</code>
     * file extension and <code>text/x-java-properties</code> Content-Type.
     */
    final val PROPERTIES = new ConfigSyntax("PROPERTIES", 2)

    private[this] val _values: Array[ConfigSyntax] =
        Array(JSON, CONF, PROPERTIES)

    def values(): Array[ConfigSyntax] = _values.clone()

    def valueOf(name: String): ConfigSyntax =
        _values.find(_.name == name).getOrElse {
            throw new IllegalArgumentException("No enum const ConfigSyntax." + name)
        }
}
