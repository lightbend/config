package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import scala.collection.JavaConverters._
import com.typesafe.config._

class PublicApiTest extends TestUtils {
    @Test
    def basicLoadAndGet() {
        val conf = Config.load("test01")

        val a = conf.getInt("ints.fortyTwo")
        val obj = conf.getObject("ints")
        val c = obj.getInt("fortyTwo")
        val ms = conf.getMilliseconds("durations.halfSecond")

        // should have used system variables
        if (System.getenv("HOME") != null)
            assertEquals(System.getenv("HOME"), conf.getString("system.home"))

        assertEquals(System.getProperty("java.version"), conf.getString("system.javaversion"))
    }

    @Test
    def noSystemVariables() {
        // should not have used system variables
        val conf = Config.load("test01", ConfigParseOptions.defaults(),
            ConfigResolveOptions.noSystem())

        intercept[ConfigException.Null] {
            conf.getString("system.home")
        }
        intercept[ConfigException.Null] {
            conf.getString("system.javaversion")
        }
    }

    @Test
    def canLimitLoadToJson {
        val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON);
        val conf = Config.load("test01", options, ConfigResolveOptions.defaults())

        assertEquals(1, conf.getInt("fromJson1"))
        intercept[ConfigException.Missing] {
            conf.getInt("ints.fortyTwo")
        }
    }

    @Test
    def canLimitLoadToProperties {
        val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES);
        val conf = Config.load("test01", options, ConfigResolveOptions.defaults())

        assertEquals(1, conf.getInt("fromProps.one"))
        intercept[ConfigException.Missing] {
            conf.getInt("ints.fortyTwo")
        }
    }

    @Test
    def canLimitLoadToConf {
        val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF);
        val conf = Config.load("test01", options, ConfigResolveOptions.defaults())

        assertEquals(42, conf.getInt("ints.fortyTwo"))
        intercept[ConfigException.Missing] {
            conf.getInt("fromJson1")
        }
        intercept[ConfigException.Missing] {
            conf.getInt("fromProps.one")
        }
    }

    @Test
    def emptyObjects() {
        assertEquals(0, Config.empty().size())
        assertEquals(0, Config.empty("foo").size())
        assertEquals("foo", Config.empty("foo").origin().description())
        assertEquals(0, Config.emptyRoot("foo.bar").size())
        assertEquals("foo.bar", Config.emptyRoot("foo.bar").origin().description())
    }
}
