package com.typesafe.config.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.spi.ConfigProvider;

/**
 * This {@link ConfigProvider} supports JSON files.
 * @author jamesratzlaff
 *
 */
public class JsonConfigProvider extends AbstractConfigProvider{

	public JsonConfigProvider() {
		super(ConfigSyntax.JSON);
	}

	@Override
	public ConfigValue rawParseValue(Reader reader, ConfigOrigin origin, ConfigParseOptions finalOptions,
			ConfigIncludeContext includeContext) throws IOException {
		Iterator<Token> tokens = Tokenizer.tokenize(origin, reader, finalOptions.getFormat());
		ConfigNodeRoot document = ConfigDocumentParser.parse(tokens, origin, finalOptions);
		return ConfigParser.parse(document, origin, finalOptions, includeContext);
	}
	
}
