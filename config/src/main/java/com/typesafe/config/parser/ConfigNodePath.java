/**
 * Copyright (C) 2011-2021, Config project contributors
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.typesafe.config.parser;

import java.util.List;

/**
 * Subtype of {@link ConfigNode} representing a path value, as used by fields
 * and references.
 *
 * <p>
 * Like all {@link ConfigNode} subtypes, {@code ConfigNodePath} is immutable.
 * This makes it threadsafe and you never have to create "defensive copies."
 *
 * <p>
 * <em>Do not implement {@code ConfigNodePath}</em>; it should only be
 * implemented by the config library. Arbitrary implementations will not work
 * because the library internals assume a specific concrete implementation.
 * Also, this interface is likely to grow new methods over time, so third-party
 * implementations will break.
 * 
 */
public interface ConfigNodePath extends ConfigNode {

    /**
     * Gets the concrete syntax nodes that are part of this path
     * @return syntax nodes associated with this path
     */
    List<ConfigNodeSyntax> getSyntaxNodes();

    /**
     * Gets the path as a list of names
     * @return path as a list of names
     */
    List<String> toList();

    /**
     * Gets the path as a dotted and escaped string
     * @return path as a string
     */
    String toString();

    /**
     * Create a derived path from just the provided syntax nodes
     * @param nodes List of syntax nodes that make up the path
     * @return The derivative path
     */
    ConfigNodePath updateSyntax(List<ConfigNodeSyntax> nodes);
}
