/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigFormat;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderClasspath;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigIncluderURL;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigParseable;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.spi.ConfigProvider;

class SimpleIncluder implements FullIncluder {

    private ConfigIncluder fallback;

    SimpleIncluder(ConfigIncluder fallback) {
        this.fallback = fallback;
    }

    // ConfigIncludeContext does this for us on its options
    static ConfigParseOptions clearForInclude(ConfigParseOptions options) {
        // the class loader and includer are inherited, but not this other
        // stuff.
        return options.setSyntax(null).setOriginDescription(null).setAllowMissing(true);
    }

    // this is the heuristic includer
    @Override
    public ConfigObject include(final ConfigIncludeContext context, String name) {
        ConfigObject obj = includeWithoutFallback(context, name);

        // now use the fallback includer if any and merge
        // its result.
        if (fallback != null) {
            return obj.withFallback(fallback.include(context, name));
        } else {
            return obj;
        }
    }

    // the heuristic includer in static form
    static ConfigObject includeWithoutFallback(final ConfigIncludeContext context, String name) {
        // the heuristic is valid URL then URL, else relative to including file;
        // relativeTo in a file falls back to classpath inside relativeTo().

        URL url;
        try {
            url = new URL(name);
        } catch (MalformedURLException e) {
            url = null;
        }

        if (url != null) {
            return includeURLWithoutFallback(context, url);
        } else {
            NameSource source = new RelativeNameSource(context);
            return fromBasename(source, name, context.parseOptions());
        }
    }

    @Override
    public ConfigObject includeURL(ConfigIncludeContext context, URL url) {
        ConfigObject obj = includeURLWithoutFallback(context, url);

        // now use the fallback includer if any and merge
        // its result.
        if (fallback != null && fallback instanceof ConfigIncluderURL) {
            return obj.withFallback(((ConfigIncluderURL) fallback).includeURL(context, url));
        } else {
            return obj;
        }
    }

    static ConfigObject includeURLWithoutFallback(final ConfigIncludeContext context, URL url) {
        return ConfigFactory.parseURL(url, context.parseOptions()).root();
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File file) {
        ConfigObject obj = includeFileWithoutFallback(context, file);

        // now use the fallback includer if any and merge
        // its result.
        if (fallback != null && fallback instanceof ConfigIncluderFile) {
            return obj.withFallback(((ConfigIncluderFile) fallback).includeFile(context, file));
        } else {
            return obj;
        }
    }

    static ConfigObject includeFileWithoutFallback(final ConfigIncludeContext context, File file) {
        return ConfigFactory.parseFileAnySyntax(file, context.parseOptions()).root();
    }

    @Override
    public ConfigObject includeResources(ConfigIncludeContext context, String resource) {
        ConfigObject obj = includeResourceWithoutFallback(context, resource);

        // now use the fallback includer if any and merge
        // its result.
        if (fallback != null && fallback instanceof ConfigIncluderClasspath) {
            return obj.withFallback(((ConfigIncluderClasspath) fallback).includeResources(context,
                    resource));
        } else {
            return obj;
        }
    }

    static ConfigObject includeResourceWithoutFallback(final ConfigIncludeContext context,
                                                       String resource) {
        return ConfigFactory.parseResourcesAnySyntax(resource, context.parseOptions()).root();
    }

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
        if (this == fallback) {
            throw new ConfigException.BugOrBroken("trying to create includer cycle");
        } else if (this.fallback == fallback) {
            return this;
        } else if (this.fallback != null) {
            return new SimpleIncluder(this.fallback.withFallback(fallback));
        } else {
            return new SimpleIncluder(fallback);
        }
    }

    interface NameSource {
        ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions);
    }

    static private class RelativeNameSource implements NameSource {
        final private ConfigIncludeContext context;

        RelativeNameSource(ConfigIncludeContext context) {
            this.context = context;
        }

        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions options) {
            ConfigParseable p = context.relativeTo(name);
            if (p == null) {
                // avoid returning null
                return Parseable
                        .newNotFound(name, "include was not found: '" + name + "'", options);
            } else {
                return p;
            }
        }
    }

    ;

    private static String getExtension(String str) {
        int lastDot = str.lastIndexOf('.');
        return str.substring(lastDot + 1);
    }

    // this function is a little tricky because there are three places we're
    // trying to use it; for 'include "basename"' in a .conf file, for
    // loading app.{conf,json,properties} from classpath, and for
    // loading app.{conf,json,properties} from the filesystem.
    static ConfigObject fromBasename(NameSource source, String name, ConfigParseOptions options) {
        ConfigObject obj;

        String extension = getExtension(name);
        if (ConfigProviderService.getInstance().supportsExtension(extension)) {
            ConfigParseable p = source.nameToParseable(name, options);

            obj = p.parse(p.options().setAllowMissing(options.getAllowMissing()));
        } else {
            boolean gotSomething = false;
            List<ConfigException.IO> fails = new ArrayList<ConfigException.IO>();
            ConfigFormat syntax = options.getSyntax();
            List<ConfigProvider> providers = ConfigProviderService.getInstance().getProviders().collect(Collectors.toList());
            obj = SimpleConfigObject.empty(SimpleConfigOrigin.newSimple(name));
            for (int i = 0; i < providers.size(); i++) {
                ConfigProvider provider = providers.get(i);
                Set<String> extensions = provider.getExtensions();
                for (String ext : extensions) {
                    ConfigParseable handle = source.nameToParseable(name + "." + ext, options);
                    if (syntax == null || syntax.isSameAs(provider.getFormat())) {
                        try {
                            ConfigObject parsed = null;
                            if (provider.getFormat() == ConfigSyntax.CONF) {
                                obj = handle.parse(handle.options().setAllowMissing(false).setSyntax(provider.getFormat()));
                            } else {
                                parsed = handle.parse(handle.options()
                                        .setAllowMissing(false).setSyntax(provider.getFormat()));
                            }
                            if (parsed != null) {
                                obj = obj.withFallback(parsed);
                            }
                            gotSomething = true;
                        } catch (ConfigException.IO e) {
                            fails.add(e);
                        }
                    }
                }
            }

            if (!options.getAllowMissing() && !gotSomething) {
                if (ConfigImpl.traceLoadsEnabled()) {
                    // the individual exceptions should have been logged already
                    // with tracing enabled
                    ConfigImpl.trace("Did not find '" + name
                            + "' with any extension (.conf, .json, .properties); "
                            + "exceptions should have been logged above.");
                }

                if (fails.isEmpty()) {
                    // this should not happen
                    throw new ConfigException.BugOrBroken(
                            "should not be reached: nothing found but no exceptions thrown");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (Throwable t : fails) {
                        sb.append(t.getMessage());
                        sb.append(", ");
                    }
                    sb.setLength(sb.length() - 2);
                    throw new ConfigException.IO(SimpleConfigOrigin.newSimple(name), sb.toString(),
                            fails.get(0));
                }
            } else if (!gotSomething) {
                if (ConfigImpl.traceLoadsEnabled()) {
                    ConfigImpl.trace("Did not find '" + name
                            + "' with any extension (.conf, .json, .properties); but '" + name
                            + "' is allowed to be missing. Exceptions from load attempts should have been logged above.");
                }
            }
        }

        return obj;
    }

    // the Proxy is a proxy for an application-provided includer that uses our
    // default implementations when the application-provided includer doesn't
    // have an implementation.
    static private class Proxy implements FullIncluder {
        final ConfigIncluder delegate;

        Proxy(ConfigIncluder delegate) {
            this.delegate = delegate;
        }

        @Override
        public ConfigIncluder withFallback(ConfigIncluder fallback) {
            // we never fall back
            return this;
        }

        @Override
        public ConfigObject include(ConfigIncludeContext context, String what) {
            return delegate.include(context, what);
        }

        @Override
        public ConfigObject includeResources(ConfigIncludeContext context, String what) {
            if (delegate instanceof ConfigIncluderClasspath)
                return ((ConfigIncluderClasspath) delegate).includeResources(context, what);
            else
                return includeResourceWithoutFallback(context, what);
        }

        @Override
        public ConfigObject includeURL(ConfigIncludeContext context, URL what) {
            if (delegate instanceof ConfigIncluderURL)
                return ((ConfigIncluderURL) delegate).includeURL(context, what);
            else
                return includeURLWithoutFallback(context, what);
        }

        @Override
        public ConfigObject includeFile(ConfigIncludeContext context, File what) {
            if (delegate instanceof ConfigIncluderFile)
                return ((ConfigIncluderFile) delegate).includeFile(context, what);
            else
                return includeFileWithoutFallback(context, what);
        }
    }

    static FullIncluder makeFull(ConfigIncluder includer) {
        if (includer instanceof FullIncluder)
            return (FullIncluder) includer;
        else
            return new Proxy(includer);
    }
}
