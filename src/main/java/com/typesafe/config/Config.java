package com.typesafe.config;

import java.util.concurrent.TimeUnit;

import com.typesafe.config.impl.ConfigImpl;

public final class Config {
    public static ConfigObject load(ConfigConfig configConfig) {
        return ConfigImpl.loadConfig(configConfig);
    }

    public static ConfigObject load(String rootPath) {
        return ConfigImpl.loadConfig(new ConfigConfig(rootPath));
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
        String s = input.trim();
        String unitString = getUnits(s);
        String numberString = s.substring(0, s.length() - unitString.length()).trim();
        TimeUnit units = null;

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
        String s = input.trim();
        String unitStringMaybePlural = getUnits(s);
        String unitString;
        if (unitStringMaybePlural.endsWith("s"))
            unitString = unitStringMaybePlural.substring(0,
                    unitStringMaybePlural.length() - 1);
        else
            unitString = unitStringMaybePlural;
        String unitStringLower = unitString.toLowerCase();
        String numberString = s.substring(0,
                s.length() - unitStringMaybePlural.length())
                .trim();
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
