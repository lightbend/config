/**
 *   Copyright (C) 2011-2026 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

public class UnicodeEscapeParsingTest {

    private static void assertParseError(String input) {
        try {
            ConfigFactory.parseString(input);
            fail("expected a parse error for " + input);
        } catch (ConfigException.Parse e) {
            assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().contains("Malformed hex digits"));
        }
    }

    @Test
    public void rejectsSignAndNonAsciiDigitsInUnicodeEscapes() {
        assertParseError("a = \"\\u+041\"");
        assertParseError("a = \"\\u-041\"");
        assertParseError("a = \"\\u\uFF10\uFF10\uFF14\uFF11\"");
        assertParseError("a = \"\\u\u0660\u0660\u0664\u0661\"");
    }

    @Test
    public void acceptsFourAsciiHexDigits() {
        assertEquals("A", ConfigFactory.parseString("a = \"\\u0041\"").getString("a"));
    }
}
