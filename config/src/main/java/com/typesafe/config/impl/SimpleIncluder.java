/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigParseable;
import com.typesafe.config.ConfigSyntax;

class SimpleIncluder implements ConfigIncluder {

    private ConfigIncluder fallback;

    SimpleIncluder(ConfigIncluder fallback) {
        this.fallback = fallback;
    }

    @Override
    public ConfigObject include(final ConfigIncludeContext context, String name) {
        NameSource source = new NameSource() {
            @Override
            public ConfigParseable nameToParseable(String name) {
                ConfigParseable p = context.relativeTo(name);
                if (p == null) {
                    // avoid returning null
                    return Parseable.newNotFound(name, "include was not found: '" + name + "'",
                            ConfigParseOptions.defaults());
                } else {
                    return p;
                }
            }
        };

        ConfigObject obj = fromBasename(source, name, ConfigParseOptions.defaults()
                .setAllowMissing(true));

        // now use the fallback includer if any and merge
        // its result.
        if (fallback != null) {
            return obj.withFallback(fallback.include(context, name));
        } else {
            return obj;
        }
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
        ConfigParseable nameToParseable(String name);
    }

    // this function is a little tricky because there are three places we're
    // trying to use it; for 'include "basename"' in a .conf file, for
    // loading app.{conf,json,properties} from classpath, and for
    // loading app.{conf,json,properties} from the filesystem.
    static ConfigObject fromBasename(NameSource source, String name, ConfigParseOptions options) {
        ConfigObject obj;
        if (name.endsWith(".conf") || name.endsWith(".json") || name.endsWith(".properties")) {
            ConfigParseable p = source.nameToParseable(name);

            obj = p.parse(p.options().setAllowMissing(options.getAllowMissing()));
        } else {
            ConfigParseable confHandle = source.nameToParseable(name + ".conf");
            ConfigParseable jsonHandle = source.nameToParseable(name + ".json");
            ConfigParseable propsHandle = source.nameToParseable(name + ".properties");
            boolean gotSomething = false;
            List<String> failMessages = new ArrayList<String>();

            ConfigSyntax syntax = options.getSyntax();

            obj = SimpleConfigObject.empty(SimpleConfigOrigin.newSimple(name));
            if (syntax == null || syntax == ConfigSyntax.CONF) {
                try {
                    obj = confHandle.parse(confHandle.options().setAllowMissing(false)
                            .setSyntax(ConfigSyntax.CONF));
                    gotSomething = true;
                } catch (ConfigException.IO e) {
                    failMessages.add(e.getMessage());
                }
            }

            if (syntax == null || syntax == ConfigSyntax.JSON) {
                try {
                    ConfigObject parsed = jsonHandle.parse(jsonHandle.options()
                            .setAllowMissing(false).setSyntax(ConfigSyntax.JSON));
                    obj = obj.withFallback(parsed);
                    gotSomething = true;
                } catch (ConfigException.IO e) {
                    failMessages.add(e.getMessage());
                }
            }

            if (syntax == null || syntax == ConfigSyntax.PROPERTIES) {
                try {
                    ConfigObject parsed = propsHandle.parse(propsHandle.options()
                            .setAllowMissing(false).setSyntax(ConfigSyntax.PROPERTIES));
                    obj = obj.withFallback(parsed);
                    gotSomething = true;
                } catch (ConfigException.IO e) {
                    failMessages.add(e.getMessage());
                }
            }

            if (!options.getAllowMissing() && !gotSomething) {
                String failMessage;
                if (failMessages.isEmpty()) {
                    // this should not happen
                    throw new ConfigException.BugOrBroken(
                            "should not be reached: nothing found but no exceptions thrown");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (String msg : failMessages) {
                        sb.append(msg);
                        sb.append(", ");
                    }
                    sb.setLength(sb.length() - 2);
                    failMessage = sb.toString();
                }
                throw new ConfigException.IO(SimpleConfigOrigin.newSimple(name), failMessage);
            }
        }

        return obj;
    }
}
