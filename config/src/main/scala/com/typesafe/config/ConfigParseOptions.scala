/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config

import com.typesafe.config.impl.ConfigImplUtil

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
object ConfigParseOptions {

    /**
     * Gets an instance of <code>ConfigParseOptions</code> with all fields
     * set to the default values. Start with this instance and make any
     * changes you need.
     *
     * @return the default parse options
     */
    def defaults() = new ConfigParseOptions(null, null, true, null, null)
}

final class ConfigParseOptions private (
    val syntax: ConfigSyntax,
    val originDescription: String,
    val allowMissing: Boolean,
    val includer: ConfigIncluder,
    val classLoader: ClassLoader) {

    /**
     * Set the file format. If set to null, try to guess from any available
     * filename extension; if guessing fails, assume {@link ConfigSyntax#CONF}.
     *
     * @param syntax
     *            a syntax or {@code null} for best guess
     * @return options with the syntax set
     */
    def setSyntax(syntax: ConfigSyntax): ConfigParseOptions =
        if (this.syntax eq syntax) this
        else
            new ConfigParseOptions(
                syntax,
                this.originDescription,
                this.allowMissing,
                this.includer,
                this.classLoader)

    /**
     * Set the file format. If set to null, assume {@link ConfigSyntax#CONF}.
     *
     * @param filename
     *            a configuration file name
     * @return options with the syntax set
     */
    def setSyntaxFromFilename(filename: String): ConfigParseOptions = {
        val syntax = ConfigImplUtil.syntaxFromExtension(filename)
        setSyntax(syntax)
    }

    /**
     * Gets the current syntax option, which may be null for "any".
     *
     * @return the current syntax or null
     */
    def getSyntax(): ConfigSyntax = syntax

    /**
     * Set a description for the thing being parsed. In most cases this will be
     * set up for you to something like the filename, but if you provide just an
     * input stream you might want to improve on it. Set to null to allow the
     * library to come up with something automatically. This description is the
     * basis for the {@link ConfigOrigin} of the parsed values.
     *
     * @param originDescription description to put in the {@link ConfigOrigin}
     * @return options with the origin description set
     */
    def setOriginDescription(originDescription: String): ConfigParseOptions = { // findbugs complains about == here but is wrong, do not "fix"
        if (this.originDescription eq originDescription) this
        else if (this.originDescription != null && originDescription != null && this.originDescription == originDescription)
            this
        else
            new ConfigParseOptions(
                this.syntax,
                originDescription,
                this.allowMissing,
                this.includer,
                this.classLoader)
    }

    /**
     * Gets the current origin description, which may be null for "automatic".
     *
     * @return the current origin description or null
     */
    def getOriginDescription(): String = originDescription

    /** this is package-private, not public API */
    private[config] def withFallbackOriginDescription(
        originDescription: String) =
        if (this.originDescription == null) setOriginDescription(originDescription)
        else this

    /**
     * Set to false to throw an exception if the item being parsed (for example
     * a file) is missing. Set to true to just return an empty document in that
     * case. Note that this setting applies on only to fetching the root document,
     * it has no effect on any nested includes.
     *
     * @param allowMissing true to silently ignore missing item
     * @return options with the "allow missing" flag set
     */
    def setAllowMissing(allowMissing: Boolean): ConfigParseOptions =
        if (this.allowMissing == allowMissing) this
        else
            new ConfigParseOptions(
                this.syntax,
                this.originDescription,
                allowMissing,
                this.includer,
                this.classLoader)

    /**
     * Gets the current "allow missing" flag.
     *
     * @return whether we allow missing files
     */
    def getAllowMissing(): Boolean = allowMissing

    /**
     * Set a {@link ConfigIncluder} which customizes how includes are handled.
     * null means to use the default includer.
     *
     * @param includer the includer to use or null for default
     * @return new version of the parse options with different includer
     */
    def setIncluder(includer: ConfigIncluder): ConfigParseOptions =
        if (this.includer eq includer) this
        else
            new ConfigParseOptions(
                this.syntax,
                this.originDescription,
                this.allowMissing,
                includer,
                this.classLoader)

    /**
     * Prepends a {@link ConfigIncluder} which customizes how
     * includes are handled.  To prepend your includer, the
     * library calls {@link ConfigIncluder#withFallback} on your
     * includer to append the existing includer to it.
     *
     * @param includer the includer to prepend (may not be null)
     * @return new version of the parse options with different includer
     */
    def prependIncluder(includer: ConfigIncluder): ConfigParseOptions = {
        if (includer == null)
            throw new NullPointerException("null includer passed to prependIncluder")
        if (this.includer eq includer) this
        else if (this.includer != null) setIncluder(includer.withFallback(this.includer))
        else setIncluder(includer)
    }

    /**
     * Appends a {@link ConfigIncluder} which customizes how
     * includes are handled.  To append, the library calls {@link
     * ConfigIncluder#withFallback} on the existing includer.
     *
     * @param includer the includer to append (may not be null)
     * @return new version of the parse options with different includer
     */
    def appendIncluder(includer: ConfigIncluder): ConfigParseOptions = {
        if (includer == null)
            throw new NullPointerException("null includer passed to appendIncluder")
        if (this.includer eq includer) this
        else if (this.includer != null) setIncluder(this.includer.withFallback(includer))
        else setIncluder(includer)
    }

    /**
     * Gets the current includer (will be null for the default includer).
     *
     * @return current includer or null
     */
    def getIncluder(): ConfigIncluder = includer

    /**
     * Set the class loader. If set to null,
     * <code>Thread.currentThread().getContextClassLoader()</code> will be used.
     *
     * @param loader
     *            a class loader or {@code null} to use thread context class
     *            loader
     * @return options with the class loader set
     */
    def setClassLoader(loader: ClassLoader): ConfigParseOptions =
        if (this.classLoader eq loader) this
        else
            new ConfigParseOptions(
                this.syntax,
                this.originDescription,
                this.allowMissing,
                this.includer,
                loader)

    /**
     * Get the class loader; never returns {@code null}, if the class loader was
     * unset, returns
     * <code>Thread.currentThread().getContextClassLoader()</code>.
     *
     * @return class loader to use
     */
    def getClassLoader(): ClassLoader =
        if (this.classLoader == null) Thread.currentThread.getContextClassLoader
        else this.classLoader
}
