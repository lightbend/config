package com.typesafe.config.impl;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.spi.ConfigProvider;

public class ConfigProviderService {

	private static volatile ConfigProviderService INSTANCE;
	
	public static ConfigProviderService getInstance() {
		if(INSTANCE==null) {
			INSTANCE=new ConfigProviderService();
		}
		return INSTANCE;
	}
	
	
	private ServiceLoader<ConfigProvider> loader;
	
	private ConfigProviderService() {
		this.loader=ServiceLoader.load(ConfigProvider.class);
	}
	
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
	
	public ConfigProvider getByExtension(String extension) {
		ConfigProvider provider = getProviders().filter(configProvider->configProvider.acceptsExtension(extension)).findFirst().orElse(null);
		return provider;
	}
	
	public ConfigProvider getByConfigParseOptions(ConfigParseOptions cpo) {
		return getProviders().filter(configProvider->configProvider.getExtensions().equals(cpo.getSyntax().getExtensions())).findFirst().orElse(null);
	}
	
	
	
	
}
