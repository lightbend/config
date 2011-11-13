package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._

class UtilTest extends TestUtils {
    private lazy val supplementaryChars = {
        val sb = new java.lang.StringBuilder()
        val codepoints = Seq(
            0x2070E, 0x20731, 0x20779, 0x20C53, 0x20C78,
            0x20C96, 0x20CCF, 0x20CD5, 0x20D15, 0x20D7C)
        for (c <- codepoints) {
            sb.appendCodePoint(c)
        }
        assertTrue(sb.length() > codepoints.length)
        sb.toString()
    }

    @Test
    def unicodeTrimSupplementaryChars() {
        assertEquals("", ConfigUtil.unicodeTrim(""))
        assertEquals("a", ConfigUtil.unicodeTrim("a"))
        assertEquals("abc", ConfigUtil.unicodeTrim("abc"))
        assertEquals(supplementaryChars, ConfigUtil.unicodeTrim(supplementaryChars))

        val s = " \u00A0 \n  " + supplementaryChars + "  \n  \u00A0 "
        val asciiTrimmed = s.trim()
        val unitrimmed = ConfigUtil.unicodeTrim(s)

        assertFalse(asciiTrimmed.equals(unitrimmed))
        assertEquals(supplementaryChars, unitrimmed)
    }

    @Test
    def definitionOfWhitespace() {
        assertTrue(ConfigUtil.isWhitespace(' '))
        assertTrue(ConfigUtil.isWhitespace('\n'))
        // these three are nonbreaking spaces
        assertTrue(ConfigUtil.isWhitespace('\u00A0'))
        assertTrue(ConfigUtil.isWhitespace('\u2007'))
        assertTrue(ConfigUtil.isWhitespace('\u202F'))
        // vertical tab, a weird one
        assertTrue(ConfigUtil.isWhitespace('\u000B'))
        // file separator, another weird one
        assertTrue(ConfigUtil.isWhitespace('\u001C'))
    }

    @Test
    def equalsThatHandlesNull() {
        assertTrue(ConfigUtil.equalsHandlingNull(null, null))
        assertFalse(ConfigUtil.equalsHandlingNull(new Object(), null))
        assertFalse(ConfigUtil.equalsHandlingNull(null, new Object()))
        assertTrue(ConfigUtil.equalsHandlingNull("", ""))
    }
}
