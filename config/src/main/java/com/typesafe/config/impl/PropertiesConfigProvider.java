package com.typesafe.config.impl;

import java.io.IOException;
import java.io.Reader;

import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

public class PropertiesConfigProvider extends AbstractConfigProvider{
	
	public PropertiesConfigProvider() {
		super(ConfigSyntax.PROPERTIES);
	}

	@Override
	public AbstractConfigValue rawParseValue(Reader reader, ConfigOrigin origin, ConfigParseOptions finalOptions,
			ConfigIncludeContext includeContext) throws IOException {
		return PropertiesParser.parse(reader, origin);
	}
}
