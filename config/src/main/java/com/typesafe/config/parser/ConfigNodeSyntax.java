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

/**
 * Subtype of {@link ConfigNode} representing any syntactic elements in the
 * parsed AST.
 *
 * <p>
 * Like all {@link ConfigNode} subtypes, {@code ConfigNodeField} is immutable.
 * This makes it threadsafe and you never have to create "defensive copies."
 *
 * <p>
 * <em>Do not implement {@code ConfigNodeField}</em>; it should only be
 * implemented by the config library. Arbitrary implementations will not work
 * because the library internals assume a specific concrete implementation.
 * Also, this interface is likely to grow new methods over time, so third-party
 * implementations will break.
 * 
 */
public interface ConfigNodeSyntax extends ConfigNode {

    /**
     * Gets the text of this syntax.
     * 
     * @return String of characters that represent this syntax element
     */
    String getText();

    /**
     * Gets the parsed value (if any, null otherwise) of this syntax node.
     * 
     * @return The parsed value or null of this syntax node.
     */
    ConfigNode getAnyValue();
}
