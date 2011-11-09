package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import java.io.Reader
import java.io.StringReader
import com.typesafe.config._
import java.util.HashMap

class ConfParserTest extends TestUtils {

    def parse(s: String): ConfigValue = {
        Parser.parse(SyntaxFlavor.CONF, new SimpleConfigOrigin("test conf string"), s, includer())
    }

    private def addOffendingJsonToException[R](parserName: String, s: String)(body: => R) = {
        try {
            body
        } catch {
            case t: Throwable =>
                throw new AssertionError(parserName + " parser did wrong thing on '" + s + "'", t)
        }
    }

    @Test
    def invalidConfThrows(): Unit = {
        // be sure we throw
        for (invalid <- whitespaceVariations(invalidConf)) {
            addOffendingJsonToException("config", invalid.test) {
                intercept[ConfigException] {
                    parse(invalid.test)
                }
            }
        }
    }

    @Test
    def validConfWorks(): Unit = {
        // all we're checking here unfortunately is that it doesn't throw.
        // for a more thorough check, use the EquivalentsTest stuff.
        for (valid <- whitespaceVariations(validConf)) {
            val ourAST = addOffendingJsonToException("config-conf", valid.test) {
                parse(valid.test)
            }
        }
    }
}
