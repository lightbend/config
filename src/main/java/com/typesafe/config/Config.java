package com.typesafe.config;

import java.io.File;
import java.io.Reader;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.typesafe.config.impl.ConfigImpl;
import com.typesafe.config.impl.ConfigUtil;
import com.typesafe.config.impl.Parseable;

/**
 * This class holds some global static methods for the config package.
 *
 * The methods with "load" in the name do some sort of higher-level operation
 * potentially parsing multiple resources and resolving substitutions, while the
 * ones with "parse" in the name just create a ConfigValue from a resource and
 * nothing else.
 *
 * Throughout the API, there is a distinction between "keys" and "paths". A key
 * is a key in a JSON object; it's just a string that's the key in a map. A
 * "path" is a parseable expression with a syntax and it refers to a series of
 * keys. A path is used to traverse nested ConfigObject by looking up each key
 * in the path. Path expressions are described in the spec for "HOCON", which
 * can be found at https://github.com/havocp/config/blob/master/HOCON.md; in
 * brief, a path is period-separated so "a.b.c" looks for key c in object b in
 * object a in the root object. Sometimes double quotes are needed around
 * special characters in path expressions.
 */
public final class Config {

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

        ConfigValue mainFiles = parse(rootPath, options);
        ConfigValue referenceFiles = parse(rootPath + ".reference", options);

        return system.withFallbacks(mainFiles, referenceFiles);
    }

    public static ConfigRoot emptyRoot(String rootPath) {
        return emptyRoot(rootPath, null);
    }

    public static ConfigObject empty() {
        return empty(null);
    }

    public static ConfigRoot emptyRoot(String rootPath, String originDescription) {
        return ConfigImpl.emptyRoot(rootPath, originDescription);
    }

    public static ConfigObject empty(String originDescription) {
        return ConfigImpl.empty(originDescription);
    }

    public static ConfigRoot systemPropertiesRoot(String rootPath) {
        return ConfigImpl.systemPropertiesRoot(rootPath);
    }

    public static ConfigObject systemProperties() {
        return ConfigImpl.systemPropertiesAsConfig();
    }

    public static ConfigObject systemEnvironment() {
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
    public static ConfigObject parse(Properties properties,
            ConfigParseOptions options) {
        return Parseable.newProperties(properties, options).parse();
    }

    public static ConfigObject parse(Reader reader, ConfigParseOptions options) {
        return Parseable.newReader(reader, options).parse();
    }

    public static ConfigObject parse(URL url, ConfigParseOptions options) {
        return Parseable.newURL(url, options).parse();
    }

    public static ConfigObject parse(File file, ConfigParseOptions options) {
        return Parseable.newFile(file, options).parse();
    }

    public static ConfigObject parse(Class<?> klass, String resource,
            ConfigParseOptions options) {
        return Parseable.newResource(klass, resource, options).parse();
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
    public static ConfigObject parse(String path, ConfigParseOptions options) {
        // null originDescription is allowed in parseResourcesForPath
        return ConfigImpl.parseResourcesForPath(path, options);
    }

    /**
     * Creates a ConfigValue from a plain Java boxed value, which may be a
     * Boolean, Number, String, Map, Iterable, or null. A Map must be a Map from
     * String to more values that can be supplied to fromAnyRef(). An Iterable
     * must iterate over more values that can be supplied to fromAnyRef(). A Map
     * will become a ConfigObject and an Iterable will become a ConfigList. If
     * the Iterable is not an ordered collection, results could be strange,
     * since ConfigList is ordered.
     *
     * In a Map passed to fromAnyRef(), the map's keys are plain keys, not path
     * expressions. So if your Map has a key "foo.bar" then you will get one
     * object with a key called "foo.bar", rather than an object with a key
     * "foo" containing another object with a key "bar".
     *
     * The originDescription will be used to set the origin() field on the
     * ConfigValue. It should normally be the name of the file the values came
     * from, or something short describing the value such as "default settings".
     * The originDescription is prefixed to error messages so users can tell
     * where problematic values are coming from.
     *
     * Supplying the result of ConfigValue.unwrapped() to this function is
     * guaranteed to work and should give you back a ConfigValue that matches
     * the one you unwrapped. The re-wrapped ConfigValue will lose some
     * information that was present in the original such as its origin, but it
     * will have matching values.
     *
     * This function throws if you supply a value that cannot be converted to a
     * ConfigValue, but supplying such a value is a bug in your program, so you
     * should never handle the exception. Just fix your program (or report a bug
     * against this library).
     *
     * @param object
     *            object to convert to ConfigValue
     * @param originDescription
     *            name of origin file or brief description of what the value is
     * @return a new value
     */
    public static ConfigValue fromAnyRef(Object object, String originDescription) {
        return ConfigImpl.fromAnyRef(object, originDescription);
    }

    /**
     * See the fromAnyRef() documentation for details. This is a typesafe
     * wrapper that only works on Map and returns ConfigObject rather than
     * ConfigValue.
     *
     * If your Map has a key "foo.bar" then you will get one object with a key
     * called "foo.bar", rather than an object with a key "foo" containing
     * another object with a key "bar". The keys in the map are keys; not path
     * expressions. That is, the Map corresponds exactly to a single
     * ConfigObject. The keys will not be parsed or modified, and the values are
     * wrapped in ConfigValue. To get nested ConfigObject, some of the values in
     * the map would have to be more maps.
     *
     * @param values
     * @param originDescription
     * @return
     */
    public static ConfigObject fromMap(Map<String, ? extends Object> values,
            String originDescription) {
        return (ConfigObject) fromAnyRef(values, originDescription);
    }

    /**
     * See the fromAnyRef() documentation for details. This is a typesafe
     * wrapper that only works on Iterable and returns ConfigList rather than
     * ConfigValue.
     *
     * @param values
     * @param originDescription
     * @return
     */
    public static ConfigList fromIterable(Iterable<? extends Object> values,
            String originDescription) {
        return (ConfigList) fromAnyRef(values, originDescription);
    }

    /**
     * See the other overload of fromAnyRef() for details, this one just uses a
     * default origin description.
     *
     * @param object
     * @return
     */
    public static ConfigValue fromAnyRef(Object object) {
        return fromAnyRef(object, null);
    }

    /**
     * See the other overload of fromMap() for details, this one just uses a
     * default origin description.
     *
     * @param values
     * @return
     */
    public static ConfigObject fromMap(Map<String, ? extends Object> values) {
        return fromMap(values, null);
    }

    /**
     * See the other overload of fromIterable() for details, this one just uses
     * a default origin description.
     *
     * @param values
     * @return
     */
    public static ConfigList fromIterable(Collection<? extends Object> values) {
        return fromIterable(values, null);
    }

    private static String getUnits(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (!Character.isLetter(c))
                break;
            i -= 1;
        }
        return s.substring(i + 1);
    }

    /**
     * Parses a duration string. If no units are specified in the string, it is
     * assumed to be in milliseconds. The returned duration is in nanoseconds.
     * The purpose of this function is to implement the duration-related methods
     * in the ConfigObject interface.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return duration in nanoseconds
     * @throws ConfigException
     *             if string is invalid
     */
    public static long parseDuration(String input,
            ConfigOrigin originForException, String pathForException) {
        String s = ConfigUtil.unicodeTrim(input);
        String originalUnitString = getUnits(s);
        String unitString = originalUnitString;
        String numberString = ConfigUtil.unicodeTrim(s.substring(0, s.length()
                - unitString.length()));
        TimeUnit units = null;

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length() == 0)
            throw new ConfigException.BadValue(originForException,
                    pathForException, "No number in duration value '" + input
                            + "'");

        if (unitString.length() > 2 && !unitString.endsWith("s"))
            unitString = unitString + "s";

        // note that this is deliberately case-sensitive
        if (unitString.equals("") || unitString.equals("ms")
                || unitString.equals("milliseconds")) {
            units = TimeUnit.MILLISECONDS;
        } else if (unitString.equals("us") || unitString.equals("microseconds")) {
            units = TimeUnit.MICROSECONDS;
        } else if (unitString.equals("ns") || unitString.equals("nanoseconds")) {
            units = TimeUnit.NANOSECONDS;
        } else if (unitString.equals("d") || unitString.equals("days")) {
            units = TimeUnit.DAYS;
        } else if (unitString.equals("h") || unitString.equals("hours")) {
            units = TimeUnit.HOURS;
        } else if (unitString.equals("s") || unitString.equals("seconds")) {
            units = TimeUnit.SECONDS;
        } else if (unitString.equals("m") || unitString.equals("minutes")) {
            units = TimeUnit.MINUTES;
        } else {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse time unit '"
                            + originalUnitString
                            + "' (try ns, us, ms, s, m, d)");
        }

        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return units.toNanos(Long.parseLong(numberString));
            } else {
                long nanosInUnit = units.toNanos(1);
                return (long) (Double.parseDouble(numberString) * nanosInUnit);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse duration number '"
                            + numberString + "'");
        }
    }

    private static enum MemoryUnit {
        BYTES(1), KILOBYTES(1024), MEGABYTES(1024 * 1024), GIGABYTES(
                1024 * 1024 * 1024), TERABYTES(1024 * 1024 * 1024 * 1024);

        int bytes;

        MemoryUnit(int bytes) {
            this.bytes = bytes;
        }
    }

    /**
     * Parses a memory-size string. If no units are specified in the string, it
     * is assumed to be in bytes. The returned value is in bytes. The purpose of
     * this function is to implement the memory-size-related methods in the
     * ConfigObject interface. The units parsed are interpreted as powers of
     * two, that is, the convention for memory rather than the convention for
     * disk space.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return size in bytes
     * @throws ConfigException
     *             if string is invalid
     */
    public static long parseMemorySizeInBytes(String input,
            ConfigOrigin originForException, String pathForException) {
        String s = ConfigUtil.unicodeTrim(input);
        String unitStringMaybePlural = getUnits(s);
        String unitString;
        if (unitStringMaybePlural.endsWith("s"))
            unitString = unitStringMaybePlural.substring(0,
                    unitStringMaybePlural.length() - 1);
        else
            unitString = unitStringMaybePlural;
        String unitStringLower = unitString.toLowerCase();
        String numberString = ConfigUtil.unicodeTrim(s.substring(0, s.length()
                - unitStringMaybePlural.length()));

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length() == 0)
            throw new ConfigException.BadValue(originForException,
                    pathForException, "No number in size-in-bytes value '"
                            + input + "'");

        MemoryUnit units = null;

        // the short abbreviations are case-insensitive but you can't write the
        // long form words in all caps.
        if (unitString.equals("") || unitStringLower.equals("b")
                || unitString.equals("byte")) {
            units = MemoryUnit.BYTES;
        } else if (unitStringLower.equals("k") || unitString.equals("kilobyte")) {
            units = MemoryUnit.KILOBYTES;
        } else if (unitStringLower.equals("m") || unitString.equals("megabyte")) {
            units = MemoryUnit.MEGABYTES;
        } else if (unitStringLower.equals("g") || unitString.equals("gigabyte")) {
            units = MemoryUnit.GIGABYTES;
        } else if (unitStringLower.equals("t") || unitString.equals("terabyte")) {
            units = MemoryUnit.TERABYTES;
        } else {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse size unit '"
                            + unitStringMaybePlural + "' (try b, k, m, g, t)");
        }

        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return Long.parseLong(numberString) * units.bytes;
            } else {
                return (long) (Double.parseDouble(numberString) * units.bytes);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse memory size number '"
                            + numberString + "'");
        }
    }
}
