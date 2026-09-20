package com.typesafe.config.impl;

import com.typesafe.config.*;
import org.junit.Test;

public class IntegerOverflowTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Test
    public void preservesOverflowLiteralNumberType() {
        Config c = ConfigFactory.parseString("v=9223372036854775808\nnegative=-9223372036854777856\nmax=9223372036854775807\ntext=123abc\nquoted=\"9223372036854775808\"\nconcat=9223372036854775808 suffix");
        check(c.getValue("v").valueType() == ConfigValueType.NUMBER, "overflow integer became string");
        check(c.getDouble("v") == 0x1.0p63, "positive numeric value");
        check(c.getDouble("negative") == -9223372036854777856.0, "negative numeric value");
        check(c.getNumber("max") instanceof Long, "in-range integers remain long");
        check(c.getString("text").equals("123abc"), "number/string concatenation");
        check(c.getValue("quoted").valueType() == ConfigValueType.STRING, "quoted number remains string");
        check(c.getString("concat").equals("9223372036854775808 suffix"), "original numeric spelling");
        Config json = ConfigFactory.parseString("{\"v\":9223372036854775808}", ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON));
        check(json.getDouble("v") == 0x1.0p63, "JSON numeric value");

    }
    @Test
    public void beyondFiniteDoubleRangeRetainsLegacyString() {
        String huge = new String(new char[310]).replace('\0', '9');
        for (String literal : new String[] {huge, "-" + huge}) {
            Config config = ConfigFactory.parseString("n=" + literal);
            check(config.getValue("n").valueType() == ConfigValueType.STRING,
                    "integer outside finite double range retains legacy string type");
            check(config.getString("n").equals(literal), "original digits retained");
            String json = config.root().render(ConfigRenderOptions.concise());
            Config reparsed = ConfigFactory.parseString(json,
                    ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON));
            check(reparsed.root().equals(config.root()), "rendered JSON remains parseable and equivalent");
        }
    }

}
