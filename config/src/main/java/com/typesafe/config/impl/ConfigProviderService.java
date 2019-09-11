package com.typesafe.config.impl;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.spi.ConfigProvider;

/**'
 * This service contains all of the {@link ConfigProvider ConfigProviders} for load/parsing different config formats 
 * 
 * @author jamesratzlaff
 *
 */
public class ConfigProviderService {

	private static volatile ConfigProviderService INSTANCE;
	/**
	 * 
	 * @return a singleton instance of the {@link ConfigProviderService} 
	 */
	public static ConfigProviderService getInstance() {
		if(INSTANCE==null) {
			synchronized (ConfigProviderService.class) {
				INSTANCE=new ConfigProviderService();	
			}
		}
		return INSTANCE;
	}
	
	
	private ServiceLoader<ConfigProvider> loader;
	
	private ConfigProviderService() {
		this.loader=ServiceLoader.load(ConfigProvider.class);
	}
	
	/**
	 * 
	 * @param extension 
	 * @return {@code true} if the is at least one {@link ConfigProvider} that parses files with the given extension 
	 */
	public boolean supportsExtension(String extension) {
		return getProviders().anyMatch(provider->provider.acceptsExtension(extension));
	}
	
	ServiceLoader<ConfigProvider> getLoader(){
		if(this.loader==null) {
			this.loader=ServiceLoader.load(ConfigProvider.class);
		}
		return this.loader;
	}
	
	Stream<ConfigProvider> getProviders(){
		return StreamSupport.stream(getLoader().spliterator(), false).sorted();
	}
	/**
	 * 
	 * @param extension the extension that a returned value should support
	 * @return the first {@link ConfigProvider} (determined by {@link ConfigProvider#priority()} that indicates that it handles the given file extension
	 * 
	 *  @see ConfigProvider#priority()
	 */
	public ConfigProvider getByExtension(String extension) {
		ConfigProvider provider = getProviders().filter(configProvider->configProvider.acceptsExtension(extension)).findFirst().orElse(null);
		return provider;
	}
	/**
	 * 
	 * @param cpo - the {@link ConfigParseOptions} that will be checked to see which {@link ConfigProvider} supports all of its extensions
	 * @return the first {@link ConfigProvider} that handles <i>all</i> of the extension presented by {@code cpo}
	 */
	public ConfigProvider getByConfigParseOptions(ConfigParseOptions cpo) {
		return getProviders().filter(configProvider->configProvider.getExtensions().containsAll(cpo.getSyntax().getExtensions())).findFirst().orElse(null);
	}
	
	
	
	
}
