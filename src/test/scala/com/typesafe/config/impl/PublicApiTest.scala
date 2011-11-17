package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import scala.collection.JavaConverters._
import com.typesafe.config._
import java.util.Collections
import java.util.TreeSet
import java.io.File

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
        assertEquals("empty config", Config.empty().origin().description())
        assertEquals(0, Config.empty("foo").size())
        assertEquals("foo", Config.empty("foo").origin().description())
        assertEquals(0, Config.emptyRoot("foo.bar").size())
        assertEquals("foo.bar", Config.emptyRoot("foo.bar").origin().description())
    }

    private val defaultValueDesc = "hardcoded value";

    private def testFromValue(expectedValue: ConfigValue, createFrom: AnyRef) {
        assertEquals(expectedValue, Config.fromAnyRef(createFrom))
        assertEquals(defaultValueDesc, Config.fromAnyRef(createFrom).origin().description())
        assertEquals(expectedValue, Config.fromAnyRef(createFrom, "foo"))
        assertEquals("foo", Config.fromAnyRef(createFrom, "foo").origin().description())
    }

    @Test
    def fromJavaBoolean() {
        testFromValue(boolValue(true), true: java.lang.Boolean)
        testFromValue(boolValue(false), false: java.lang.Boolean)
    }

    @Test
    def fromJavaNull() {
        testFromValue(nullValue, null);
    }

    @Test
    def fromJavaNumbers() {
        testFromValue(intValue(5), 5: java.lang.Integer)
        testFromValue(longValue(6), 6: java.lang.Long)
        testFromValue(doubleValue(3.14), 3.14: java.lang.Double)

        class WeirdNumber(v: Double) extends java.lang.Number {
            override def doubleValue = v
            override def intValue = v.intValue
            override def longValue = v.longValue
            override def floatValue = v.floatValue
        }

        val weirdNumber = new WeirdNumber(5.1);
        testFromValue(doubleValue(5.1), weirdNumber)
    }

    @Test
    def fromJavaString() {
        testFromValue(stringValue("hello world"), "hello world")
    }

    @Test
    def fromJavaMap() {
        val emptyMapValue = Collections.emptyMap[String, AbstractConfigValue]
        val aMapValue = Map("a" -> 1, "b" -> 2, "c" -> 3).mapValues(intValue(_): AbstractConfigValue).asJava
        testFromValue(new SimpleConfigObject(fakeOrigin(), emptyMapValue), Collections.emptyMap[String, Int])
        testFromValue(new SimpleConfigObject(fakeOrigin(), aMapValue), Map("a" -> 1, "b" -> 2, "c" -> 3).asJava)

        assertEquals("hardcoded value", Config.fromMap(Map("a" -> 1, "b" -> 2, "c" -> 3).asJava).origin().description())
        assertEquals("foo", Config.fromMap(Map("a" -> 1, "b" -> 2, "c" -> 3).asJava, "foo").origin().description())
    }

    @Test
    def fromJavaCollection() {
        val emptyListValue = Collections.emptyList[AbstractConfigValue]
        val aListValue = List(1, 2, 3).map(intValue(_): AbstractConfigValue).asJava

        testFromValue(new SimpleConfigList(fakeOrigin(), emptyListValue), Nil.asJava)
        testFromValue(new SimpleConfigList(fakeOrigin(), aListValue), List(1, 2, 3).asJava)

        // test with a non-List (but has to be ordered)
        val treeSet = new TreeSet[Int]();
        treeSet.add(1)
        treeSet.add(2)
        treeSet.add(3)

        testFromValue(new SimpleConfigList(fakeOrigin(), emptyListValue), Set.empty[String].asJava)
        testFromValue(new SimpleConfigList(fakeOrigin(), aListValue), treeSet)

        // testFromValue doesn't test the fromIterable public wrapper around fromAnyRef,
        // do so here.
        assertEquals(new SimpleConfigList(fakeOrigin(), aListValue), Config.fromIterable(List(1, 2, 3).asJava))
        assertEquals(new SimpleConfigList(fakeOrigin(), aListValue), Config.fromIterable(treeSet))

        assertEquals("hardcoded value", Config.fromIterable(List(1, 2, 3).asJava).origin().description())
        assertEquals("foo", Config.fromIterable(treeSet, "foo").origin().description())
    }

    @Test
    def roundTripUnwrap() {
        val conf = Config.load("test01")
        assertTrue(conf.size() > 4) // "has a lot of stuff in it"
        val unwrapped = conf.unwrapped()
        val rewrapped = Config.fromMap(unwrapped, conf.origin().description())
        val reunwrapped = rewrapped.unwrapped()
        assertEquals(conf, rewrapped)
        assertEquals(reunwrapped, unwrapped)
    }

    private def resource(filename: String) = {
        val resourceDir = new File("src/test/resources")
        if (!resourceDir.exists())
            throw new RuntimeException("This test can only be run from the project's root directory")
        new File(resourceDir, filename)
    }

    @Test
    def defaultParseOptions() {
        val d = ConfigParseOptions.defaults()
        assertEquals(true, d.getAllowMissing())
        assertNull(d.getIncluder())
        assertNull(d.getOriginDescription())
        assertNull(d.getSyntax())
    }

    @Test
    def allowMissing() {
        val e = intercept[ConfigException.IO] {
            Config.parse(resource("nonexistent.conf"), ConfigParseOptions.defaults().setAllowMissing(false))
        }
        assertTrue(e.getMessage.contains("No such"))

        val conf = Config.parse(resource("nonexistent.conf"), ConfigParseOptions.defaults().setAllowMissing(true))
        assertTrue(conf.isEmpty())
    }

    @Test
    def includesCanBeMissingThoughFileCannot() {
        // test03.conf contains some nonexistent includes. check that
        // setAllowMissing on the file (which is not missing) doesn't
        // change that the includes are allowed to be missing.
        // This can break because some options might "propagate" through
        // to includes, but we don't want them all to do so.
        val conf = Config.parse(resource("test03.conf"), ConfigParseOptions.defaults().setAllowMissing(false))
        assertEquals(42, conf.getInt("test01.booleans"))

        val conf2 = Config.parse(resource("test03.conf"), ConfigParseOptions.defaults().setAllowMissing(true))
        assertEquals(conf, conf2)
    }
}
