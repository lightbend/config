/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

import java.io.File;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import com.typesafe.config.impl.ConfigImpl;
import com.typesafe.config.impl.Parseable;

/**
 * Contains static methods for creating {@link Config} instances.
 *
 * <p>
 * See also {@link ConfigValueFactory} which contains static methods for
 * converting Java values into a {@link ConfigObject}. You can then convert a
 * {@code ConfigObject} into a {@code Config} with {@link ConfigObject#toConfig}.
 *
 * <p>
 * The static methods with "load" in the name do some sort of higher-level
 * operation potentially parsing multiple resources and resolving substitutions,
 * while the ones with "parse" in the name just create a {@link ConfigValue}
 * from a resource and nothing else.
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
     * @return configuration for the requested root path
     */
    public static Config load(String rootPath) {
        return loadWithoutResolving(rootPath).resolve();
    }

    public static Config load(String rootPath,
            ConfigParseOptions parseOptions, ConfigResolveOptions resolveOptions) {
        return loadWithoutResolving(rootPath, parseOptions).resolve(
                resolveOptions);
    }

    /**
     * Like load() but does not resolve the config, so you can go ahead and add
     * more fallbacks and stuff and have them seen by substitutions when you do
     * call {@link Config#resolve()}.
     *
     * @param rootPath
     * @return configuration for the requested root path
     */
    public static Config loadWithoutResolving(String rootPath) {
        return loadWithoutResolving(rootPath, ConfigParseOptions.defaults());
    }

    public static Config loadWithoutResolving(String rootPath,
            ConfigParseOptions options) {
        Config system = ConfigImpl.systemPropertiesWithPrefix(rootPath);

        Config mainFiles = parseResourcesForPath(rootPath, options);
        Config referenceFiles = parseResourcesForPath(rootPath + ".reference",
                options);

        return system.withFallback(mainFiles).withFallback(referenceFiles);
    }

    public static Config empty() {
        return empty(null);
    }

    public static Config empty(String originDescription) {
        return ConfigImpl.emptyConfig(originDescription);
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
     * @return the parsed configuration
     */
    public static Config parseProperties(Properties properties,
            ConfigParseOptions options) {
        return Parseable.newProperties(properties, options).parse().toConfig();
    }

    public static Config parseReader(Reader reader, ConfigParseOptions options) {
        return Parseable.newReader(reader, options).parse().toConfig();
    }

    public static Config parseURL(URL url, ConfigParseOptions options) {
        return Parseable.newURL(url, options).parse().toConfig();
    }

    public static Config parseFile(File file, ConfigParseOptions options) {
        return Parseable.newFile(file, options).parse().toConfig();
    }

    /**
     * Parses a file with a flexible extension. If the <code>fileBasename</code>
     * already ends in a known extension, this method parses it according to
     * that extension (the file's syntax must match its extension). If the
     * <code>fileBasename</code> does not end in an extension, it parses files
     * with all known extensions and merges whatever is found.
     *
     * <p>
     * In the current implementation, the extension ".conf" forces
     * {@link ConfigSyntax#CONF}, ".json" forces {@link ConfigSyntax#JSON}, and
     * ".properties" forces {@link ConfigSyntax#PROPERTIES}. When merging files,
     * ".conf" falls back to ".json" falls back to ".properties".
     *
     * <p>
     * Future versions of the implementation may add additional syntaxes or
     * additional extensions. However, the ordering (fallback priority) of the
     * three current extensions will remain the same.
     *
     * <p>
     * If <code>options</code> forces a specific syntax, this method only parses
     * files with an extension matching that syntax.
     *
     * <p>
     * If {@link ConfigParseOptions#getAllowMissing options.getAllowMissing()}
     * is true, then no files have to exist; if false, then at least one file
     * has to exist.
     *
     * @param fileBasename
     *            a filename with or without extension
     * @param options
     *            parse options
     * @return the parsed configuration
     */
    public static Config parseFileAnySyntax(File fileBasename,
            ConfigParseOptions options) {
        return ConfigImpl.parseFileAnySyntax(fileBasename, options).toConfig();
    }

    /**
     * Parses all resources on the classpath with the given name and merges them
     * into a single <code>Config</code>.
     *
     * <p>
     * If the resource name does not begin with a "/", it will have the supplied
     * class's package added to it, in the same way as
     * {@link java.lang.Class#getResource}.
     *
     * <p>
     * Duplicate resources with the same name are merged such that ones returned
     * earlier from {@link ClassLoader#getResources} fall back to (have higher
     * priority than) the ones returned later. This implies that resources
     * earlier in the classpath override those later in the classpath when they
     * configure the same setting. However, in practice real applications may
     * not be consistent about classpath ordering, so be careful. It may be best
     * to avoid assuming too much.
     *
     * @param klass
     *            <code>klass.getClassLoader()</code> will be used to load
     *            resources, and non-absolute resource names will have this
     *            class's package added
     * @param resource
     *            resource to look up, relative to <code>klass</code>'s package
     *            or absolute starting with a "/"
     * @param options
     *            parse options
     * @return the parsed configuration
     */
    public static Config parseResources(Class<?> klass, String resource,
            ConfigParseOptions options) {
        return Parseable.newResources(klass, resource, options).parse()
                .toConfig();
    }

    /**
     * Parses classpath resources with a flexible extension. In general, this
     * method has the same behavior as
     * {@link #parseFileAnySyntax(File,ConfigParseOptions)} but for classpath
     * resources instead, as in {@link #parseResources}.
     *
     * <p>
     * There is a thorny problem with this method, which is that
     * {@link java.lang.ClassLoader#getResources} must be called separately for
     * each possible extension. The implementation ends up with separate lists
     * of resources called "basename.conf" and "basename.json" for example. As a
     * result, the ideal ordering between two files with different extensions is
     * unknown; there is no way to figure out how to merge the two lists in
     * classpath order. To keep it simple, the lists are simply concatenated,
     * with the same syntax priorities as
     * {@link #parseFileAnySyntax(File,ConfigParseOptions) parseFileAnySyntax()}
     * - all ".conf" resources are ahead of all ".json" resources which are
     * ahead of all ".properties" resources.
     *
     * @param klass
     *            class which determines the <code>ClassLoader</code> and the
     *            package for relative resource names
     * @param resourceBasename
     *            a resource name as in {@link java.lang.Class#getResource},
     *            with or without extension
     * @param options
     *            parse options
     * @return the parsed configuration
     */
    public static Config parseResourcesAnySyntax(Class<?> klass, String resourceBasename,
            ConfigParseOptions options) {
        return ConfigImpl.parseResourceAnySyntax(klass, resourceBasename,
                options).toConfig();
    }

    public static Config parseString(String s, ConfigParseOptions options) {
        return Parseable.newString(s, options).parse().toConfig();
    }

    /**
     * Parses classpath resources corresponding to this path expression.
     * Essentially if the path is "foo.bar" then the resources are
     * "/foo-bar.conf", "/foo-bar.json", and "/foo-bar.properties". If more than
     * one of those exists, they are merged.
     *
     * @param rootPath
     * @param options
     * @return the parsed configuration
     */
    public static Config parseResourcesForPath(String rootPath,
            ConfigParseOptions options) {
        // null originDescription is allowed in parseResourcesForPath
        return ConfigImpl.parseResourcesForPath(rootPath, options).toConfig();
    }

    /**
     * Creates a {@code Config} based on a {@link java.util.Map} from paths to
     * plain Java values. Similar to
     * {@link ConfigValueFactory#fromMap(Map,String)}, except the keys in the
     * map are path expressions, rather than keys; and correspondingly it
     * returns a {@code Config} instead of a {@code ConfigObject}. This is more
     * convenient if you are writing literal maps in code, and less convenient
     * if you are getting your maps from some data source such as a parser.
     *
     * <p>
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
     * @return the map converted to a {@code Config}
     */
    public static Config parseMap(Map<String, ? extends Object> values,
            String originDescription) {
        return ConfigImpl.fromPathMap(values, originDescription).toConfig();
    }

    /**
     * See the other overload of {@link #parseMap(Map, String)} for details,
     * this one just uses a default origin description.
     *
     * @param values
     * @return the map converted to a {@code Config}
     */
    public static Config parseMap(Map<String, ? extends Object> values) {
        return parseMap(values, null);
    }
}
