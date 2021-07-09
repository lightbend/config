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

package com.typesafe.config.impl;

import java.util.List;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.parser.ConfigNodePath;
import com.typesafe.config.parser.ConfigNodeSyntax;
import com.typesafe.config.parser.ConfigNodeVisitor;

// This is the public AST node for. for the internal node, see ConfigNodeParsedPath 
final class ConfigNodeUnparsedPath implements ConfigNodePath {
    private List<ConfigNodeSyntax> path;
    private ConfigOrigin origin;

    ConfigNodeUnparsedPath(List<ConfigNodeSyntax> path, ConfigOrigin origin) {
        this.path = path;
        this.origin = origin;
    }

    @Override
    public String render() {
        return toPath().render();
    }

    @Override
    public String toString() {
        return render();
    }

    private Path toPath() {
        // Note: we don't eagerly parse the path in case it has errors and the user
        // wants to fix them via updateSyntax
        return PathParser.parsePathExpression(path.stream().map(x -> ((ConfigNodeSingleToken) x).token()).iterator(),
                origin);
    }

    @Override
    public List<String> toList() {
        return toPath().toUnmodifiableJava();
    }

    @Override
    public List<ConfigNodeSyntax> getSyntaxNodes() {
        return path;
    }

    @Override
    public ConfigOrigin origin() {
        return origin;
    }

    @Override
    public ConfigNodePath updateSyntax(List<ConfigNodeSyntax> nodes) {
        return new ConfigNodeUnparsedPath(nodes, origin);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitPath(this);
    }

}
