/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;


/**
 * A set of options related to parsing.
 *
 * <p>
 * This object is immutable, so the "setters" return a new object.
 *
 * <p>
 * Here is an example of creating a custom {@code ConfigParseOptions}:
 *
 * <pre>
 *     ConfigParseOptions options = ConfigParseOptions.defaults()
 *         .setSyntax(ConfigSyntax.JSON)
 *         .setAllowMissing(false)
 * </pre>
 *
 */
public final class ConfigParseOptions {
    final ConfigSyntax syntax;
    final String originDescription;
    final boolean allowMissing;
    final ConfigIncluder includer;
    final ClassLoader classLoader;

    private ConfigParseOptions(ConfigSyntax syntax, String originDescription, boolean allowMissing,
            ConfigIncluder includer, ClassLoader classLoader) {
        this.syntax = syntax;
        this.originDescription = originDescription;
        this.allowMissing = allowMissing;
        this.includer = includer;
        this.classLoader = classLoader;
    }

    public static ConfigParseOptions defaults() {
        return new ConfigParseOptions(null, null, true, null, null);
    }

    /**
     * Set the file format. If set to null, try to guess from any available
     * filename extension; if guessing fails, assume {@link ConfigSyntax#CONF}.
     *
     * @param syntax
     *            a syntax or {@code null} for best guess
     * @return options with the syntax set
     */
    public ConfigParseOptions setSyntax(ConfigSyntax syntax) {
        if (this.syntax == syntax)
            return this;
        else
            return new ConfigParseOptions(syntax, this.originDescription, this.allowMissing,
                    this.includer, this.classLoader);
    }

    public ConfigSyntax getSyntax() {
        return syntax;
    }

    /**
     * Set a description for the thing being parsed. In most cases this will be
     * set up for you to something like the filename, but if you provide just an
     * input stream you might want to improve on it. Set to null to allow the
     * library to come up with something automatically. This description is the
     * basis for the {@link ConfigOrigin} of the parsed values.
     *
     * @param originDescription
     * @return options with the origin description set
     */
    public ConfigParseOptions setOriginDescription(String originDescription) {
        // findbugs complains about == here but is wrong, do not "fix"
        if (this.originDescription == originDescription)
            return this;
        else if (this.originDescription != null && originDescription != null
                && this.originDescription.equals(originDescription))
            return this;
        else
            return new ConfigParseOptions(this.syntax, originDescription, this.allowMissing,
                    this.includer, this.classLoader);
    }

    public String getOriginDescription() {
        return originDescription;
    }

    /** this is package-private, not public API */
    ConfigParseOptions withFallbackOriginDescription(String originDescription) {
        if (this.originDescription == null)
            return setOriginDescription(originDescription);
        else
            return this;
    }

    /**
     * Set to false to throw an exception if the item being parsed (for example
     * a file) is missing. Set to true to just return an empty document in that
     * case. Applies only to the root document, any includes are allowed to be missing.
     *
     * @param allowMissing
     * @return options with the "allow missing" flag set
     */
    public ConfigParseOptions setAllowMissing(boolean allowMissing) {
        if (this.allowMissing == allowMissing)
            return this;
        else
            return new ConfigParseOptions(this.syntax, this.originDescription, allowMissing,
                    this.includer, this.classLoader);
    }

    public boolean getAllowMissing() {
        return allowMissing;
    }

    /**
     * Set a ConfigIncluder which customizes how includes are handled.
     *
     * @param includer
     * @return new version of the parse options with different includer
     */
    public ConfigParseOptions setIncluder(ConfigIncluder includer) {
        if (this.includer == includer)
            return this;
        else
            return new ConfigParseOptions(this.syntax, this.originDescription, this.allowMissing,
                    includer, this.classLoader);
    }

    public ConfigParseOptions prependIncluder(ConfigIncluder includer) {
        if (this.includer == includer)
            return this;
        else if (this.includer != null)
            return setIncluder(includer.withFallback(this.includer));
        else
            return setIncluder(includer);
    }

    public ConfigParseOptions appendIncluder(ConfigIncluder includer) {
        if (this.includer == includer)
            return this;
        else if (this.includer != null)
            return setIncluder(this.includer.withFallback(includer));
        else
            return setIncluder(includer);
    }

    public ConfigIncluder getIncluder() {
        return includer;
    }

    /**
     * Set the class loader. If set to null,
     * <code>Thread.currentThread().getContextClassLoader()</code> will be used.
     *
     * @param loader
     *            a class loader or {@code null} to use thread context class
     *            loader
     * @return options with the class loader set
     */
    public ConfigParseOptions setClassLoader(ClassLoader loader) {
        if (this.classLoader == loader)
            return this;
        else
            return new ConfigParseOptions(this.syntax, this.originDescription, this.allowMissing,
                    this.includer, loader);
    }

    /**
     * Get the class loader; never returns {@code null}, if the class loader was
     * unset, returns
     * <code>Thread.currentThread().getContextClassLoader()</code>.
     *
     * @return class loader to use
     */
    public ClassLoader getClassLoader() {
        if (this.classLoader == null)
            return Thread.currentThread().getContextClassLoader();
        else
            return this.classLoader;
    }
}
