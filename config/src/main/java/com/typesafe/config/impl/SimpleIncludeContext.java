/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

import com.twitter_typesafe.config.ConfigIncludeContext;
import com.twitter_typesafe.config.ConfigParseOptions;
import com.twitter_typesafe.config.ConfigParseable;

class SimpleIncludeContext implements ConfigIncludeContext {

    private final Parseable parseable;

    SimpleIncludeContext(Parseable parseable) {
        this.parseable = parseable;
    }

    SimpleIncludeContext withParseable(Parseable parseable) {
        if (parseable == this.parseable)
            return this;
        else
            return new SimpleIncludeContext(parseable);
    }

    @Override
    public ConfigParseable relativeTo(String filename) {
        if (ConfigImpl.traceLoadsEnabled())
            ConfigImpl.trace("Looking for '" + filename + "' relative to " + parseable);
        if (parseable != null)
            return parseable.relativeTo(filename);
        else
            return null;
    }

    @Override
    public ConfigParseOptions parseOptions() {
        return SimpleIncluder.clearForInclude(parseable.options());
    }
}
