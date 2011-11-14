package com.typesafe.config.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRoot;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValue;

/** This is public but is only supposed to be used by the "config" package */
public class ConfigImpl {
    private static AbstractConfigObject forceParsedToObject(ConfigValue value) {
        if (value instanceof AbstractConfigObject) {
            return (AbstractConfigObject) value;
        } else {
            throw new ConfigException.WrongType(value.origin(), "",
                    "object at file root", value.valueType().name());
        }
    }

    static AbstractConfigValue parseValue(Parseable parseable,
            ConfigParseOptions options) {
        ConfigOrigin origin = new SimpleConfigOrigin(
                options.getOriginDescription());
        return Parser.parse(parseable, origin, options);
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parse(Parseable parseable,
            ConfigParseOptions options) {
        return forceParsedToObject(parseValue(parseable, options));
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

    // ConfigParseOptions has a package-private method for this but I don't want
    // to make it public
    static ConfigOrigin originWithSuffix(ConfigParseOptions options,
            String suffix) {
        return new SimpleConfigOrigin(options.getOriginDescription() + suffix);
    }

    static String syntaxSuffix(ConfigSyntax syntax) {
        switch (syntax) {
        case PROPERTIES:
            return ".properties";
        case CONF:
            return ".conf";
        case JSON:
            return ".json";
        }
        throw new ConfigException.BugOrBroken("not a valid ConfigSyntax: "
                + syntax);
    }

    static AbstractConfigObject loadForResource(Class<?> loadClass,
            String basename, ConfigSyntax syntax, ConfigParseOptions options) {
        String suffix = syntaxSuffix(syntax);
        String resource = basename + suffix;

        // we want null rather than empty object if missingness is allowed,
        // so we can handle it.
        if (options.getAllowMissing()
                && loadClass.getResource(resource) == null) {
            return null;
        } else {
            return forceParsedToObject(Parser.parse(
                    Parseable.newResource(loadClass, resource),
                    originWithSuffix(options, suffix),
                    options.setSyntax(syntax)));
        }
    }

    static AbstractConfigObject checkAllowMissing(AbstractConfigObject obj,
            ConfigOrigin origin, ConfigParseOptions options) {
        if (obj == null) {
            if (options.getAllowMissing()) {
                return SimpleConfigObject.emptyMissing(origin);
            } else {
                throw new ConfigException.IO(origin,
                        "Resource not found on classpath");
            }
        } else {
            return obj;
        }
    }

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parseResourcesForPath(String expression,
            ConfigParseOptions baseOptions) {
        Path path = Parser.parsePath(expression);
        String basename = makeResourceBasename(path);

        Class<?> loadClass = ConfigImpl.class;

        ConfigParseOptions options;
        if (baseOptions.getOriginDescription() != null)
            options = baseOptions;
        else
            options = baseOptions.setOriginDescription(basename);

        if (options.getSyntax() != null) {
            ConfigSyntax syntax = options.getSyntax();
            AbstractConfigObject obj = loadForResource(loadClass, basename,
                    syntax, options);
            return checkAllowMissing(obj, originWithSuffix(options, syntaxSuffix(syntax)), options);
        } else {
            // we want to try all three then

            ConfigParseOptions allowMissing = options.setAllowMissing(true);
            AbstractConfigObject conf = loadForResource(loadClass, basename,
                    ConfigSyntax.CONF, allowMissing);
            AbstractConfigObject json = loadForResource(loadClass, basename,
                    ConfigSyntax.JSON, allowMissing);
            AbstractConfigObject props = loadForResource(loadClass, basename,
                    ConfigSyntax.PROPERTIES, allowMissing);

            ConfigOrigin baseOrigin = new SimpleConfigOrigin(options
                    .getOriginDescription());

            if (!options.getAllowMissing() && conf == null && json == null && props == null) {
                throw new ConfigException.IO(baseOrigin,
                        "No config files {.conf,.json,.properties} found on classpath");
            }

            AbstractConfigObject merged = SimpleConfigObject
                    .empty(baseOrigin);

            if (conf != null)
                merged = merged.withFallback(conf);
            if (json != null)
                merged = merged.withFallback(json);
            if (props != null)
                merged = merged.withFallback(props);

            return merged;
        }
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

    /** For use ONLY by library internals, DO NOT TOUCH not guaranteed ABI */
    public static ConfigObject parse(Properties properties,
            ConfigParseOptions options) {
        return Loader
                .fromProperties(options.getOriginDescription(), properties);
    }

    private static ConfigTransformer defaultTransformer = null;

    synchronized static ConfigTransformer defaultConfigTransformer() {
        if (defaultTransformer == null) {
            defaultTransformer = new DefaultTransformer();
        }
        return defaultTransformer;
    }

    private static IncludeHandler defaultIncluder = null;

    synchronized static IncludeHandler defaultIncluder() {
        if (defaultIncluder == null) {
            defaultIncluder = new IncludeHandler() {

                @Override
                public AbstractConfigObject include(String name) {
                    return Loader.load(name, this);
                }
            };
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
        return Loader.fromProperties("system property", System.getProperties());
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
