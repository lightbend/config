/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigParseable;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.impl.SimpleIncluder.NameSource;

/** This is public but is only supposed to be used by the "config" package */
public class ConfigImpl {

    private static class LoaderCache {
        private Config currentSystemProperties;
        private ClassLoader currentLoader;
        private Map<String, Config> cache;

        LoaderCache() {
            this.currentSystemProperties = null;
            this.currentLoader = null;
            this.cache = new HashMap<String, Config>();
        }

        // for now, caching as long as the loader remains the same,
        // drop entire cache if it changes.
        synchronized Config getOrElseUpdate(ClassLoader loader, String key, Callable<Config> updater) {
            if (loader != currentLoader) {
                // reset the cache if we start using a different loader
                cache.clear();
                currentLoader = loader;
            }

            Config systemProperties = systemPropertiesAsConfig();
            if (systemProperties != currentSystemProperties) {
                cache.clear();
                currentSystemProperties = systemProperties;
            }

            Config config = cache.get(key);
            if (config == null) {
                try {
                    config = updater.call();
                } catch (RuntimeException e) {
                    throw e; // this will include ConfigException
                } catch (Exception e) {
                    throw new ConfigException.Generic(e.getMessage(), e);
                }
                if (config == null)
                    throw new ConfigException.BugOrBroken("null config from cache updater");
                cache.put(key, config);
            }

            return config;
        }
    }

    private static class LoaderCacheHolder {
        static final LoaderCache cache = new LoaderCache();
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static Config computeCachedConfig(ClassLoader loader, String key,
            Callable<Config> updater) {
        LoaderCache cache;
        try {
            cache = LoaderCacheHolder.cache;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
        return cache.getOrElseUpdate(loader, key, updater);
    }


    static class FileNameSource implements SimpleIncluder.NameSource {
        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions) {
            return Parseable.newFile(new File(name), parseOptions);
        }
    };

    static class ClasspathNameSource implements SimpleIncluder.NameSource {
        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions) {
            return Parseable.newResources(name, parseOptions);
        }
    };

    static class ClasspathNameSourceWithClass implements SimpleIncluder.NameSource {
        final private Class<?> klass;

        public ClasspathNameSourceWithClass(Class<?> klass) {
            this.klass = klass;
        }

        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions) {
            return Parseable.newResources(klass, name, parseOptions);
        }
    };

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parseResourcesAnySyntax(Class<?> klass, String resourceBasename,
            ConfigParseOptions baseOptions) {
        NameSource source = new ClasspathNameSourceWithClass(klass);
        return SimpleIncluder.fromBasename(source, resourceBasename, baseOptions);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parseResourcesAnySyntax(String resourceBasename,
            ConfigParseOptions baseOptions) {
        NameSource source = new ClasspathNameSource();
        return SimpleIncluder.fromBasename(source, resourceBasename, baseOptions);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parseFileAnySyntax(File basename, ConfigParseOptions baseOptions) {
        NameSource source = new FileNameSource();
        return SimpleIncluder.fromBasename(source, basename.getPath(), baseOptions);
    }

    static AbstractConfigObject emptyObject(String originDescription) {
        ConfigOrigin origin = originDescription != null ? SimpleConfigOrigin
                .newSimple(originDescription) : null;
        return emptyObject(origin);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static Config emptyConfig(String originDescription) {
        return emptyObject(originDescription).toConfig();
    }

    static AbstractConfigObject empty(ConfigOrigin origin) {
        return emptyObject(origin);
    }

    // default origin for values created with fromAnyRef and no origin specified
    final private static ConfigOrigin defaultValueOrigin = SimpleConfigOrigin
            .newSimple("hardcoded value");
    final private static ConfigBoolean defaultTrueValue = new ConfigBoolean(
            defaultValueOrigin, true);
    final private static ConfigBoolean defaultFalseValue = new ConfigBoolean(
            defaultValueOrigin, false);
    final private static ConfigNull defaultNullValue = new ConfigNull(
            defaultValueOrigin);
    final private static SimpleConfigList defaultEmptyList = new SimpleConfigList(
            defaultValueOrigin, Collections.<AbstractConfigValue> emptyList());
    final private static SimpleConfigObject defaultEmptyObject = SimpleConfigObject
            .empty(defaultValueOrigin);

    private static SimpleConfigList emptyList(ConfigOrigin origin) {
        if (origin == null || origin == defaultValueOrigin)
            return defaultEmptyList;
        else
            return new SimpleConfigList(origin,
                    Collections.<AbstractConfigValue> emptyList());
    }

    private static AbstractConfigObject emptyObject(ConfigOrigin origin) {
        // we want null origin to go to SimpleConfigObject.empty() to get the
        // origin "empty config" rather than "hardcoded value"
        if (origin == defaultValueOrigin)
            return defaultEmptyObject;
        else
            return SimpleConfigObject.empty(origin);
    }

    private static ConfigOrigin valueOrigin(String originDescription) {
        if (originDescription == null)
            return defaultValueOrigin;
        else
            return SimpleConfigOrigin.newSimple(originDescription);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigValue fromAnyRef(Object object, String originDescription) {
        ConfigOrigin origin = valueOrigin(originDescription);
        return fromAnyRef(object, origin, FromMapMode.KEYS_ARE_KEYS);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject fromPathMap(
            Map<String, ? extends Object> pathMap, String originDescription) {
        ConfigOrigin origin = valueOrigin(originDescription);
        return (ConfigObject) fromAnyRef(pathMap, origin,
                FromMapMode.KEYS_ARE_PATHS);
    }

    static AbstractConfigValue fromAnyRef(Object object, ConfigOrigin origin,
            FromMapMode mapMode) {
        if (origin == null)
            throw new ConfigException.BugOrBroken(
                    "origin not supposed to be null");

        if (object == null) {
            if (origin != defaultValueOrigin)
                return new ConfigNull(origin);
            else
                return defaultNullValue;
        } else if (object instanceof Boolean) {
            if (origin != defaultValueOrigin) {
                return new ConfigBoolean(origin, (Boolean) object);
            } else if ((Boolean) object) {
                return defaultTrueValue;
            } else {
                return defaultFalseValue;
            }
        } else if (object instanceof String) {
            return new ConfigString(origin, (String) object);
        } else if (object instanceof Number) {
            // here we always keep the same type that was passed to us,
            // rather than figuring out if a Long would fit in an Int
            // or a Double has no fractional part. i.e. deliberately
            // not using ConfigNumber.newNumber() when we have a
            // Double, Integer, or Long.
            if (object instanceof Double) {
                return new ConfigDouble(origin, (Double) object, null);
            } else if (object instanceof Integer) {
                return new ConfigInt(origin, (Integer) object, null);
            } else if (object instanceof Long) {
                return new ConfigLong(origin, (Long) object, null);
            } else {
                return ConfigNumber.newNumber(origin,
                        ((Number) object).doubleValue(), null);
            }
        } else if (object instanceof Map) {
            if (((Map<?, ?>) object).isEmpty())
                return emptyObject(origin);

            if (mapMode == FromMapMode.KEYS_ARE_KEYS) {
                Map<String, AbstractConfigValue> values = new HashMap<String, AbstractConfigValue>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    Object key = entry.getKey();
                    if (!(key instanceof String))
                        throw new ConfigException.BugOrBroken(
                                "bug in method caller: not valid to create ConfigObject from map with non-String key: "
                                        + key);
                    AbstractConfigValue value = fromAnyRef(entry.getValue(),
                            origin, mapMode);
                    values.put((String) key, value);
                }

                return new SimpleConfigObject(origin, values);
            } else {
                return PropertiesParser.fromPathMap(origin, (Map<?, ?>) object);
            }
        } else if (object instanceof Iterable) {
            Iterator<?> i = ((Iterable<?>) object).iterator();
            if (!i.hasNext())
                return emptyList(origin);

            List<AbstractConfigValue> values = new ArrayList<AbstractConfigValue>();
            while (i.hasNext()) {
                AbstractConfigValue v = fromAnyRef(i.next(), origin, mapMode);
                values.add(v);
            }

            return new SimpleConfigList(origin, values);
        } else {
            throw new ConfigException.BugOrBroken(
                    "bug in method caller: not valid to create ConfigValue from: "
                            + object);
        }
    }

    private static class DefaultIncluderHolder {
        static final ConfigIncluder defaultIncluder = new SimpleIncluder(null);
    }

    static ConfigIncluder defaultIncluder() {
        try {
            return DefaultIncluderHolder.defaultIncluder;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    private static Properties getSystemProperties() {
        // Avoid ConcurrentModificationException due to parallel setting of system properties by copying properties
        final Properties systemProperties = System.getProperties();
        final Properties systemPropertiesCopy = new Properties();
        synchronized (systemProperties) {
            systemPropertiesCopy.putAll(systemProperties);
        }
        return systemPropertiesCopy;
    }

    private static AbstractConfigObject loadSystemProperties() {
        return (AbstractConfigObject) Parseable.newProperties(getSystemProperties(),
                ConfigParseOptions.defaults().setOriginDescription("system properties")).parse();
    }

    private static class SystemPropertiesHolder {
        // this isn't final due to the reloadSystemPropertiesConfig() hack below
        static volatile AbstractConfigObject systemProperties = loadSystemProperties();
    }

    static AbstractConfigObject systemPropertiesAsConfigObject() {
        try {
            return SystemPropertiesHolder.systemProperties;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static Config systemPropertiesAsConfig() {
        return systemPropertiesAsConfigObject().toConfig();
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static void reloadSystemPropertiesConfig() {
        // ConfigFactory.invalidateCaches() relies on this having the side
        // effect that it drops all caches
        SystemPropertiesHolder.systemProperties = loadSystemProperties();
    }

    private static AbstractConfigObject loadEnvVariables() {
        Map<String, String> env = System.getenv();
        Map<String, AbstractConfigValue> m = new HashMap<String, AbstractConfigValue>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            m.put(key,
                    new ConfigString(SimpleConfigOrigin.newSimple("env var " + key), entry
                            .getValue()));
        }
        return new SimpleConfigObject(SimpleConfigOrigin.newSimple("env variables"),
                m, ResolveStatus.RESOLVED, false /* ignoresFallbacks */);
    }

    private static class EnvVariablesHolder {
        static final AbstractConfigObject envVariables = loadEnvVariables();
    }

    static AbstractConfigObject envVariablesAsConfigObject() {
        try {
            return EnvVariablesHolder.envVariables;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static Config envVariablesAsConfig() {
        return envVariablesAsConfigObject().toConfig();
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static Config defaultReference(final ClassLoader loader) {
        return computeCachedConfig(loader, "defaultReference", new Callable<Config>() {
            @Override
            public Config call() {
                Config unresolvedResources = Parseable
                        .newResources("reference.conf",
                                ConfigParseOptions.defaults().setClassLoader(loader))
                        .parse().toConfig();
                return systemPropertiesAsConfig().withFallback(unresolvedResources).resolve();
            }
        });
    }

    private static class DebugHolder {
        private static String LOADS = "loads";

        private static Map<String, Boolean> loadDiagnostics() {
            Map<String, Boolean> result = new HashMap<String, Boolean>();
            result.put(LOADS, false);

            // People do -Dconfig.trace=foo,bar to enable tracing of different things
            String s = System.getProperty("config.trace");
            if (s == null) {
                return result;
            } else {
                String[] keys = s.split(",");
                for (String k : keys) {
                    if (k.equals(LOADS)) {
                        result.put(LOADS, true);
                    } else {
                        System.err.println("config.trace property contains unknown trace topic '"
                                + k + "'");
                    }
                }
                return result;
            }
        }

        private static final Map<String, Boolean> diagnostics = loadDiagnostics();

        private static final boolean traceLoadsEnabled = diagnostics.get(LOADS);

        static boolean traceLoadsEnabled() {
            return traceLoadsEnabled;
        }
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static boolean traceLoadsEnabled() {
        try {
            return DebugHolder.traceLoadsEnabled();
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    public static void trace(String message) {
        System.err.println(message);
    }

    // the basic idea here is to add the "what" and have a canonical
    // toplevel error message. the "original" exception may however have extra
    // detail about what happened. call this if you have a better "what" than
    // further down on the stack.
    static ConfigException.NotResolved improveNotResolved(Path what,
            ConfigException.NotResolved original) {
        String newMessage = what.render()
                + " has not been resolved, you need to call Config#resolve(),"
                + " see API docs for Config#resolve()";
        if (newMessage.equals(original.getMessage()))
            return original;
        else
            return new ConfigException.NotResolved(newMessage, original);
    }
}
