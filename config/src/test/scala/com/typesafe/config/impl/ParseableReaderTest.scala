package com.typesafe.config.impl

import java.io.InputStreamReader

import com.typesafe.config.{ ConfigException, ConfigFactory, ConfigParseOptions }
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.{ assertEquals, assertThat }
import org.junit.Test

class ParseableReaderTest extends TestUtils {

    @Test
    def parse(): Unit = {
        val filename = "/test01.properties"
        val configInput = new InputStreamReader(getClass.getResourceAsStream(filename))
        val config = ConfigFactory.parseReader(configInput, ConfigParseOptions.defaults()
            .setSyntaxFromFilename(filename))
        assertEquals("hello^^", config.getString("fromProps.specialChars"))
    }

    @Test
    def parseIncorrectFormat(): Unit = {
        val filename = "/test01.properties"
        val configInput = new InputStreamReader(getClass.getResourceAsStream(filename))
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.parseReader(configInput)
        }
        assertThat(e.getMessage, containsString("Expecting end of input or a comma, got '^'"))
    }
}
