package com.typesafe.config.impl;

import java.util.List;

import com.typesafe.config.ConfigFormat;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.spi.ConfigProvider;

public abstract class AbstractConfigProvider implements ConfigProvider{

	private final ConfigFormat format;

	protected AbstractConfigProvider(ConfigFormat format) {
		if(format!=null) {
			if(format.isSameAs(ConfigSyntax.CONF)) {
				format=ConfigSyntax.CONF;
			} else if(format.isSameAs(ConfigSyntax.JSON)) {
				format=ConfigSyntax.JSON;
			} else if(format.isSameAs(ConfigSyntax.PROPERTIES)) {
				format=ConfigSyntax.PROPERTIES;
			}
		}
		this.format=format;
	}
	
	protected AbstractConfigProvider(List<String> extensions, String...mimeTypes) {
		this(new SimpleConfigFormat(extensions, mimeTypes));
	}
	
	public ConfigFormat getFormat() {
		return format;
	}
	
	
	
	
		
	
	
}
