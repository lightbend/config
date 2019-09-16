package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiPredicate;

import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.spi.ConfigProvider;

/**
 * ' This service contains all of the {@link ConfigProvider ConfigProviders} for
 * load/parsing different config formats.
 * 
 * @author jamesratzlaff
 *
 */
public class ConfigProviderService {
	private static final class ConfigProviderServiceContainer {
		private static final ConfigProviderService INSTANCE = new ConfigProviderService();
	}

	/**
	 * 
	 * @return a singleton instance of the {@link ConfigProviderService}
	 */
	public static ConfigProviderService getInstance() {
		return ConfigProviderServiceContainer.INSTANCE;
	}

	private ServiceLoader<ConfigProvider> loader;

	private ConfigProviderService() {
		this.loader = ServiceLoader.load(ConfigProvider.class);
	}

	/**
	 * 
	 * @param extension the extension to check for support
	 * @return {@code true} if the is at least one {@link ConfigProvider} that
	 *         parses files with the given extension
	 */
	public boolean supportsExtension(String extension) {
		return getByExtension(extension)!=null;
	}

	ServiceLoader<ConfigProvider> getLoader() {
		if (this.loader == null) {
			this.loader = ServiceLoader.load(ConfigProvider.class);
		}
		return this.loader;
	}

	/**
	 * reloads all of the ConfigProviders.
	 */
	public void reload() {
		getLoader().reload();
	}

	/**
	 * 
	 * @return a sort {@link List} of {@link ConfigProvider ConfigProviders} sorted by
	 * {@link ConfigProvider#priority() priority}
	 */
	List<ConfigProvider> getProviders() {
		ArrayList<ConfigProvider> configProviders = new ArrayList<ConfigProvider>(3);
		Iterator<ConfigProvider> configProvIter = getLoader().iterator();
		while(configProvIter.hasNext()) {
			configProviders.add(configProvIter.next());
		}
		Collections.sort(configProviders);
		configProviders.trimToSize();
		return configProviders;
	}
	
	

	/**
	 * 
	 * @param <T>       the type of value used in the predicate
	 * @param predicate a predicate to filter by
	 * @param value     the value to use with the predicate
	 * @return the first {@link ConfigProvider} matching the predicate, other
	 *         {@code null} if none matched
	 */
	private <T> ConfigProvider get(BiPredicate<ConfigProvider, T> predicate, final T value) {
		Iterator<ConfigProvider> providers = getLoader().iterator();
		ConfigProvider found = null;
		while (found == null && providers.hasNext()) {
			ConfigProvider configProvider = providers.next();
			boolean match = predicate.test(configProvider, value);
			if (match) {
				found = configProvider;
			}
		}
		return found;
	}

	/**
	 * 
	 * @param extension the extension that a returned value should support
	 * @return the first {@link ConfigProvider} (determined by
	 *         {@link ConfigProvider#priority()} that indicates that it handles the
	 *         given file extension
	 * 
	 * @see ConfigProvider#priority()
	 */
	public ConfigProvider getByExtension(String extension) {
		ConfigProvider provider = get(ConfigProvider::acceptsExtension, extension);
		return provider;
	}

	/**
	 * 
	 * @param cpo - the {@link ConfigParseOptions} that will be checked to see which
	 *            {@link ConfigProvider} supports all of its extensions
	 * @return the first {@link ConfigProvider} that handles <i>all</i> of the
	 *         extension presented by {@code cpo}
	 */
	public ConfigProvider getByConfigParseOptions(ConfigParseOptions cpo) {
		return get((configProvider, opts) -> opts != null && configProvider.getExtensions()
						.containsAll(opts.getFormat().getExtensions()),
				cpo);
	}

}
