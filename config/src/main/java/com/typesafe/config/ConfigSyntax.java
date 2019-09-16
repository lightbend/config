/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The syntax of a character stream (<a href="http://json.org">JSON</a>, <a
 * href="https://github.com/lightbend/config/blob/master/HOCON.md">HOCON</a>
 * aka ".conf", or <a href=
 * "http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29"
 * >Java properties</a>).
 * 
 */
public enum ConfigSyntax implements ConfigFormat{
    /**
     * Pedantically strict <a href="http://json.org">JSON</a> format; no
     * comments, no unexpected commas, no duplicate keys in the same object.
     * Associated with the <code>.json</code> file extension and
     * <code>application/json</code> Content-Type.
     */
    JSON("application/json"),
    /**
     * The JSON-superset <a
     * href="https://github.com/lightbend/config/blob/master/HOCON.md"
     * >HOCON</a> format. Associated with the <code>.conf</code> file extension
     * and <code>application/hocon</code> Content-Type.
     */
    CONF("application/hocon"),
    /**
     * Standard <a href=
     * "http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29"
     * >Java properties</a> format. Associated with the <code>.properties</code>
     * file extension and <code>text/x-java-properties</code> Content-Type.
     */
    PROPERTIES("text/x-java-properties");
	
	private final Set<String> extensions;
	private final Set<String> mimeTypes;
	
	private ConfigSyntax(String...mimeTypes) {
		Set<String> extensionsToUse = new LinkedHashSet<String>(1);
		Set<String> mimeTypesToUse = new LinkedHashSet<String>(mimeTypes.length);
		extensionsToUse.add(name().toLowerCase());
		mimeTypesToUse.addAll(Arrays.asList(mimeTypes));
		this.extensions=Collections.unmodifiableSet(extensionsToUse);
		this.mimeTypes=Collections.unmodifiableSet(mimeTypesToUse);
	}

	/**
	 *
	 * @return a {@link Set}{@link String &lt;String&gt;} of lowercase extensions acceptable by this format
	 * @see ConfigFormat#getExtensions()
	 */
	@Override
	public Set<String> getExtensions() {
		return this.extensions;
	}

	/**
	 * @return a {@link Set}{@link String &lt;String&gt;} of lowercase mime-types acceptable by this format
	 * @see ConfigFormat#getMimeTypes()
	 */
	@Override
	public Set<String> getMimeTypes() {
		return this.mimeTypes;
	}
	
	
	
}
