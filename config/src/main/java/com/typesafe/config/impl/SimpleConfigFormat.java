package com.typesafe.config.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.ConfigFormat;
import com.typesafe.config.ConfigSyntax;

/**
 * This is a basic implementation of {@link ConfigFormat} all fields are
 * immutable and any setter create new instances of a ConfigFormat object.
 * 
 *
 * 
 * @author jamesratzlaff
 * @see ConfigSyntax
 */
public class SimpleConfigFormat implements ConfigFormat {
	private final Set<String> extensions;
	private final Set<String> mimeTypes;

	/**
	 * 
	 * @param extensions - the file extensions that this ConfigFormat supports
	 */
	public SimpleConfigFormat(String... extensions) {
		this(Arrays.asList(extensions));
	}

	/**
	 * 
	 * @param extensions - a {@link List} of extensions that this ConfigFormat
	 *                   supports
	 * @param mimeTypes  - the mime-types that are associated with this format
	 */
	public SimpleConfigFormat(List<String> extensions, String... mimeTypes) {
		Set<String> toUse = new LinkedHashSet<String>(
				extensions != null ? extensions.size() : 0);
		if (extensions != null) {
			toUse.addAll(extensions
					.stream()
					.filter(Objects::nonNull)
					.map(String::toLowerCase)
					.collect(Collectors.toList()));
		}
		this.extensions = Collections.unmodifiableSet(toUse);
		Set<String> mToUse = new LinkedHashSet<String>(mimeTypes.length);
		mToUse.addAll(Arrays.asList(mimeTypes).stream()
				.filter(Objects::nonNull)
				.map(String::toLowerCase)
				.collect(Collectors.toList()));
		this.mimeTypes = Collections.unmodifiableSet(mToUse);
	}

	/**
	 * 
	 * @param extensions - a {@link Set}{@link String &lt;String&gt;} of extensions
	 *                   that this ConfigFormat supports
	 * @param mimeTypes  - a {@link Set}{@link String &lt;String&gt;} of mime types
	 *                   associated with this format
	 */
	public SimpleConfigFormat(Set<String> extensions, Set<String> mimeTypes) {
		this.extensions = Collections
				.unmodifiableSet(extensions == null ? Collections.emptySet()
						: new LinkedHashSet<String>(extensions.stream()
								.filter(Objects::nonNull)
								.map(String::toLowerCase)
								.collect(Collectors.toList())));
		this.mimeTypes = Collections.unmodifiableSet(
				mimeTypes == null ? Collections.emptySet()
						: new LinkedHashSet<String>(
								mimeTypes.stream()
										.filter(Objects::nonNull)
										.map(String::toLowerCase)
										.collect(Collectors.toList())));
	}

	@Override
	public Set<String> getExtensions() {
		return extensions;
	}

	@Override
	public Set<String> getMimeTypes() {
		return mimeTypes;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((extensions == null) ? 0 : extensions.hashCode());
		result = prime * result + ((mimeTypes == null) ? 0 : mimeTypes.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleConfigFormat other = (SimpleConfigFormat) obj;
		if (extensions == null) {
			if (other.extensions != null)
				return false;
		} else if (!extensions.equals(other.extensions))
			return false;
		if (mimeTypes == null) {
			if (other.mimeTypes != null)
				return false;
		} else if (!mimeTypes.equals(other.mimeTypes))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SimpleConfigFormat [extensions=" + extensions + 
				", mimeTypes=" + mimeTypes+ "]";
	}

}
