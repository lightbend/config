package com.typesafe.config.impl;

import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigParseable;
import com.typesafe.config.ConfigRoot;
import com.typesafe.config.ConfigSyntax;

/** This is public but is only supposed to be used by the "config" package */
public class ConfigImpl {

    private interface NameSource {
        ConfigParseable nameToParseable(String name);
    }

    // this function is a little tricky because there are two places we're
    // trying to use it; for 'include "basename"' in a .conf file, and for
    // loading app.{conf,json,properties} from classpath.
    private static ConfigObject fromBasename(NameSource source, String name,
            ConfigParseOptions options) {
        ConfigObject obj;
        if (name.endsWith(".conf") || name.endsWith(".json")
                || name.endsWith(".properties")) {
            ConfigParseable p = source.nameToParseable(name);

            if (p != null) {
                obj = p.parse(p.options().setAllowMissing(
                        options.getAllowMissing()));
            } else {
                obj = SimpleConfigObject.emptyMissing(new SimpleConfigOrigin(
                        name));
            }
        } else {
            ConfigParseable confHandle = source.nameToParseable(name + ".conf");
            ConfigParseable jsonHandle = source.nameToParseable(name + ".json");
            ConfigParseable propsHandle = source.nameToParseable(name
                    + ".properties");

            if (!options.getAllowMissing() && confHandle == null
                    && jsonHandle == null && propsHandle == null) {
                throw new ConfigException.IO(new SimpleConfigOrigin(name),
                        "No config files {.conf,.json,.properties} found");
            }

            ConfigSyntax syntax = options.getSyntax();

            obj = SimpleConfigObject.empty(new SimpleConfigOrigin(name));
            if (confHandle != null
                    && (syntax == null || syntax == ConfigSyntax.CONF)) {
                obj = confHandle.parse(confHandle.options()
                        .setAllowMissing(true).setSyntax(ConfigSyntax.CONF));
            }

            if (jsonHandle != null
                    && (syntax == null || syntax == ConfigSyntax.JSON)) {
                ConfigObject parsed = jsonHandle.parse(jsonHandle
                        .options().setAllowMissing(true)
                        .setSyntax(ConfigSyntax.JSON));
                obj = obj.withFallback(parsed);
            }

            if (propsHandle != null
                    && (syntax == null || syntax == ConfigSyntax.PROPERTIES)) {
                ConfigObject parsed = propsHandle.parse(propsHandle.options()
                        .setAllowMissing(true)
                        .setSyntax(ConfigSyntax.PROPERTIES));
                obj = obj.withFallback(parsed);
            }
        }

        return obj;
    }

    private static String makeResourceBasename(Path path) {
        StringBuilder sb = new StringBuilder("/");
        String next = path.first();
        Path remaining = path.remainder();
        while (next != null) {
            sb.append(next);
            sb.append('-');

            if (remaining == null)
                break;

            next = remaining.first();
            remaining = remaining.remainder();
        }
        sb.setLength(sb.length() - 1); // chop extra hyphen
        return sb.toString();
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parseResourcesForPath(String expression,
            final ConfigParseOptions baseOptions) {
        Path path = Parser.parsePath(expression);
        String basename = makeResourceBasename(path);
        NameSource source = new NameSource() {
            @Override
            public ConfigParseable nameToParseable(String name) {
                return Parseable.newResource(ConfigImpl.class, name,
                        baseOptions);
            }
        };
        return fromBasename(source, basename, baseOptions);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigRoot emptyRoot(String rootPath) {
        return SimpleConfigObject.empty(new SimpleConfigOrigin(rootPath))
                .asRoot(
                Parser.parsePath(rootPath));
    }

    public static ConfigObject empty() {
        return SimpleConfigObject.empty();
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigRoot systemPropertiesRoot(String rootPath) {
        Path path = Parser.parsePath(rootPath);
        try {
            return systemPropertiesAsConfig().getObject(rootPath).asRoot(path);
        } catch (ConfigException.Missing e) {
            return SimpleConfigObject.empty().asRoot(path);
        }
    }

    private static ConfigTransformer defaultTransformer = null;

    synchronized static ConfigTransformer defaultConfigTransformer() {
        if (defaultTransformer == null) {
            defaultTransformer = new DefaultTransformer();
        }
        return defaultTransformer;
    }

    private static class SimpleIncluder implements ConfigIncluder {

        private ConfigIncluder fallback;

        SimpleIncluder(ConfigIncluder fallback) {
            this.fallback = fallback;
        }

        @Override
        public ConfigObject include(final ConfigIncludeContext context,
                String name) {
            NameSource source = new NameSource() {
                @Override
                public ConfigParseable nameToParseable(String name) {
                    return context.relativeTo(name);
                }
            };

            ConfigObject obj = fromBasename(source, name, ConfigParseOptions
                    .defaults().setAllowMissing(true));

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
                throw new ConfigException.BugOrBroken(
                        "trying to create includer cycle");
            } else if (this.fallback == fallback) {
                return this;
            } else if (this.fallback != null) {
                return new SimpleIncluder(this.fallback.withFallback(fallback));
            } else {
                return new SimpleIncluder(fallback);
            }
        }
    }

    private static ConfigIncluder defaultIncluder = null;

    synchronized static ConfigIncluder defaultIncluder() {
        if (defaultIncluder == null) {
            defaultIncluder = new SimpleIncluder(null);
        }
        return defaultIncluder;
    }

    private static AbstractConfigObject systemProperties = null;

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public synchronized static AbstractConfigObject systemPropertiesAsConfig() {
        if (systemProperties == null) {
            systemProperties = loadSystemProperties();
        }
        return systemProperties;
    }

    private static AbstractConfigObject loadSystemProperties() {
        return (AbstractConfigObject) Parseable.newProperties(
                System.getProperties(),
                ConfigParseOptions.defaults().setOriginDescription(
                        "system properties")).parse();
    }

    // this is a hack to let us set system props in the test suite
    synchronized static void dropSystemPropertiesConfig() {
        systemProperties = null;
    }

    private static AbstractConfigObject envVariables = null;

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public synchronized static AbstractConfigObject envVariablesAsConfig() {
        if (envVariables == null) {
            envVariables = loadEnvVariables();
        }
        return envVariables;
    }

    private static AbstractConfigObject loadEnvVariables() {
        Map<String, String> env = System.getenv();
        Map<String, AbstractConfigValue> m = new HashMap<String, AbstractConfigValue>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            m.put(key, new ConfigString(
                    new SimpleConfigOrigin("env var " + key), entry.getValue()));
        }
        return new SimpleConfigObject(new SimpleConfigOrigin("env variables"),
                m, ResolveStatus.RESOLVED);
    }
}
