package com.typesafe.config;

import java.util.concurrent.TimeUnit;

import com.typesafe.config.impl.ConfigFactory;

public final class Config {
    public static ConfigObject load(ConfigConfig configConfig) {
        return ConfigFactory.loadConfig(configConfig);
    }

    public static ConfigObject load(String rootPath) {
        return ConfigFactory.loadConfig(new ConfigConfig(rootPath, null));
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
        String s = input.trim();
        String unitString = getUnits(s);
        String numberString = s.substring(0, s.length() - unitString.length()).trim();
        TimeUnit units = null;

        // note that this is deliberately case-sensitive
        if (unitString == "" || unitString == "ms" || unitString == "milliseconds") {
            units = TimeUnit.MILLISECONDS;
        } else if (unitString == "us" || unitString == "microseconds") {
            units = TimeUnit.MICROSECONDS;
        } else if (unitString == "ns" || unitString == "nanoseconds") {
            units = TimeUnit.NANOSECONDS;
        } else if (unitString == "d" || unitString == "days") {
            units = TimeUnit.DAYS;
        } else if (unitString == "s" || unitString == "seconds") {
            units = TimeUnit.SECONDS;
        } else if (unitString == "m" || unitString == "minutes") {
            units = TimeUnit.MINUTES;
        } else {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse time unit '"
                            + unitString + "' (try ns, us, ms, s, m, d)");
        }

        try {
            // if the string is purely digits, parse as an integer to avoid possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return units.toNanos(Long.parseLong(numberString));
            } else {
                long nanosInUnit = units.toNanos(1);
                return (new Double(Double.parseDouble(numberString) * nanosInUnit)).longValue();
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
     * is assumed to be in bytes. The returned value is in bytes.
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
        String s = input.trim();
        String unitString = getUnits(s);
        String unitStringLower = unitString.toLowerCase();
        String numberString = s.substring(0, s.length() - unitString.length())
                .trim();
        MemoryUnit units = null;

        // the short abbreviations are case-insensitive but you can't write the
        // long form words in all caps.
        if (unitString == "" || unitStringLower == "b"
                || unitString == "bytes") {
            units = MemoryUnit.BYTES;
        } else if (unitStringLower == "k" || unitString == "kilobytes") {
            units = MemoryUnit.KILOBYTES;
        } else if (unitStringLower == "m" || unitString == "megabytes") {
            units = MemoryUnit.MEGABYTES;
        } else if (unitStringLower == "g" || unitString == "gigabytes") {
            units = MemoryUnit.GIGABYTES;
        } else {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse size unit '"
                            + unitString + "' (try b, k, m, g)");
        }

        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return Long.parseLong(numberString) * units.bytes;
            } else {
                return (new Double(Double.parseDouble(numberString)
                        * units.bytes)).longValue();
            }
        } catch (NumberFormatException e) {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse memory size number '"
                            + numberString
                            + "'");
        }
    }
}
