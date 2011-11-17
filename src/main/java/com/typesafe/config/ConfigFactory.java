package com.typesafe.config;

import java.io.File;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import com.typesafe.config.impl.ConfigImpl;
import com.typesafe.config.impl.Parseable;

/**
 * This class contains static methods for creating Config objects.
 *
 * The static methods with "load" in the name do some sort of higher-level
 * operation potentially parsing multiple resources and resolving substitutions,
 * while the ones with "parse" in the name just create a ConfigValue from a
 * resource and nothing else.
 */
public final class ConfigFactory {
    /**
     * Loads a configuration for the given root path in a "standard" way.
     * Oversimplified, if your root path is foo.bar then this will load files
     * from the classpath: foo-bar.conf, foo-bar.json, foo-bar.properties,
     * foo-bar-reference.conf, foo-bar-reference.json,
     * foo-bar-reference.properties. It will override all those files with any
     * system properties that begin with "foo.bar.", as well.
     *
     * The root path should be a path expression, usually just a single short
     * word, that scopes the package being configured; typically it's the
     * package name or something similar. System properties overriding values in
     * the configuration will have to be prefixed with the root path. The root
     * path may have periods in it if you like but other punctuation or
     * whitespace will probably cause you headaches. Example root paths: "akka",
     * "sbt", "jsoup", "heroku", "mongo", etc.
     *
     * The loaded object will already be resolved (substitutions have already
     * been processed). As a result, if you add more fallbacks then they won't
     * be seen by substitutions. Substitutions are the "${foo.bar}" syntax. If
     * you want to parse additional files or something then you need to use
     * loadWithoutResolving().
     *
     * @param rootPath
     *            the configuration "domain"
     * @return configuration object for the requested root path
     */
    public static ConfigRoot load(String rootPath) {
        return loadWithoutResolving(rootPath).resolve();
    }

    public static ConfigRoot load(String rootPath,
            ConfigParseOptions parseOptions, ConfigResolveOptions resolveOptions) {
        return loadWithoutResolving(rootPath, parseOptions).resolve(
                resolveOptions);
    }

    /**
     * Like load() but does not resolve the object, so you can go ahead and add
     * more fallbacks and stuff and have them seen by substitutions when you do
     * call {@link ConfigRoot.resolve()}.
     *
     * @param rootPath
     * @return
     */
    public static ConfigRoot loadWithoutResolving(String rootPath) {
        return loadWithoutResolving(rootPath, ConfigParseOptions.defaults());
    }

    public static ConfigRoot loadWithoutResolving(String rootPath,
            ConfigParseOptions options) {
        ConfigRoot system = systemPropertiesRoot(rootPath);

        Config mainFiles = parse(rootPath, options);
        Config referenceFiles = parse(rootPath + ".reference", options);

        return system.withFallbacks(mainFiles, referenceFiles);
    }

    public static ConfigRoot emptyRoot(String rootPath) {
        return emptyRoot(rootPath, null);
    }

    public static Config empty() {
        return empty(null);
    }

    public static ConfigRoot emptyRoot(String rootPath, String originDescription) {
        return ConfigImpl.emptyRoot(rootPath, originDescription);
    }

    public static Config empty(String originDescription) {
        return ConfigImpl.emptyConfig(originDescription);
    }

    public static ConfigRoot systemPropertiesRoot(String rootPath) {
        return ConfigImpl.systemPropertiesRoot(rootPath);
    }

    public static Config systemProperties() {
        return ConfigImpl.systemPropertiesAsConfig();
    }

    public static Config systemEnvironment() {
        return ConfigImpl.envVariablesAsConfig();
    }

    /**
     * Converts a Java Properties object to a ConfigObject using the rules
     * documented in https://github.com/havocp/config/blob/master/HOCON.md The
     * keys in the Properties object are split on the period character '.' and
     * treated as paths. The values will all end up as string values. If you
     * have both "a=foo" and "a.b=bar" in your properties file, so "a" is both
     * the object containing "b" and the string "foo", then the string value is
     * dropped.
     *
     * If you want to get System.getProperties() as a ConfigObject, it's better
     * to use the systemProperties() or systemPropertiesRoot() methods. Those
     * methods are able to use a cached global singleton ConfigObject for the
     * system properties.
     *
     * @param properties
     *            a Java Properties object
     * @param options
     * @return
     */
    public static Config parse(Properties properties,
            ConfigParseOptions options) {
        return Parseable.newProperties(properties, options).parse().toConfig();
    }

    public static Config parse(Reader reader, ConfigParseOptions options) {
        return Parseable.newReader(reader, options).parse().toConfig();
    }

    public static Config parse(URL url, ConfigParseOptions options) {
        return Parseable.newURL(url, options).parse().toConfig();
    }

    public static Config parse(File file, ConfigParseOptions options) {
        return Parseable.newFile(file, options).parse().toConfig();
    }

    public static Config parse(Class<?> klass, String resource,
            ConfigParseOptions options) {
        return Parseable.newResource(klass, resource, options).parse()
                .toConfig();
    }

    /**
     * Parses classpath resources corresponding to this path expression.
     * Essentially if the path is "foo.bar" then the resources are
     * "/foo-bar.conf", "/foo-bar.json", and "/foo-bar.properties". If more than
     * one of those exists, they are merged.
     *
     * @param path
     * @param options
     * @return
     */
    public static Config parse(String path, ConfigParseOptions options) {
        // null originDescription is allowed in parseResourcesForPath
        return ConfigImpl.parseResourcesForPath(path, options).toConfig();
    }

    /**
     * Similar to ConfigValueFactory.fromMap(), but the keys in the map are path
     * expressions, rather than keys; and correspondingly it returns a Config
     * instead of a ConfigObject. This is more convenient if you are writing
     * literal maps in code, and less convenient if you are getting your maps
     * from some data source such as a parser.
     *
     * An exception will be thrown (and it is a bug in the caller of the method)
     * if a path is both an object and a value, for example if you had both
     * "a=foo" and "a.b=bar", then "a" is both the string "foo" and the parent
     * object of "b". The caller of this method should ensure that doesn't
     * happen.
     *
     * @param values
     * @param originDescription
     *            description of what this map represents, like a filename, or
     *            "default settings" (origin description is used in error
     *            messages)
     * @return
     */
    public static Config parseMap(Map<String, ? extends Object> values,
            String originDescription) {
        return ConfigImpl.fromPathMap(values, originDescription).toConfig();
    }

    /**
     * See the other overload of parseMap() for details, this one just uses a
     * default origin description.
     *
     * @param values
     * @return
     */
    public static Config parseMap(Map<String, ? extends Object> values) {
        return parseMap(values, null);
    }
}
