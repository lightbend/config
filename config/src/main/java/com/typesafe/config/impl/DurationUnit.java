package com.typesafe.config.impl;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * https://github.com/typesafehub/config/blob/master/HOCON.md#duration-format
 */
public enum DurationUnit {


    NANOSECONDS(TimeUnit.NANOSECONDS, Arrays.asList("ns", "nano", "nanos", "nanosecond", "nanoseconds")),
    MICROSECONDS(TimeUnit.MICROSECONDS, Arrays.asList("us", "micro", "micros", "microsecond", "microseconds")),
    MILLISECONDS(TimeUnit.MILLISECONDS,Arrays.asList("ms", "milli", "millis", "millisecond", "milliseconds")),
    SECONDS(TimeUnit.SECONDS,Arrays.asList("s", "second", "seconds")),
    MINUTES(TimeUnit.MINUTES,Arrays.asList("m", "minute", "minutes")),
    HOURS(TimeUnit.HOURS,Arrays.asList("h", "hour", "hours")),
    DAYS(TimeUnit.DAYS,Arrays.asList("d", "day", "days"));

    private List<String> aliases;
    private TimeUnit timeUnit;

    DurationUnit(TimeUnit timeUnit,List<String> aliases) {
        this.timeUnit = timeUnit;
        this.aliases = aliases;
    }

    /**
     * Finds corresponding duration unit by its string representation.
     * @param rawVal - string duration value ('s','ns' etc.)
     * @return one of enum values or null if none applicable
     */
    public static final DurationUnit fromString(String rawVal) {
        for (DurationUnit durationUnit : DurationUnit.values()) {
            // note that this is deliberately case-sensitive
            if(durationUnit.aliases.contains(rawVal)) {
                return durationUnit;
            }
        }
        return null;
    }

    /**
     * Checks whether given string contains duration token.
     * @param rawVal - string like '10s','45nanos' etc.
     *               String is case sensitive, i.e. '10S' is not treated as valid duration string
     * @return true - if string contains one of enum aliases, false otherwise
     */
    public static boolean containsDurationToken(String rawVal) {
        String unitStr = rawVal.replaceAll("[^A-Za-z]","");
        return fromString(unitStr) != null;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
