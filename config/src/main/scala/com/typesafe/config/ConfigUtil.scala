package com.typesafe.config

import java.{ util => ju }

import com.typesafe.config.impl.ConfigImplUtil

import scala.annotation.varargs

/**
 * Contains static utility methods.
 *
 */
object ConfigUtil {

    /**
     * Quotes and escapes a string, as in the JSON specification.
     *
     * @param s
     *            a string
     * @return the string quoted and escaped
     */
    def quoteString(s: String): String = ConfigImplUtil.renderJsonString(s)

    /**
     * Converts a list of keys to a path expression, by quoting the path
     * elements as needed and then joining them separated by a period. A path
     * expression is usable with a {@link Config}, while individual path
     * elements are usable with a {@link ConfigObject}.
     * <p>
     * See the overview documentation for {@link Config} for more detail on path
     * expressions vs. keys.
     *
     * @param elements
     *            the keys in the path
     * @return a path expression
     * @throws ConfigException
     *             if there are no elements
     */
    @varargs def joinPath(elements: String*): String = ConfigImplUtil.joinPath(elements)

    /**
     * Converts a list of strings to a path expression, by quoting the path
     * elements as needed and then joining them separated by a period. A path
     * expression is usable with a {@link Config}, while individual path
     * elements are usable with a {@link ConfigObject}.
     * <p>
     * See the overview documentation for {@link Config} for more detail on path
     * expressions vs. keys.
     *
     * @param elements
     *            the keys in the path
     * @return a path expression
     * @throws ConfigException
     *             if the list is empty
     */
    def joinPath(elements: ju.List[String]): String =
        ConfigImplUtil.joinPath(elements)

    /**
     * Converts a path expression into a list of keys, by splitting on period
     * and unquoting the individual path elements. A path expression is usable
     * with a {@link Config}, while individual path elements are usable with a
     * {@link ConfigObject}.
     * <p>
     * See the overview documentation for {@link Config} for more detail on path
     * expressions vs. keys.
     *
     * @param path
     *            a path expression
     * @return the individual keys in the path
     * @throws ConfigException
     *             if the path expression is invalid
     */
    def splitPath(path: String): ju.List[String] = ConfigImplUtil.splitPath(path)
}

final class ConfigUtil private () {}
