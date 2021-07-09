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
 * Interface for visiting {@link ConfigNode}. Implement and pass to
 * {@link ConfigNode#accept(ConfigNodeVisitor)} to enumerate the CST/AST.
 * 
 * Also see {@link AbstractConfigNodeVisitor}
 */
public interface ConfigNodeVisitor<T> {

    /**
     * Root node present at the current AST location
     * 
     * @param node Root Node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitRoot(ConfigNodeRoot node);

    /**
     * String value node present at the current AST location
     * 
     * @param node String value node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitString(ConfigNodeString node);

    /**
     * Array node present at the current AST location
     * 
     * @param node Array node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitArray(ConfigNodeArray node);

    /**
     * Concatenation node present at the current AST location
     * 
     * @param node Concatenation node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitConcatenation(ConfigNodeConcatenation node);

    /**
     * Object Field node present at the current AST location
     * 
     * @param node Object Field node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitField(ConfigNodeField node);

    /**
     * Include node present at the current AST location
     * 
     * @param node Include node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitInclude(ConfigNodeInclude node);

    /**
     * Object node present at the current AST location
     * 
     * @param node Object node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitObject(ConfigNodeObject node);

    /**
     * Value Reference node present at the current AST location
     * 
     * @param node Value Reference node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitReference(ConfigNodeReference node);

    /**
     * Null value node present at the current AST location
     * 
     * @param node Null value node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitNull(ConfigNodeNull node);

    /**
     * Long value node present at the current AST location
     * 
     * @param node Long value node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitLong(ConfigNodeLong node);

    /**
     * Integer value node present at the current AST location
     * 
     * @param node Integer value node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitInt(ConfigNodeInt node);

    /**
     * IEEE Double floating point value node present at the current AST location
     * 
     * @param node double value node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitDouble(ConfigNodeDouble node);

    /**
     * Boolean value node present at the current AST location
     * 
     * @param node Boolean value node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitBoolean(ConfigNodeBoolean node);

    /**
     * Comment node present at the current AST location. Always exposed via iteration on the children of a node (if present)
     * 
     * @param node String node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitComment(ConfigNodeComment node);

    /**
     * Path node present at the current AST location. Note this is redundant, as all
     * places that provide a path are typed. As such this is provided for
     * completeness only.
     * 
     * @param node Path node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitPath(ConfigNodePath node);

    /**
     * Syntax node present at the current AST location, if
     * {@link ConfigNodeVisitor#includeSyntax()} is true.
     * 
     * @param node Syntax node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    T visitSyntax(ConfigNodeSyntax node);

    /**
     * Whether to return syntax nodes, or to skip them and return null from
     * {@link ConfigNode#accept(ConfigNodeVisitor)}. If true, this is effectively an
     * CST + AST visitor. If false, only visits the AST.
     * 
     * @return If Syntax nodes should be visited
     */
    boolean includeSyntax();

}
