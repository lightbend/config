/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;


/**
 * A set of options related to bean creation.
 *
 * <p>
 * This object is immutable, so the "setters" return a new object.
 *
 * <p>
 * Here is an example of creating a custom {@code ConfigBeanFactoryOptions}:
 *
 * <pre>
 *     ConfigBeanFactoryOptions options = ConfigBeanFactoryOptions.defaults()
 *         .setAllowMissing(true)
 * </pre>
 *
 */
public final class ConfigBeanFactoryOptions {
    final boolean allowMissing;

    private ConfigBeanFactoryOptions(boolean allowMissing) {
        this.allowMissing = allowMissing;
    }

    /**
     * Gets an instance of <code>ConfigBeanFactoryOptions</code> with all fields
     * set to the default values. Start with this instance and make any
     * changes you need.
     * @return the default bean factory options
     */
    public static ConfigBeanFactoryOptions defaults() {
        return new ConfigBeanFactoryOptions(false);
    }

    /**
     * Set to false to throw an exception if the bean contains a non-optional field that lacks a configuration setting.
     * Set to true to just ignore fields missing a configuration thus keeping those to their corresponding default values.
     * Attempting to assign a value incompatible with the target field type will still throw an error
     *
     * @param allowMissing true to silently ignore fields missing settings
     * @return options with the "allow missing" flag set
     */
    public ConfigBeanFactoryOptions setAllowMissing(boolean allowMissing) {
        if (this.allowMissing == allowMissing)
            return this;
        else
            return new ConfigBeanFactoryOptions(allowMissing);
    }

    /**
     * Gets the current "allow missing" flag.
     * @return whether we allow fields missing a config setting
     */
    public boolean getAllowMissing() {
        return allowMissing;
    }
}
