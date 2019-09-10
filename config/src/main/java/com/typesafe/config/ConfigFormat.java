/**
 * 
 */
package com.typesafe.config;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.impl.SimpleConfigFormat;

/**
 * @author jamesratzlaff
 *
 */
public interface ConfigFormat {

	/**
	 * 
	 * @return a set of lowercase extensions acceptable by this format
	 */
	Set<String> getExtensions();

	/**
	 * 
	 * @return a set of lowercase mime-types acceptable by this format
	 */
	Set<String> getMimeTypes();

	/**
	 * 
	 * @return a new instance of a ConfigFormat using the default implementation of
	 *         {@link SimpleConfigFormat} with the extensions and mimetypes values
	 *         of {@code this} instance
	 */
	default ConfigFormat copy() {
		return new SimpleConfigFormat(getExtensions(), getMimeTypes());
	}

	/**
	 * 
	 * @param extensions
	 * @return a new instance of a ConfigFormat object that has the given extensions
	 *         and {@code this} instance's mimeType values. If the extension as a
	 *         {@link Set} are the same as {@code this} instances
	 *         {@link #getExtensions()} then the current instance will be returned
	 */
	default ConfigFormat withExtensions(String... extensions) {
		ConfigFormat toReturn = this;
		Set<String> asSet = new LinkedHashSet<String>(Arrays.asList(extensions).stream().filter(Objects::nonNull)
				.map(String::toLowerCase).collect(Collectors.toList()));
		boolean same = asSet.equals(getExtensions());
		if (!same) {
			toReturn = new SimpleConfigFormat(asSet, getMimeTypes());
			if (ConfigSyntax.CONF.isSameAs(toReturn)) {
				toReturn = ConfigSyntax.CONF;
			} else if (ConfigSyntax.JSON.isSameAs(toReturn)) {
				toReturn = ConfigSyntax.JSON;
			} else if (ConfigSyntax.PROPERTIES.isSameAs(toReturn)) {
				toReturn = ConfigSyntax.PROPERTIES;
			}
		}
		return toReturn;
	}

	/**
	 * 
	 * @param mimeTypes
	 * @return a new instance of a ConfigFormat object that has the given mimeTypes
	 *         and {@code this} instance's extension values. If the mimeTypes as a
	 *         {@link Set} are the same as {@code this} instances
	 *         {@link #getMimeTypes()} then the current instance will be returned
	 */
	default ConfigFormat withMimeTypes(String... mimeTypes) {
		ConfigFormat toReturn = this;
		Set<String> asSet = new LinkedHashSet<String>(Arrays.asList(mimeTypes).stream().filter(Objects::nonNull)
				.map(String::toLowerCase).collect(Collectors.toList()));
		boolean same = asSet.equals(getMimeTypes());
		if (!same) {
			toReturn = new SimpleConfigFormat(getExtensions(), asSet);
			if (ConfigSyntax.CONF.isSameAs(toReturn)) {
				toReturn = ConfigSyntax.CONF;
			} else if (ConfigSyntax.JSON.isSameAs(toReturn)) {
				toReturn = ConfigSyntax.JSON;
			} else if (ConfigSyntax.PROPERTIES.isSameAs(toReturn)) {
				toReturn = ConfigSyntax.PROPERTIES;
			}
		}
		return toReturn;
	}

	/**
	 * 
	 * @param other
	 * @return {@code true} if the values of {@code this}{@link #getExtensions()
	 *         .getExtensions()} and {@link #getMimeTypes()} are equal to
	 *         {@code other} respective values
	 */
	default boolean isSameAs(ConfigFormat other) {

		boolean truth = other != null;
		if (truth) {
			truth = getExtensions().equals(other.getExtensions());
		}
		if (truth) {
			truth = getMimeTypes().equals(other.getMimeTypes());
		}
		return truth;
	}

	/**
	 * 
	 * @return a string reflecting the value of an HTTP Accept-Content header
	 */
	default String acceptContent() {
		return String.join("; ", getMimeTypes());
	}

	/**
	 * 
	 * @param acceptsContentString "Content-Type" header returned from an http
	 *                             response
	 * @return
	 */
	default boolean acceptsContent(String contentType) {
		if (contentType == null) {
			return acceptsMimeType(contentType);
		}
		String[] contentTypes = contentType.split("[;]");
		boolean accepted = false;
		for (int i = 0; !accepted && i < contentTypes.length; i++) {
			String mimeType = contentTypes[i];
			accepted = acceptsMimeType(mimeType);
		}
		return accepted;
	}

	/**
	 * 
	 * @param extension
	 * @return {@code true} if the given {@code extension} is acceptable by this
	 *         format
	 */
	default boolean acceptsExtension(String extension) {
		return getExtensions().contains(extension != null ? extension.toLowerCase() : null);
	}

	/**
	 * 
	 * @param mimeType
	 * @return {@code true} if mimeType is a mimeType that this ConfigFormat accepts
	 */
	default boolean acceptsMimeType(String mimeType) {
		return getMimeTypes().contains(mimeType != null ? mimeType.toLowerCase() : null);
	}

	/**
	 * 
	 * @param url
	 * @return {@code true} if the given {@link URL url} has an extension acceptable
	 *         by this ConfigFormat
	 */
	default boolean test(URL url) {
		String extension = getExtension(url);
		return acceptsExtension(extension);
	}

	/**
	 * 
	 * @param file
	 * @return {@code true} if the given {@link File file} has an extension
	 *         acceptable by this ConfigFormat
	 */
	default boolean test(File file) {
		String extension = getExtension(file);
		return acceptsExtension(extension);
	}

	/**
	 * 
	 * @param path
	 * @return {@code true} if the given {@link Path path} has an extension
	 *         acceptable by this ConfigFormat
	 */
	default boolean test(Path path) {
		String extension = getExtension(path);
		return acceptsExtension(extension);
	}

	/**
	 * 
	 * @param resource
	 * @return {@code true} if the given resource path has an extension acceptable
	 *         by this ConfigFormat
	 */
	default boolean test(String resource) {
		String extension = getExtension(resource);
		return acceptsExtension(extension);
	}

	static String getExtension(File file) {
		String extension = null;
		if (file != null) {
			extension = getExtension(file.toPath());
		}
		return extension;
	}

	static String getExtension(Path path) {
		String extension = null;
		if (path != null) {
			extension = path.getFileName().toString();
		}
		return extension;
	}

	static String getExtension(String str) {
		String extension = null;
		if (str != null && !str.isEmpty()) {
			int lastSlash = str.lastIndexOf('/');
			lastSlash += 1;
			int lastDot = str.lastIndexOf('.', lastSlash);
			if (lastDot != -1) {
				extension = str.substring((lastDot + 1));
			}
		}
		return extension;
	}

	static String getExtension(URL url) {
		String extension = null;
		if (url != null) {
			String path = url.getPath();
			extension = getExtension(path);
		}
		return extension;
	}

}
