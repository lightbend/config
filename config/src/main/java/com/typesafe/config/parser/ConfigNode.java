/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.parser;

/**
 * A node in the syntax tree for a HOCON or JSON document.
 *
 * <p>
 * Because this object is immutable, it is safe to use from multiple threads and
 * there's no need for "defensive copies."
 *
 * <p>
 * <em>Do not implement interface {@code ConfigNode}</em>; it should only be
 * implemented by the config library. Arbitrary implementations will not work
 * because the library internals assume a specific concrete implementation.
 * Also, this interface is likely to grow new methods over time, so third-party
 * implementations will break.
 */
public interface ConfigNode {
    /**
     * The original text of the input which was used to form this particular node.
     * @return the original text used to form this node as a String
     */
    public String render();
}
