package com.typesafe.config.impl;

import com.typesafe.config.ConfigDocument;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;

import java.io.StringReader;
import java.util.Iterator;

final class SimpleConfigDocument implements ConfigDocument {
    private ConfigNodeRoot configNodeTree;
    private ConfigParseOptions parseOptions;

    SimpleConfigDocument(ConfigNodeRoot parsedNode, ConfigParseOptions parseOptions) {
        configNodeTree = parsedNode;
        this.parseOptions = parseOptions;
    }

    public ConfigDocument setValue(String path, String newValue) {
        SimpleConfigOrigin origin = SimpleConfigOrigin.newSimple("single value parsing");
        StringReader reader = new StringReader(newValue);
        Iterator<Token> tokens = Tokenizer.tokenize(origin, reader, parseOptions.getSyntax());
        AbstractConfigNodeValue parsedValue = ConfigDocumentParser.parseValue(tokens, parseOptions);
        reader.close();

        return new SimpleConfigDocument(configNodeTree.setValue(path, parsedValue, parseOptions.getSyntax()), parseOptions);
    }

    public ConfigDocument setValue(String path, ConfigValue newValue) {
        return setValue(path, newValue.render());
    }

    public String render() {
        return configNodeTree.render();
    }
}
