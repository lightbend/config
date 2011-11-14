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

    /**
     * Like load() but does not resolve the object, so you can go ahead and add
     * more fallbacks and stuff and have them seen by substitutions when you do
     * call {@link ConfigRoot.resolve()}.
     *
     * @param rootPath
     * @return
     */
    public static ConfigRoot loadWithoutResolving(String rootPath) {
        ConfigRoot system = systemPropertiesRoot(rootPath);

        ConfigValue mainFiles = parse(rootPath, ConfigParseOptions.defaults());
        ConfigValue referenceFiles = parse(rootPath + ".reference",
                ConfigParseOptions.defaults());

        return system.withFallbacks(mainFiles, referenceFiles);
    }

    public static ConfigRoot emptyRoot(String rootPath) {
        return ConfigImpl.emptyRoot(rootPath);
    }

    public static ConfigObject empty() {
        return ConfigImpl.empty();
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

    public static ConfigObject parse(Properties properties,
            ConfigParseOptions options) {
        return ConfigImpl.parse(properties,
                options.withFallbackOriginDescription("properties"));
    }

    public static ConfigObject parse(Reader reader, ConfigParseOptions options) {
        Parseable p = Parseable.newReader(reader);
        return ConfigImpl.parse(p,
                options.withFallbackOriginDescription("Reader"));
    }

    public static ConfigObject parse(URL url, ConfigParseOptions options) {
        Parseable p = Parseable.newURL(url);
        return ConfigImpl.parse(p,
                options.withFallbackOriginDescription(url.toExternalForm()));
    }

    public static ConfigObject parse(File file, ConfigParseOptions options) {
        Parseable p = Parseable.newFile(file);
        return ConfigImpl.parse(p,
                options.withFallbackOriginDescription(file.getPath()));
    }

    public static ConfigObject parse(Class<?> klass, String resource,
            ConfigParseOptions options) {
        Parseable p = Parseable.newResource(klass, resource);
        return ConfigImpl.parse(p,
                options.withFallbackOriginDescription(resource));
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

    public static ConfigValue fromAnyRef(Object object) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public static ConfigObject fromMap(Map<String, ? extends Object> values) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public static ConfigList fromCollection(Collection<? extends Object> values) {
        throw new UnsupportedOperationException("not implemented yet");
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
        if (unitString.equals("") || unitString.equals("ms") || unitString.equals("milliseconds")) {
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
            // if the string is purely digits, parse as an integer to avoid possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return units.toNanos(Long.parseLong(numberString));
            } else {
                long nanosInUnit = units.toNanos(1);
                return (long) (Double.parseDouble(numberString) * nanosInUnit);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException.BadValue(originForException, pathForException,
 "Could not parse duration number '"
                            + numberString + "'");
        }
    }

    private static enum MemoryUnit {
        BYTES(1), KILOBYTES(1024), MEGABYTES(1024 * 1024), GIGABYTES(
                1024 * 1024 * 1024);

        int bytes;
        MemoryUnit(int bytes) {
            this.bytes = bytes;
        }
    }

    /**
     * Parses a memory-size string. If no units are specified in the string, it
     * is assumed to be in bytes. The returned value is in bytes. The purpose of
     * this function is to implement the memory-size-related methods in the
     * ConfigObject interface.
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
    public static long parseMemorySize(String input,
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
        } else {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse size unit '"
                            + unitStringMaybePlural + "' (try b, k, m, g)");
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
                            + numberString
                            + "'");
        }
    }
}
