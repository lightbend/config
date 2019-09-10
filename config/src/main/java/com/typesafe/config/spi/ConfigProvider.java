package com.typesafe.config.spi;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

import com.typesafe.config.ConfigFormat;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValue;

public interface ConfigProvider extends ConfigFormat, Comparable<ConfigProvider> {

	default int priority() {
		if(getFormat()==ConfigSyntax.CONF) {
			return Integer.MIN_VALUE;
		} else if(getFormat()==ConfigSyntax.JSON) {
			return Integer.MIN_VALUE+1;
		} else if(getFormat()==ConfigSyntax.PROPERTIES) {
			return Integer.MIN_VALUE+2;
		}
		return Integer.MAX_VALUE;
	}
	
	ConfigFormat getFormat();
	
	ConfigValue rawParseValue(Reader reader, ConfigOrigin origin, ConfigParseOptions finalOptions, ConfigIncludeContext includeContext) throws IOException;
	

	@Override
	default Set<String> getExtensions() {
		ConfigFormat cf = getFormat();
		if(cf!=null&&cf!=this) {
			return getFormat().getExtensions();
		} else {
			return Collections.emptySet();
		}
	}

	@Override
	default Set<String> getMimeTypes() {
		ConfigFormat cf = getFormat();
		if(cf!=null&&cf!=this) {
			return getFormat().getMimeTypes();
		} else {
			return Collections.emptySet();
		}
	}

	@Override
	default int compareTo(ConfigProvider o) {
		if(o==this) {
			return 0;
		}
		if(o==null) {
			return -1;
		}
		return Integer.compare(priority(), o.priority());
	}

	
	
	
}
