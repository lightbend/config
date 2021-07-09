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
 * Abstract class for visiting {@link ConfigNode}. Defaults all nodes to
 * visitDefault. Implement and pass to
 * {@link ConfigNode#accept(ConfigNodeVisitor)} to enumerate the CST/AST.
 */
public abstract class AbstractConfigNodeVisitor<T> implements ConfigNodeVisitor<T> {

    @Override
    public T visitRoot(ConfigNodeRoot node) {
        return visitDefault(node);
    }

    @Override
    public T visitString(ConfigNodeString node) {
        return visitDefault(node);
    }

    @Override
    public T visitArray(ConfigNodeArray node) {
        return visitDefault(node);
    }

    @Override
    public T visitConcatenation(ConfigNodeConcatenation node) {
        return visitDefault(node);
    }

    @Override
    public T visitField(ConfigNodeField node) {
        return visitDefault(node);
    }

    @Override
    public T visitInclude(ConfigNodeInclude node) {
        return visitDefault(node);
    }

    @Override
    public T visitObject(ConfigNodeObject node) {
        return visitDefault(node);
    }

    @Override
    public T visitReference(ConfigNodeReference node) {
        return visitDefault(node);
    }

    @Override
    public T visitNull(ConfigNodeNull node) {
        return visitDefault(node);
    }

    @Override
    public T visitLong(ConfigNodeLong node) {
        return visitDefault(node);
    }

    @Override
    public T visitInt(ConfigNodeInt node) {
        return visitDefault(node);
    }

    @Override
    public T visitDouble(ConfigNodeDouble node) {
        return visitDefault(node);
    }

    @Override
    public T visitBoolean(ConfigNodeBoolean node) {
        return visitDefault(node);
    }

    @Override
    public T visitComment(ConfigNodeComment node) {
        return visitDefault(node);
    }

    @Override
    public T visitPath(ConfigNodePath node) {
        return visitDefault(node);
    }

    @Override
    public T visitSyntax(ConfigNodeSyntax node) {
        return visitDefault(node);
    }

    /**
     * A node was visited, but the specific type wasn't overridden, at the current
     * AST location.
     * 
     * @param node Node
     * @return user supplied value to return from
     *         {@link ConfigNode#accept(ConfigNodeVisitor)}
     */
    public abstract T visitDefault(ConfigNode node);

}
