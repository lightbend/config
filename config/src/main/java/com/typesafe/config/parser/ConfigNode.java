/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.parser;

import com.typesafe.config.ConfigOrigin;

/**
 * A node in the syntax tree for a HOCON or JSON document.
 * 
 * <p>
 * The root node can be retrieved via {@link ConfigDocument#getRoot()}, and
 * further nodes via manual seek or visitors.
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
     *
     * @return the original text used to form this node as a String
     */
    public String render();

    /**
     * Accepts a custom visitor for syntax tree traversal and returns the resulting
     * value (if any).
     * 
     * @param <T>     Type of the return
     * @param visitor Custom visitor returning T
     * @return Result of calling visitor.visit*(this)
     */
    public <T> T accept(ConfigNodeVisitor<T> visitor);

    /**
     * Returns the location of the current node in the source file
     * 
     * @return origin location
     */
    public ConfigOrigin origin();
}
