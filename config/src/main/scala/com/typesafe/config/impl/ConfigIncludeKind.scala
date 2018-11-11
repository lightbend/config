package com.typesafe.config.impl

class ConfigIncludeKind private (name: String, ordinal: Int)
    extends Enum[ConfigIncludeKind](name, ordinal)

object ConfigIncludeKind {

    final val URL = new ConfigIncludeKind("URL", 0)
    final val FILE = new ConfigIncludeKind("FILE", 1)
    final val CLASSPATH = new ConfigIncludeKind("CLASSPATH", 2)
    final val HEURISTIC = new ConfigIncludeKind("HEURISTIC", 3)

    private[this] val _values: Array[ConfigIncludeKind] =
        Array(URL, FILE, CLASSPATH, HEURISTIC)

    def values(): Array[ConfigIncludeKind] = _values.clone()

    def valueOf(name: String): ConfigIncludeKind =
        _values.find(_.name == name).getOrElse {
            throw new IllegalArgumentException("No enum const ConfigIncludeKind." + name)
        }
}