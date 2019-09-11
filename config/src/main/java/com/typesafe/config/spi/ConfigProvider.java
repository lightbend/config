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
import com.typesafe.config.impl.AbstractConfigProvider;

/**
 * This config provider interface allows for custom loading of different config
 * file types. The way a provider is usually look up is by file extension and
 * sometime mime-type
 * 
 * @author jamesratzlaff
 * @see AbstractConfigProvider
 */
public interface ConfigProvider extends ConfigFormat, Comparable<ConfigProvider> {

	/**
	 * 
	 * @return the priority in which this ConfigProvider should be used, with the
	 *         highest priority given to
	 *         {@link ConfigSyntax#CONF},{@link ConfigSyntax#JSON},{@link ConfigSyntax#PROPERTIES}
	 *         respectively
	 */
	default int priority() {
		if (getFormat() == ConfigSyntax.CONF) {
			return Integer.MIN_VALUE;
		} else if (getFormat() == ConfigSyntax.JSON) {
			return Integer.MIN_VALUE + 1;
		} else if (getFormat() == ConfigSyntax.PROPERTIES) {
			return Integer.MIN_VALUE + 2;
		}
		return Integer.MAX_VALUE;
	}

	/**
	 * 
	 * @return the {@link ConfigFormat} of this ConfigProvider
	 */
	ConfigFormat getFormat();

	/**
	 * This is the key method a subclass should implement to get their custom loader
	 * working with TypeSafe config
	 * 
	 * @param reader         the reader which reads in the data associated to the
	 *                       config file
	 * @param origin         the {@link ConfigOrigin}
	 * @param finalOptions   the {@link ConfigParseOptions}
	 * @param includeContext the {@link ConfigIncludeContext} for this parser, this
	 *                       can be null in some situation, such as when parsing
	 *                       properties
	 * @return a {@link ConfigValue} (most likely a {@link ConfigObject}
	 * @throws IOException if an IOException occurs
	 */
	ConfigValue rawParseValue(Reader reader, ConfigOrigin origin, ConfigParseOptions finalOptions,
			ConfigIncludeContext includeContext) throws IOException;

	@Override
	default Set<String> getExtensions() {
		ConfigFormat cf = getFormat();
		if (cf != null && cf != this) {
			return getFormat().getExtensions();
		} else {
			return Collections.emptySet();
		}
	}

	@Override
	default Set<String> getMimeTypes() {
		ConfigFormat cf = getFormat();
		if (cf != null && cf != this) {
			return getFormat().getMimeTypes();
		} else {
			return Collections.emptySet();
		}
	}

	@Override
	default int compareTo(ConfigProvider o) {
		if (o == this) {
			return 0;
		}
		if (o == null) {
			return -1;
		}
		return Integer.compare(priority(), o.priority());
	}

}
