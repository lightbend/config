package com.typesafe.config.impl;

import com.typesafe.config.*;
import org.junit.Test;

public class NumberBoundaryTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Test
    public void preservesPositiveLongBoundary() {
        Config c = ConfigFactory.parseString("high=9223372036854775808.0\ninside=9223372036854774784.0\nlow=-9223372036854775808.0\nmax=9223372036854775807");
        check(c.getNumber("high") instanceof Double, "2^63 must not saturate to Long.MAX_VALUE");
        check(c.getDouble("high") == 0x1.0p63, "2^63 value");
        check(c.getLong("inside") == 9223372036854774784L, "inside long range");
        check(c.getLong("low") == Long.MIN_VALUE, "inclusive lower bound");
        check(!c.getValue("high").equals(c.getValue("max")), "distinct boundary numbers equal");
        check(!c.getValue("max").equals(c.getValue("high")), "equality must be symmetric");
        ConfigValue max = ConfigValueFactory.fromAnyRef(Long.MAX_VALUE);
        check(max.equals(c.getValue("max")), "actual long max remains whole");
        ConfigValue one = ConfigValueFactory.fromAnyRef(1.0);
        ConfigValue integer = ConfigValueFactory.fromAnyRef(1);
        check(one.equals(integer) && integer.equals(one), "cross-type integral equality");
        check(one.hashCode() == integer.hashCode(), "equal numbers share hash");

    }
}
