package com.typesafe.config;


public final class ConfigParseOptions {
    final ConfigSyntax syntax;
    final String originDescription;
    final boolean allowMissing;

    protected ConfigParseOptions(ConfigSyntax syntax, String originDescription,
            boolean allowMissing) {
        this.syntax = syntax;
        this.originDescription = originDescription;
        this.allowMissing = allowMissing;
    }

    public static ConfigParseOptions defaults() {
        return new ConfigParseOptions(null, null, true);
    }

    /**
     * Set the file format. If set to null, try to guess from any available
     * filename extension; if guessing fails, assume ConfigSyntax.CONF.
     *
     * @param syntax
     * @return
     */
    public ConfigParseOptions setSyntax(ConfigSyntax syntax) {
        if (this.syntax == syntax)
            return this;
        else
            return new ConfigParseOptions(syntax, this.originDescription,
                    this.allowMissing);
    }

    public ConfigSyntax getSyntax() {
        return syntax;
    }

    /**
     * Set a description for the thing being parsed. In most cases this will be
     * set up for you to something like the filename, but if you provide just an
     * input stream you might want to improve on it. Set to null to allow the
     * library to come up with something automatically.
     *
     * @param originDescription
     * @return
     */
    public ConfigParseOptions setOriginDescription(String originDescription) {
        if (this.originDescription == originDescription)
            return this;
        else if (this.originDescription != null && originDescription != null
                && this.originDescription.equals(originDescription))
            return this;
        else
            return new ConfigParseOptions(this.syntax, originDescription,
                    this.allowMissing);
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
     * case.
     *
     * @param allowMissing
     * @return
     */
    public ConfigParseOptions setAllowMissing(boolean allowMissing) {
        if (this.allowMissing == allowMissing)
            return this;
        else
            return new ConfigParseOptions(this.syntax, this.originDescription,
                    allowMissing);
    }

    public boolean getAllowMissing() {
        return allowMissing;
    }
}
