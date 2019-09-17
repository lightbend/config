/**
 *
 */
package com.typesafe.config;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.typesafe.config.impl.SimpleConfigFormat;

/**
 * This class represents commonly known metadata about a given config format.
 * 
 * @author jamesratzlaff
 * @see SimpleConfigFormat
 * @see ConfigSyntax
 */
public interface ConfigFormat {
	/**
	 * utilities for removing nulls and remapping values in a given collection.
	 * 
	 * @author jamesratzlaff
	 *
	 */
	public static class CollectionRemapper {

		/**
		 * 
		 * @param strs the strings to lowercase
		 * @return a {@link List} of all of the values of {@code strs} lowercased and
		 *         all nulls removed. If {@code strs} is {@code null} {@code null} will
		 *         be returned
		 */
		public final static List<String> noNullsLowerCaseNullable(Collection<String> strs) {
			return noNullsLowerCase(strs, true);
		}

		/**
		 * 
		 * @param strs the strings to lowercase
		 * @return a {@link List} of all of the values of {@code strs} lowercased and
		 *         all nulls removed. If {@code strs} is {@code null} an empty list will
		 *         be returned. This should never return {@code null}
		 */
		public final static List<String> noNullsLowerCase(Collection<String> strs) {
			return noNullsLowerCase(strs, false);
		}
		
		

		/**
		 * 
		 * @param strs     the strings to lowercase
		 * @param nullable whether or not the return value can be {@code null}
		 * @return a {@link List} of all of the values of {@code strs} lowercased and
		 *         all nulls removed. If {@code strs} is {@code null} {@null} will be
		 *         returned if {@code nullable} is {@code true} otherwise and enmpty
		 *         list will be returned
		 */
		private final static List<String> noNullsLowerCase(Collection<String> strs,
				boolean nullable) {
			if (strs == null) {
				if (nullable) {
					return null;
				} else {
					strs = Collections.emptyList();
				}
			}
			ArrayList<String> lowerCaseNoNull = new ArrayList<String>(strs.size());
			Iterator<String> iterator = strs.iterator();
			while(iterator.hasNext()) {
				String str = iterator.next();
				if (str != null) {
					lowerCaseNoNull.add(str.toLowerCase());
				}
			}
			lowerCaseNoNull.trimToSize();
			return lowerCaseNoNull;
		}

		/**
		 * 
		 * @param strs the strings to lowercase
		 * @return a {@link List} of all of the values of {@code strs} lowercased and
		 *         all nulls removed.
		 */
		public final static List<String> noNullsLowerCase(String... strs) {
			return noNullsLowerCase(Arrays.asList(strs));
		}
	}

	/**
	 *
	 * @return a {@link Set}{@link String &lt;String&gt;} of lowercase extensions
	 *         acceptable by this format
	 */
	Set<String> getExtensions();

	/**
	 *
	 * @return a {@link Set}{@link String &lt;String&gt;} of lowercase mime-types
	 *         acceptable by this format
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
	 * @param extensions the file extension (prefixed period excluded) that the
	 *                   ConfigFormat is compatible with
	 * @return a new instance of a ConfigFormat object that has the given extensions
	 *         and {@code this} instance's mimeType values. If the extension as a
	 *         {@link Set} are the same as {@code this} instances
	 *         {@link #getExtensions()} then the current instance will be returned
	 */
	default ConfigFormat withExtensions(String... extensions) {
		ConfigFormat toReturn = this;
		Set<String> asSet = new LinkedHashSet<String>(
				CollectionRemapper.noNullsLowerCase(extensions));
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
	 * @param mimeTypes the mime types that the Config format is associated with
	 * @return a new instance of a ConfigFormat object that has the given mimeTypes
	 *         and {@code this} instance's extension values. If the mimeTypes as a
	 *         {@link Set} are the same as {@code this} instances
	 *         {@link #getMimeTypes()} then the current instance will be returned
	 */
	default ConfigFormat withMimeTypes(String... mimeTypes) {
		ConfigFormat toReturn = this;
		Set<String> asSet = new LinkedHashSet<String>(
				CollectionRemapper.noNullsLowerCase(mimeTypes));
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
	 * @param other some other ConfigFormat object
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
	 * @param contentType "Content-Type" header returned from an http response
	 * @return {@code true} if this ConfigFormat is associated to any of the given
	 *         mime-types, otherwise {@code false}
	 */
	default boolean acceptsContent(String contentType) {
		if (contentType == null) {
			return acceptsMimeType(contentType);
		}
		String[] contentTypes = contentType.split("\\s*[;]\\s*");
		boolean accepted = false;
		for (int i = 0; !accepted && i < contentTypes.length; i++) {
			String mimeType = contentTypes[i];
			accepted = acceptsMimeType(mimeType);
		}
		return accepted;
	}

	/**
	 *
	 * @param extension the extension (period excluded) to test against
	 * @return {@code true} if the given {@code extension} is acceptable by this
	 *         format
	 */
	default boolean acceptsExtension(String extension) {
		return getExtensions().contains(extension != null ? extension.toLowerCase() : null);
	}

	/**
	 *
	 * @param mimeType the mime type to test for association to {@code this}
	 *                 ConfigFormat instance
	 * @return {@code true} if mimeType is a mimeType that this ConfigFormat
	 *         accepts, otherwise {@code false}
	 */
	default boolean acceptsMimeType(String mimeType) {
		return getMimeTypes().contains(mimeType != null ? mimeType.toLowerCase() : null);
	}

	/**
	 *
	 * @param url the {@link URL url} to test for extension compatibility
	 * @return {@code true} if the given {@link URL url} has an extension acceptable
	 *         by this ConfigFormat
	 */
	default boolean test(URL url) {
		String extension = getExtension(url);
		return acceptsExtension(extension);
	}

	/**
	 *
	 * @param file the {@link File file} to test for extension compatibility
	 * @return {@code true} if the given {@link File file} has an extension
	 *         acceptable by this ConfigFormat
	 */
	default boolean test(File file) {
		String extension = getExtension(file);
		return acceptsExtension(extension);
	}

	/**
	 *
	 * @param path the {@link Path path} to test for extension compatibility
	 * @return {@code true} if the given {@link Path path} has an extension
	 *         acceptable by this ConfigFormat
	 */
	default boolean test(Path path) {
		String extension = getExtension(path);
		return acceptsExtension(extension);
	}

	/**
	 *
	 * @param resource the resource to test for extension compatibility
	 * @return {@code true} if the given resource path has an extension acceptable
	 *         by this ConfigFormat
	 */
	default boolean test(String resource) {
		String extension = getExtension(resource);
		return acceptsExtension(extension);
	}

	/**
	 *
	 * @param file the {@link File} to get the extension of
	 * @return the file's extension
	 */
	static String getExtension(File file) {
		String extension = null;
		if (file != null) {
			extension = getExtension(file.toPath());
		}
		return extension;
	}

	/**
	 *
	 * @param path the {@link Path} to get the extension of
	 * @return the path's extension
	 */
	static String getExtension(Path path) {
		String extension = null;
		if (path != null) {
			extension = getExtension(path.getFileName().toString());
		}
		return extension;
	}

	/**
	 *
	 * @param str the string to get the extension of
	 * @return the string's extension
	 */
	static String getExtension(String str) {
		String extension = null;
		if (str != null && !str.isEmpty()) {
			int lastDot = str.lastIndexOf('.');
			if (lastDot != -1) {
				extension = str.substring((lastDot + 1));
			}
		}
		return extension;
	}

	/**
	 *
	 * @param url the {@link URL} to get the extension of
	 * @return the url's extension
	 */
	static String getExtension(URL url) {
		String extension = null;
		if (url != null) {
			String path = url.getPath();
			extension = getExtension(path);
		}
		return extension;
	}

}
