package com.typesafe.config.impl;

import com.typesafe.config.ConfigDocument;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;

import java.io.StringReader;
import java.util.Iterator;

final class SimpleConfigDocument implements ConfigDocument {
    private ConfigNodeComplexValue configNodeTree;
    private ConfigParseOptions parseOptions;

    SimpleConfigDocument(ConfigNodeComplexValue parsedNode, ConfigParseOptions parseOptions) {
        configNodeTree = parsedNode;
        this.parseOptions = parseOptions;
    }

    public ConfigDocument setValue(String path, String newValue) {
        if (configNodeTree instanceof ConfigNodeArray) {
            throw new ConfigException.Generic("The ConfigDocument had an array at the root level, and values cannot be replaced inside an array.");
        }
        SimpleConfigOrigin origin = SimpleConfigOrigin.newSimple("single value parsing");
        StringReader reader = new StringReader(newValue);
        Iterator<Token> tokens = Tokenizer.tokenize(origin, reader, parseOptions.getSyntax());
        AbstractConfigNodeValue parsedValue = ConfigDocumentParser.parseValue(tokens, parseOptions);
        reader.close();

        return new SimpleConfigDocument(((ConfigNodeObject)configNodeTree).setValueOnPath(path, parsedValue, parseOptions.getSyntax()), parseOptions);
    }

    public String render() {
        return configNodeTree.render();
    }
}
