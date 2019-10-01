/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import scala.collection.JavaConverters._
import com.typesafe.config._
import java.util.{ Collections, TimeZone, TreeSet }
import java.io.File
import scala.collection.mutable
import equiv03.SomethingInEquiv03
import java.io.StringReader
import java.net.URL
import java.time.Duration

class PublicApiTest extends TestUtils {

    @Before
    def before(): Unit = {
        // TimeZone.getDefault internally invokes System.setProperty("user.timezone", <default time zone>) and it may
        // cause flaky tests depending on tests order and jvm options. This method is invoked
        // eg. by URLConnection.getContentType (it reads headers and gets default time zone).
        TimeZone.getDefault
    }

    @Test
    def basicLoadAndGet() {
        val conf = ConfigFactory.load("test01")

        val a = conf.getInt("ints.fortyTwo")
        val child = conf.getConfig("ints")
        val c = child.getInt("fortyTwo")
        val ms = conf.getMilliseconds("durations.halfSecond")

        // should have used system variables
        if (System.getenv("HOME") != null)
            assertEquals(System.getenv("HOME"), conf.getString("system.home"))

        assertEquals(System.getProperty("java.version"), conf.getString("system.javaversion"))
    }

    @Test
    def noSystemVariables() {
        // should not have used system variables
        val conf = ConfigFactory.parseResourcesAnySyntax(classOf[PublicApiTest], "/test01")
            .resolve(ConfigResolveOptions.noSystem())

        intercept[ConfigException.Missing] {
            conf.getString("system.home")
        }
        intercept[ConfigException.Missing] {
            conf.getString("system.javaversion")
        }
    }

    @Test
    def canLimitLoadToJson {
        val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON);
        val conf = ConfigFactory.load("test01", options, ConfigResolveOptions.defaults())

        assertEquals(1, conf.getInt("fromJson1"))
        intercept[ConfigException.Missing] {
            conf.getInt("ints.fortyTwo")
        }
    }

    @Test
    def canLimitLoadToProperties {
        val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES);
        val conf = ConfigFactory.load("test01", options, ConfigResolveOptions.defaults())

        assertEquals(1, conf.getInt("fromProps.one"))
        intercept[ConfigException.Missing] {
            conf.getInt("ints.fortyTwo")
        }
    }

    @Test
    def canLimitLoadToConf {
        val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF);
        val conf = ConfigFactory.load("test01", options, ConfigResolveOptions.defaults())

        assertEquals(42, conf.getInt("ints.fortyTwo"))
        intercept[ConfigException.Missing] {
            conf.getInt("fromJson1")
        }
        intercept[ConfigException.Missing] {
            conf.getInt("fromProps.one")
        }
    }

    @Test
    def emptyConfigs() {
        assertTrue(ConfigFactory.empty().isEmpty())
        assertEquals("empty config", ConfigFactory.empty().origin().description())
        assertTrue(ConfigFactory.empty("foo").isEmpty())
        assertEquals("foo", ConfigFactory.empty("foo").origin().description())
    }

    private val defaultValueDesc = "hardcoded value";

    private def testFromValue(expectedValue: ConfigValue, createFrom: AnyRef) {
        assertEquals(expectedValue, ConfigValueFactory.fromAnyRef(createFrom))

        assertEquals(expectedValue, ConfigValueFactory.fromAnyRef(createFrom, "foo"))

        // description is ignored for createFrom that is already a ConfigValue
        createFrom match {
            case c: ConfigValue =>
                assertEquals(c.origin().description(), ConfigValueFactory.fromAnyRef(createFrom).origin().description())
            case _ =>
                assertEquals(defaultValueDesc, ConfigValueFactory.fromAnyRef(createFrom).origin().description())
                assertEquals("foo", ConfigValueFactory.fromAnyRef(createFrom, "foo").origin().description())
        }
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

        assertEquals("hardcoded value", ConfigValueFactory.fromMap(Map("a" -> 1, "b" -> 2, "c" -> 3).asJava).origin().description())
        assertEquals("foo", ConfigValueFactory.fromMap(Map("a" -> 1, "b" -> 2, "c" -> 3).asJava, "foo").origin().description())
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
        assertEquals(new SimpleConfigList(fakeOrigin(), aListValue), ConfigValueFactory.fromIterable(List(1, 2, 3).asJava))
        assertEquals(new SimpleConfigList(fakeOrigin(), aListValue), ConfigValueFactory.fromIterable(treeSet))

        assertEquals("hardcoded value", ConfigValueFactory.fromIterable(List(1, 2, 3).asJava).origin().description())
        assertEquals("foo", ConfigValueFactory.fromIterable(treeSet, "foo").origin().description())
    }

    @Test
    def fromConfigMemorySize() {
        testFromValue(longValue(1024), ConfigMemorySize.ofBytes(1024));
        testFromValue(longValue(512), ConfigMemorySize.ofBytes(512));
    }

    @Test
    def fromDuration() {
        testFromValue(longValue(1000), Duration.ofMillis(1000));
        testFromValue(longValue(1000 * 60 * 60 * 24), Duration.ofDays(1));
    }

    @Test
    def fromExistingConfigValue() {
        testFromValue(longValue(1000), longValue(1000));
        testFromValue(stringValue("foo"), stringValue("foo"));

        val aMapValue = new SimpleConfigObject(fakeOrigin(),
            Map("a" -> 1, "b" -> 2, "c" -> 3).mapValues(intValue(_): AbstractConfigValue).asJava)

        testFromValue(aMapValue, aMapValue)
    }

    @Test
    def fromExistingJavaListOfConfigValue() {
        // you can mix "unwrapped" List with ConfigValue elements
        val list = List(longValue(1), longValue(2), longValue(3)).asJava
        testFromValue(new SimpleConfigList(fakeOrigin(), List(longValue(1): AbstractConfigValue,
            longValue(2): AbstractConfigValue,
            longValue(3): AbstractConfigValue).asJava),
            list);
    }

    @Test
    def roundTripUnwrap() {
        val conf = ConfigFactory.load("test01")
        assertTrue(conf.root.size > 4) // "has a lot of stuff in it"
        val unwrapped = conf.root.unwrapped()
        val rewrapped = ConfigValueFactory.fromMap(unwrapped, conf.origin().description())
        val reunwrapped = rewrapped.unwrapped()
        assertEquals(conf.root, rewrapped)
        assertEquals(reunwrapped, unwrapped)
    }

    private def testFromPathMap(expectedValue: ConfigObject, createFrom: java.util.Map[String, Object]) {
        assertEquals(expectedValue, ConfigFactory.parseMap(createFrom).root)
        assertEquals(defaultValueDesc, ConfigFactory.parseMap(createFrom).origin().description())
        assertEquals(expectedValue, ConfigFactory.parseMap(createFrom, "foo").root)
        assertEquals("foo", ConfigFactory.parseMap(createFrom, "foo").origin().description())
    }

    @Test
    def fromJavaPathMap() {
        // first the same tests as with fromMap, but use parseMap
        val emptyMapValue = Collections.emptyMap[String, AbstractConfigValue]
        val aMapValue = Map("a" -> 1, "b" -> 2, "c" -> 3).mapValues(intValue(_): AbstractConfigValue).asJava
        testFromPathMap(new SimpleConfigObject(fakeOrigin(), emptyMapValue),
            Collections.emptyMap[String, Object])
        testFromPathMap(new SimpleConfigObject(fakeOrigin(), aMapValue),
            Map("a" -> 1, "b" -> 2, "c" -> 3).asInstanceOf[Map[String, AnyRef]].asJava)

        assertEquals("hardcoded value", ConfigFactory.parseMap(Map("a" -> 1, "b" -> 2, "c" -> 3).asJava).origin().description())
        assertEquals("foo", ConfigFactory.parseMap(Map("a" -> 1, "b" -> 2, "c" -> 3).asJava, "foo").origin().description())

        // now some tests with paths; be sure to test nested path maps
        val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
        val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

        val conf = ConfigFactory.parseMap(pathMapValue)

        assertEquals(2, conf.root.size)
        assertEquals(4, conf.getInt("b.x.y"))
        assertEquals(5, conf.getInt("b.z"))
        assertEquals(1, conf.getInt("a.c"))
    }

    @Test
    def brokenPathMap() {
        // "a" is both number 1 and an object
        val pathMapValue = Map("a" -> 1, "a.b" -> 2).asInstanceOf[Map[String, AnyRef]].asJava
        intercept[ConfigException.BugOrBroken] {
            ConfigFactory.parseMap(pathMapValue)
        }
    }

    @Test
    def defaultParseOptions() {
        val d = ConfigParseOptions.defaults()
        assertEquals(true, d.getAllowMissing())
        assertNull(d.getIncluder())
        assertNull(d.getOriginDescription())
        assertNull(d.getSyntax())
    }

    private def assertNotFound(e: ConfigException) {
        assertTrue("Message text: " + e.getMessage, e.getMessage.contains("No such") ||
            e.getMessage.contains("not found") ||
            e.getMessage.contains("were found") ||
            e.getMessage.contains("java.io.FileNotFoundException"))
    }

    @Test
    def allowMissing() {
        val e = intercept[ConfigException.IO] {
            ConfigFactory.parseFile(resourceFile("nonexistent.conf"), ConfigParseOptions.defaults().setAllowMissing(false))
        }
        assertNotFound(e)

        val conf = ConfigFactory.parseFile(resourceFile("nonexistent.conf"), ConfigParseOptions.defaults().setAllowMissing(true))
        assertTrue("is empty", conf.isEmpty())
    }

    @Test
    def allowMissingFileAnySyntax() {
        val e = intercept[ConfigException.IO] {
            ConfigFactory.parseFileAnySyntax(resourceFile("nonexistent"), ConfigParseOptions.defaults().setAllowMissing(false))
        }
        assertNotFound(e)

        val conf = ConfigFactory.parseFileAnySyntax(resourceFile("nonexistent"), ConfigParseOptions.defaults().setAllowMissing(true))
        assertTrue("is empty", conf.isEmpty())
    }

    @Test
    def allowMissingResourcesAnySyntax() {
        val e = intercept[ConfigException.IO] {
            ConfigFactory.parseResourcesAnySyntax(classOf[PublicApiTest], "nonexistent", ConfigParseOptions.defaults().setAllowMissing(false))
        }
        assertNotFound(e)

        val conf = ConfigFactory.parseResourcesAnySyntax(classOf[PublicApiTest], "nonexistent", ConfigParseOptions.defaults().setAllowMissing(true))
        assertTrue("is empty", conf.isEmpty())
    }

    @Test
    def includesCanBeMissingThoughFileCannot() {
        // test03.conf contains some nonexistent includes. check that
        // setAllowMissing on the file (which is not missing) doesn't
        // change that the includes are allowed to be missing.
        // This can break because some options might "propagate" through
        // to includes, but we don't want them all to do so.
        val conf = ConfigFactory.parseFile(resourceFile("test03.conf"), ConfigParseOptions.defaults().setAllowMissing(false))
        assertEquals(42, conf.getInt("test01.booleans"))

        val conf2 = ConfigFactory.parseFile(resourceFile("test03.conf"), ConfigParseOptions.defaults().setAllowMissing(true))
        assertEquals(conf, conf2)
    }

    sealed trait IncludeKind
    case object IncludeKindHeuristic extends IncludeKind;
    case object IncludeKindFile extends IncludeKind;
    case object IncludeKindURL extends IncludeKind;
    case object IncludeKindClasspath extends IncludeKind;

    case class Included(name: String, fallback: ConfigIncluder, kind: IncludeKind)

    class RecordingIncluder(val fallback: ConfigIncluder, val included: mutable.ListBuffer[Included]) extends ConfigIncluder {
        override def include(context: ConfigIncludeContext, name: String): ConfigObject = {
            included += Included(name, fallback, IncludeKindHeuristic)
            fallback.include(context, name)
        }

        override def withFallback(fallback: ConfigIncluder) = {
            if (this.fallback == fallback) {
                this;
            } else if (this.fallback == null) {
                new RecordingIncluder(fallback, included);
            } else {
                new RecordingIncluder(this.fallback.withFallback(fallback), included)
            }
        }
    }

    class RecordingFullIncluder(fallback: ConfigIncluder, included: mutable.ListBuffer[Included])
        extends RecordingIncluder(fallback, included)
        with ConfigIncluderFile with ConfigIncluderURL with ConfigIncluderClasspath {
        override def includeFile(context: ConfigIncludeContext, file: File) = {
            included += Included("file(" + file.getName() + ")", fallback, IncludeKindFile)
            fallback.asInstanceOf[ConfigIncluderFile].includeFile(context, file)
        }

        override def includeURL(context: ConfigIncludeContext, url: URL) = {
            included += Included("url(" + url.toExternalForm() + ")", fallback, IncludeKindURL)
            fallback.asInstanceOf[ConfigIncluderURL].includeURL(context, url)
        }

        override def includeResources(context: ConfigIncludeContext, name: String) = {
            included += Included("classpath(" + name + ")", fallback, IncludeKindFile)
            fallback.asInstanceOf[ConfigIncluderClasspath].includeResources(context, name)
        }

        override def withFallback(fallback: ConfigIncluder) = {
            if (this.fallback == fallback) {
                this;
            } else if (this.fallback == null) {
                new RecordingFullIncluder(fallback, included);
            } else {
                new RecordingFullIncluder(this.fallback.withFallback(fallback), included)
            }
        }
    }

    private def whatWasIncluded(parser: ConfigParseOptions => Config): List[Included] = {
        val included = mutable.ListBuffer[Included]()
        val includer = new RecordingIncluder(null, included)

        val conf = parser(ConfigParseOptions.defaults().setIncluder(includer).setAllowMissing(false))

        included.toList
    }

    private def whatWasIncludedFull(parser: ConfigParseOptions => Config): List[Included] = {
        val included = mutable.ListBuffer[Included]()
        val includer = new RecordingFullIncluder(null, included)

        val conf = parser(ConfigParseOptions.defaults().setIncluder(includer).setAllowMissing(false))

        included.toList
    }

    @Test
    def includersAreUsedWithFiles() {
        val included = whatWasIncluded(ConfigFactory.parseFile(resourceFile("test03.conf"), _))

        assertEquals(List("test01", "test02.conf", "equiv01/original.json",
            "nothere", "nothere.conf", "nothere.json", "nothere.properties",
            "test03-included.conf", "test03-included.conf"),
            included.map(_.name))
    }

    @Test
    def includersAreUsedRecursivelyWithFiles() {
        // includes.conf has recursive includes in it
        val included = whatWasIncluded(ConfigFactory.parseFile(resourceFile("equiv03/includes.conf"), _))

        assertEquals(List("letters/a.conf", "numbers/1.conf", "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))
    }

    @Test
    def includersAreUsedRecursivelyWithString() {
        val included = whatWasIncluded(ConfigFactory.parseString(""" include "equiv03/includes.conf" """, _))

        assertEquals(List("equiv03/includes.conf", "letters/a.conf", "numbers/1.conf", "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))
    }

    // full includer should only be used with the file(), url(), classpath() syntax.
    @Test
    def fullIncluderNotUsedWithoutNewSyntax() {
        val included = whatWasIncluded(ConfigFactory.parseFile(resourceFile("equiv03/includes.conf"), _))

        assertEquals(List("letters/a.conf", "numbers/1.conf", "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))

        val includedFull = whatWasIncludedFull(ConfigFactory.parseFile(resourceFile("equiv03/includes.conf"), _))
        assertEquals(included, includedFull)
    }

    @Test
    def includersAreUsedWithClasspath() {
        val included = whatWasIncluded(ConfigFactory.parseResources(classOf[PublicApiTest], "/test03.conf", _))

        assertEquals(List("test01", "test02.conf", "equiv01/original.json",
            "nothere", "nothere.conf", "nothere.json", "nothere.properties",
            "test03-included.conf", "test03-included.conf"),
            included.map(_.name))
    }

    @Test
    def includersAreUsedRecursivelyWithClasspath() {
        // includes.conf has recursive includes in it; here we look it up
        // with an "absolute" class path resource.
        val included = whatWasIncluded(ConfigFactory.parseResources(classOf[PublicApiTest], "/equiv03/includes.conf", _))

        assertEquals(List("letters/a.conf", "numbers/1.conf", "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))
    }

    @Test
    def includersAreUsedRecursivelyWithClasspathRelativeResource() {
        // includes.conf has recursive includes in it; here we look it up
        // with a "class-relative" class path resource
        val included = whatWasIncluded(ConfigFactory.parseResources(classOf[SomethingInEquiv03], "includes.conf", _))

        assertEquals(List("letters/a.conf", "numbers/1.conf", "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))
    }

    @Test
    def includersAreUsedRecursivelyWithURL() {
        // includes.conf has recursive includes in it; here we look it up
        // with a URL
        val included = whatWasIncluded(ConfigFactory.parseURL(resourceFile("/equiv03/includes.conf").toURI.toURL, _))

        assertEquals(List("letters/a.conf", "numbers/1.conf", "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))
    }

    @Test
    def fullIncluderUsed() {
        val included = whatWasIncludedFull(ConfigFactory.parseString("""
                    include "equiv03/includes.conf"
                    include file("nonexistent")
                    include url("file:/nonexistent")
                    include classpath("nonexistent")
                """, _))
        assertEquals(List("equiv03/includes.conf", "letters/a.conf", "numbers/1.conf",
            "numbers/2", "letters/b.json", "letters/c", "root/foo.conf",
            "file(nonexistent)", "url(file:/nonexistent)", "classpath(nonexistent)"),
            included.map(_.name))
    }

    @Test
    def nonFullIncluderSurvivesNewStyleIncludes() {
        val included = whatWasIncluded(ConfigFactory.parseString("""
                    include "equiv03/includes.conf"
                    include file("nonexistent")
                    include url("file:/nonexistent")
                    include classpath("nonexistent")
                """, _))
        assertEquals(List("equiv03/includes.conf", "letters/a.conf", "numbers/1.conf",
            "numbers/2", "letters/b.json", "letters/c", "root/foo.conf"),
            included.map(_.name))
    }

    @Test
    def stringParsing() {
        val conf = ConfigFactory.parseString("{ a : b }", ConfigParseOptions.defaults())
        assertEquals("b", conf.getString("a"))
    }

    @Test
    def readerParsing() {
        val conf = ConfigFactory.parseReader(new StringReader("{ a : b }"), ConfigParseOptions.defaults())
        assertEquals("b", conf.getString("a"))
    }

    @Test
    def anySyntax() {
        // test01 has all three syntaxes; first load with basename
        val conf = ConfigFactory.parseFileAnySyntax(resourceFile("test01"), ConfigParseOptions.defaults())
        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertEquals("A", conf.getString("fromJsonA"))
        assertEquals("true", conf.getString("fromProps.bool"))

        // now include a suffix, should only load one of them
        val onlyProps = ConfigFactory.parseFileAnySyntax(resourceFile("test01.properties"), ConfigParseOptions.defaults())
        assertFalse(onlyProps.hasPath("ints.fortyTwo"))
        assertFalse(onlyProps.hasPath("fromJsonA"))
        assertEquals("true", onlyProps.getString("fromProps.bool"))

        // force only one syntax via options
        val onlyPropsViaOptions = ConfigFactory.parseFileAnySyntax(resourceFile("test01.properties"),
            ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES))
        assertFalse(onlyPropsViaOptions.hasPath("ints.fortyTwo"))
        assertFalse(onlyPropsViaOptions.hasPath("fromJsonA"))
        assertEquals("true", onlyPropsViaOptions.getString("fromProps.bool"))

        // make sure it works with resources too
        val fromResources = ConfigFactory.parseResourcesAnySyntax(classOf[PublicApiTest], "/test01",
            ConfigParseOptions.defaults())
        assertEquals(42, fromResources.getInt("ints.fortyTwo"))
        assertEquals("A", fromResources.getString("fromJsonA"))
        assertEquals("true", fromResources.getString("fromProps.bool"))
    }

    @Test
    def resourceFromAnotherClasspath() {
        val conf = ConfigFactory.parseResources(classOf[PublicApiTest], "/test-lib.conf", ConfigParseOptions.defaults())

        assertEquals("This is to test classpath searches.", conf.getString("test-lib.description"))
    }

    @Test
    def multipleResourcesUsed() {
        val conf = ConfigFactory.parseResources(classOf[PublicApiTest], "/test01.conf")

        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertEquals(true, conf.getBoolean("test-lib.fromTestLib"))

        // check that each value has its own ConfigOrigin
        val v1 = conf.getValue("ints.fortyTwo")
        val v2 = conf.getValue("test-lib.fromTestLib")
        assertEquals("v1 has right origin resource", "test01.conf", v1.origin.resource)
        assertEquals("v2 has right origin resource", "test01.conf", v2.origin.resource)
        assertEquals(v1.origin.resource, v2.origin.resource)
        assertFalse("same urls in " + v1.origin + " " + v2.origin, v1.origin.url == v2.origin.url)
        assertFalse(v1.origin.filename == v2.origin.filename)
    }

    @Test
    def splitAndJoinPath() {
        // the actual join-path logic should be tested OK in the non-public-API tests,
        // this is just to test the public wrappers.

        assertEquals("\"\".a.b.\"$\"", ConfigUtil.joinPath("", "a", "b", "$"))
        assertEquals("\"\".a.b.\"$\"", ConfigUtil.joinPath(Seq("", "a", "b", "$").asJava))
        assertEquals(Seq("", "a", "b", "$"), ConfigUtil.splitPath("\"\".a.b.\"$\"").asScala)

        // invalid stuff throws
        intercept[ConfigException] {
            ConfigUtil.splitPath("$")
        }
        intercept[ConfigException] {
            ConfigUtil.joinPath()
        }
        intercept[ConfigException] {
            ConfigUtil.joinPath(Collections.emptyList[String]())
        }
    }

    @Test
    def quoteString() {
        // the actual quote logic should be tested OK in the non-public-API tests,
        // this is just to test the public wrapper.

        assertEquals("\"\"", ConfigUtil.quoteString(""))
        assertEquals("\"a\"", ConfigUtil.quoteString("a"))
        assertEquals("\"\\n\"", ConfigUtil.quoteString("\n"))
    }

    @Test
    def usesContextClassLoaderForReferenceConf() {
        val loaderA1 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("reference.conf" -> resourceFile("a_1.conf").toURI.toURL()))
        val loaderB2 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("reference.conf" -> resourceFile("b_2.conf").toURI.toURL()))

        val configA1 = withContextClassLoader(loaderA1) {
            ConfigFactory.load()
        }
        assertEquals(1, configA1.getInt("a"))
        assertFalse("no b", configA1.hasPath("b"))

        val configB2 = withContextClassLoader(loaderB2) {
            ConfigFactory.load()
        }
        assertEquals(2, configB2.getInt("b"))
        assertFalse("no a", configB2.hasPath("a"))

        val configPlain = ConfigFactory.load()
        assertFalse("no a", configPlain.hasPath("a"))
        assertFalse("no b", configPlain.hasPath("b"))
    }

    @Test
    def supportsConfigLoadingStrategyAlteration(): Unit = {
        assertEquals("config.strategy is not set", null, System.getProperty("config.strategy"))
        System.setProperty("config.strategy", classOf[TestStrategy].getCanonicalName)

        try {
            val incovationsBeforeTest = TestStrategy.getIncovations()
            val loaderA1 = new TestClassLoader(this.getClass().getClassLoader(),
                Map("reference.conf" -> resourceFile("a_1.conf").toURI.toURL()))

            val configA1 = withContextClassLoader(loaderA1) {
                ConfigFactory.load()
            }
            ConfigFactory.load()
            assertEquals(1, configA1.getInt("a"))
            assertEquals(2, TestStrategy.getIncovations() - incovationsBeforeTest)
        } finally {
            System.clearProperty("config.strategy")
        }
    }

    @Test
    def loadEnvironmentVariablesOverridesIfConfigured(): Unit = {
        assertEquals("config.override_with_env_vars is not set", null, System.getProperty("config.override_with_env_vars"))

        System.setProperty("config.override_with_env_vars", "true")

        try {
            val loaderB2 = new TestClassLoader(this.getClass().getClassLoader(),
                Map("reference.conf" -> resourceFile("b_2.conf").toURI.toURL()))

            val configB2 = withContextClassLoader(loaderB2) {
                ConfigFactory.load()
            }

            assertEquals(5, configB2.getInt("b"))
        } finally {
            System.clearProperty("config.override_with_env_vars")
        }
    }

    @Test
    def usesContextClassLoaderForApplicationConf() {
        val loaderA1 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("application.conf" -> resourceFile("a_1.conf").toURI.toURL()))
        val loaderB2 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("application.conf" -> resourceFile("b_2.conf").toURI.toURL()))

        val configA1 = withContextClassLoader(loaderA1) {
            ConfigFactory.load()
        }
        assertEquals(1, configA1.getInt("a"))
        assertFalse("no b", configA1.hasPath("b"))

        val configB2 = withContextClassLoader(loaderB2) {
            ConfigFactory.load()
        }
        assertEquals(2, configB2.getInt("b"))
        assertFalse("no a", configB2.hasPath("a"))

        val configPlain = ConfigFactory.load()
        assertFalse("no a", configPlain.hasPath("a"))
        assertFalse("no b", configPlain.hasPath("b"))
    }

    @Test
    def usesSuppliedClassLoaderForReferenceConf() {
        val loaderA1 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("reference.conf" -> resourceFile("a_1.conf").toURI.toURL()))
        val loaderB2 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("reference.conf" -> resourceFile("b_2.conf").toURI.toURL()))

        val configA1 = ConfigFactory.load(loaderA1)

        assertEquals(1, configA1.getInt("a"))
        assertFalse("no b", configA1.hasPath("b"))

        val configB2 = ConfigFactory.load(loaderB2)

        assertEquals(2, configB2.getInt("b"))
        assertFalse("no a", configB2.hasPath("a"))

        val configPlain = ConfigFactory.load()
        assertFalse("no a", configPlain.hasPath("a"))
        assertFalse("no b", configPlain.hasPath("b"))

        // check the various overloads that take a loader parameter
        for (
            c <- Seq(ConfigFactory.parseResources(loaderA1, "reference.conf"),
                ConfigFactory.parseResourcesAnySyntax(loaderA1, "reference"),
                ConfigFactory.parseResources(loaderA1, "reference.conf", ConfigParseOptions.defaults()),
                ConfigFactory.parseResourcesAnySyntax(loaderA1, "reference", ConfigParseOptions.defaults()),
                ConfigFactory.load(loaderA1, "application"),
                ConfigFactory.load(loaderA1, "application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
                ConfigFactory.load(loaderA1, "application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
                ConfigFactory.load(loaderA1, ConfigFactory.parseString("")),
                ConfigFactory.load(loaderA1, ConfigFactory.parseString(""), ConfigResolveOptions.defaults()),
                ConfigFactory.defaultReference(loaderA1))
        ) {
            assertEquals(1, c.getInt("a"))
            assertFalse("no b", c.hasPath("b"))
        }

        // check providing the loader via ConfigParseOptions
        val withLoader = ConfigParseOptions.defaults().setClassLoader(loaderA1);
        for (
            c <- Seq(ConfigFactory.parseResources("reference.conf", withLoader),
                ConfigFactory.parseResourcesAnySyntax("reference", withLoader),
                ConfigFactory.load("application", withLoader, ConfigResolveOptions.defaults()))
        ) {
            assertEquals(1, c.getInt("a"))
            assertFalse("no b", c.hasPath("b"))
        }

        // check not providing the loader
        for (
            c <- Seq(ConfigFactory.parseResources("reference.conf"),
                ConfigFactory.parseResourcesAnySyntax("reference"),
                ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults()),
                ConfigFactory.parseResourcesAnySyntax("reference", ConfigParseOptions.defaults()),
                ConfigFactory.load("application"),
                ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
                ConfigFactory.load(ConfigFactory.parseString("")),
                ConfigFactory.load(ConfigFactory.parseString(""), ConfigResolveOptions.defaults()),
                ConfigFactory.defaultReference())
        ) {
            assertFalse("no a", c.hasPath("a"))
            assertFalse("no b", c.hasPath("b"))
        }

        // check providing the loader via current context
        withContextClassLoader(loaderA1) {
            for (
                c <- Seq(ConfigFactory.parseResources("reference.conf"),
                    ConfigFactory.parseResourcesAnySyntax("reference"),
                    ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults()),
                    ConfigFactory.parseResourcesAnySyntax("reference", ConfigParseOptions.defaults()),
                    ConfigFactory.load("application"),
                    ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
                    ConfigFactory.load(ConfigFactory.parseString("")),
                    ConfigFactory.load(ConfigFactory.parseString(""), ConfigResolveOptions.defaults()),
                    ConfigFactory.defaultReference())
            ) {
                assertEquals(1, c.getInt("a"))
                assertFalse("no b", c.hasPath("b"))
            }
        }
    }

    @Test
    def usesSuppliedClassLoaderForApplicationConf() {
        val loaderA1 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("application.conf" -> resourceFile("a_1.conf").toURI.toURL()))
        val loaderB2 = new TestClassLoader(this.getClass().getClassLoader(),
            Map("application.conf" -> resourceFile("b_2.conf").toURI.toURL()))

        val configA1 = ConfigFactory.load(loaderA1)

        assertEquals(1, configA1.getInt("a"))
        assertFalse("no b", configA1.hasPath("b"))

        val configB2 = ConfigFactory.load(loaderB2)

        assertEquals(2, configB2.getInt("b"))
        assertFalse("no a", configB2.hasPath("a"))

        val configPlain = ConfigFactory.load()
        assertFalse("no a", configPlain.hasPath("a"))
        assertFalse("no b", configPlain.hasPath("b"))

        // check the various overloads that take a loader parameter
        for (
            c <- Seq(ConfigFactory.parseResources(loaderA1, "application.conf"),
                ConfigFactory.parseResourcesAnySyntax(loaderA1, "application"),
                ConfigFactory.parseResources(loaderA1, "application.conf", ConfigParseOptions.defaults()),
                ConfigFactory.parseResourcesAnySyntax(loaderA1, "application", ConfigParseOptions.defaults()),
                ConfigFactory.load(loaderA1, "application"),
                ConfigFactory.load(loaderA1, "application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
                ConfigFactory.defaultApplication(loaderA1))
        ) {
            assertEquals(1, c.getInt("a"))
            assertFalse("no b", c.hasPath("b"))
        }

        // check providing the loader via ConfigParseOptions
        val withLoader = ConfigParseOptions.defaults().setClassLoader(loaderA1);
        for (
            c <- Seq(ConfigFactory.parseResources("application.conf", withLoader),
                ConfigFactory.parseResourcesAnySyntax("application", withLoader),
                ConfigFactory.defaultApplication(withLoader),
                ConfigFactory.load(withLoader, ConfigResolveOptions.defaults()),
                ConfigFactory.load("application", withLoader, ConfigResolveOptions.defaults()))
        ) {
            assertEquals(1, c.getInt("a"))
            assertFalse("no b", c.hasPath("b"))
        }

        // check not providing the loader
        for (
            c <- Seq(ConfigFactory.parseResources("application.conf"),
                ConfigFactory.parseResourcesAnySyntax("application"),
                ConfigFactory.parseResources("application.conf", ConfigParseOptions.defaults()),
                ConfigFactory.parseResourcesAnySyntax("application", ConfigParseOptions.defaults()),
                ConfigFactory.load("application"),
                ConfigFactory.defaultApplication(),
                ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()))
        ) {
            assertFalse("no a", c.hasPath("a"))
            assertFalse("no b", c.hasPath("b"))
        }

        // check providing the loader via current context
        withContextClassLoader(loaderA1) {
            for (
                c <- Seq(ConfigFactory.parseResources("application.conf"),
                    ConfigFactory.parseResourcesAnySyntax("application"),
                    ConfigFactory.parseResources("application.conf", ConfigParseOptions.defaults()),
                    ConfigFactory.parseResourcesAnySyntax("application", ConfigParseOptions.defaults()),
                    ConfigFactory.load("application"),
                    ConfigFactory.defaultApplication(),
                    ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()))
            ) {
                assertEquals(1, c.getInt("a"))
                assertFalse("no b", c.hasPath("b"))
            }
        }
    }

    @Test
    def cachedDefaultConfig() {
        val load1 = ConfigFactory.load()
        val load2 = ConfigFactory.load()
        assertTrue("load() was cached", load1 eq load2)
        assertEquals(load1, load2)

        // the other loader has to have some reference.conf or else we just get
        // back the system properties singleton which is not per-class-loader
        val otherLoader = new TestClassLoader(this.getClass().getClassLoader(),
            Map("reference.conf" -> resourceFile("a_1.conf").toURI.toURL()))
        val load3 = ConfigFactory.load(otherLoader)
        val load4 = ConfigFactory.load(otherLoader)
        assertTrue("different config for different classloaders", load1 ne load3)
        assertTrue("load(loader) was cached", load3 eq load4)
        assertEquals(load3, load4)

        val load5 = ConfigFactory.load()
        val load6 = ConfigFactory.load()
        assertTrue("load() was cached again", load5 eq load6)
        assertEquals(load5, load5)
        assertEquals(load1, load5)

        val load7 = ConfigFactory.load(otherLoader)
        assertTrue("cache was dropped when switching loaders", load3 ne load7)
        assertEquals(load3, load7)
    }

    @Test
    def cachedReferenceConfig() {
        val load1 = ConfigFactory.defaultReference()
        val load2 = ConfigFactory.defaultReference()
        assertTrue("defaultReference() was cached", load1 eq load2)
        assertEquals(load1, load2)

        // the other loader has to have some reference.conf or else we just get
        // back the system properties singleton which is not per-class-loader
        val otherLoader = new TestClassLoader(this.getClass().getClassLoader(),
            Map("reference.conf" -> resourceFile("a_1.conf").toURI.toURL()))
        val load3 = ConfigFactory.defaultReference(otherLoader)
        val load4 = ConfigFactory.defaultReference(otherLoader)
        assertTrue("different config for different classloaders", load1 ne load3)
        assertTrue("defaultReference(loader) was cached", load3 eq load4)
        assertEquals(load3, load4)

        val load5 = ConfigFactory.defaultReference()
        val load6 = ConfigFactory.defaultReference()
        assertTrue("defaultReference() was cached again", load5 eq load6)
        assertEquals(load5, load5)
        assertEquals(load1, load5)

        val load7 = ConfigFactory.defaultReference(otherLoader)
        assertTrue("cache was dropped when switching loaders", load3 ne load7)
        assertEquals(load3, load7)
    }

    @Test
    def detectIncludeCycle() {
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.load("cycle")
        }

        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("include statements nested"))
    }

    // We would ideally make this case NOT throw an exception but we need to do some work
    // to get there, see https://github.com/lightbend/config/issues/160
    @Test
    def detectIncludeFromList() {
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.load("include-from-list.conf")
        }

        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("limitation"))
    }

    @Test
    def missingOverrideResourceFails() {
        assertEquals("config.file is not set", null, System.getProperty("config.file"))
        val old = System.getProperty("config.resource")
        try {
            System.setProperty("config.resource", "donotexists.conf")
            intercept[ConfigException.IO] {
                ConfigFactory.load()
            }
        } finally {
            // cleanup properties
            Option(old).map { v =>
                System.setProperty("config.resource", v)
                v
            }.orElse {
                System.clearProperty("config.resource")
                None
            }
            assertEquals("config.resource restored", old, System.getProperty("config.resource"))
            ConfigImpl.reloadSystemPropertiesConfig()
        }
    }

    @Test
    def missingOverrideFileFails() {
        assertEquals("config.resource is not set", null, System.getProperty("config.resource"))
        val old = System.getProperty("config.file")
        try {
            System.setProperty("config.file", "donotexists.conf")
            intercept[ConfigException.IO] {
                ConfigFactory.load()
            }
        } finally {
            // cleanup properties
            Option(old).map { v =>
                System.setProperty("config.file", v)
                v
            }.orElse {
                System.clearProperty("config.file")
                None
            }
            assertEquals("config.file restored", old, System.getProperty("config.file"))
            ConfigImpl.reloadSystemPropertiesConfig()
        }
    }

    @Test
    def exceptionSerializable() {
        // ArrayList is a serialization problem so we want to cover it in tests
        val comments = new java.util.ArrayList(List("comment 1", "comment 2").asJava)
        val e = new ConfigException.WrongType(SimpleConfigOrigin.newSimple("an origin").withComments(comments),
            "this is a message", new RuntimeException("this is a cause"))
        val eCopy = checkSerializableNoMeaningfulEquals(e)
        assertTrue("messages equal after deserialize", e.getMessage.equals(eCopy.getMessage))
        assertTrue("cause messages equal after deserialize", e.getCause().getMessage.equals(eCopy.getCause().getMessage))
        assertTrue("origins equal after deserialize", e.origin().equals(eCopy.origin()))
    }

    @Test
    def exceptionSerializableWithNullOrigin() {
        val e = new ConfigException.Missing("this is a message", new RuntimeException("this is a cause"))
        assertTrue("origin null before serialize", e.origin() == null)
        val eCopy = checkSerializableNoMeaningfulEquals(e)
        assertTrue("messages equal after deserialize", e.getMessage.equals(eCopy.getMessage))
        assertTrue("cause messages equal after deserialize", e.getCause().getMessage.equals(eCopy.getCause().getMessage))
        assertTrue("origin null after deserialize", e.origin() == null)
    }

    @Test
    def exceptionSerializableWithWrongType() {
        val e = intercept[ConfigException.WrongType] {
            ConfigValueFactory.fromAnyRef(Map("item" -> "uhoh, fail").asJava) match {
                case o: ConfigObject => o.toConfig.getStringList("item")
            }
        }
        val eCopy = checkSerializableNoMeaningfulEquals(e)
        assertTrue("messages equal after deserialize", e.getMessage.equals(eCopy.getMessage))
    }

    @Test
    def invalidateCaches() {
        val conf0 = ConfigFactory.load()
        val sys0 = ConfigFactory.systemProperties()
        val conf1 = ConfigFactory.load()
        val sys1 = ConfigFactory.systemProperties()
        ConfigFactory.invalidateCaches()
        val conf2 = ConfigFactory.load()
        val sys2 = ConfigFactory.systemProperties()
        System.setProperty("invalidateCachesTest", "Hello!")
        ConfigFactory.invalidateCaches()
        val conf3 = ConfigFactory.load()
        val sys3 = ConfigFactory.systemProperties()
        val conf4 = ConfigFactory.load()
        val sys4 = ConfigFactory.systemProperties()
        System.clearProperty("invalidateCachesTest")

        assertTrue("stuff gets cached sys", sys0 eq sys1)
        assertTrue("stuff gets cached conf", conf0 eq conf1)

        assertTrue("test system property is not set sys", !sys0.hasPath("invalidateCachesTest"))
        assertTrue("test system property is not set conf", !conf0.hasPath("invalidateCachesTest"))

        assertTrue("invalidate caches works on unchanged system props sys", sys1 ne sys2)
        assertTrue("invalidate caches works on unchanged system props conf", conf1 ne conf2)

        assertTrue("invalidate caches works on changed system props sys", sys2 ne sys3)
        assertTrue("invalidate caches works on changed system props conf", conf2 ne conf3)

        assertEquals("invalidate caches doesn't change value if no system prop changes sys", sys1, sys2)
        assertEquals("invalidate caches doesn't change value if no system prop changes conf", conf1, conf2)

        assertTrue("test system property is set sys", sys3.hasPath("invalidateCachesTest"))
        assertTrue("test system property is set conf", conf3.hasPath("invalidateCachesTest"))

        assertTrue("invalidate caches DOES change value if system props changed sys", sys2 != sys3)
        assertTrue("invalidate caches DOES change value if system props changed conf", conf2 != conf3)

        assertTrue("stuff gets cached repeatedly sys", sys3 eq sys4)
        assertTrue("stuff gets cached repeatedly conf", conf3 eq conf4)
    }

    @Test
    def invalidateReferenceConfig(): Unit = {
        val orig = ConfigFactory.defaultReference()
        val cached = ConfigFactory.defaultReference()
        assertTrue("reference config was cached", orig eq cached)

        ConfigFactory.invalidateCaches()
        val changed = ConfigFactory.defaultReference()
        assertTrue("reference config was invalidated", orig ne changed)
    }

    @Test
    def invalidateFullConfig(): Unit = {
        val orig = ConfigFactory.load()
        val cached = ConfigFactory.load()
        assertTrue("full config was cached", orig eq cached)

        ConfigFactory.invalidateCaches()
        val changed = ConfigFactory.load()
        assertTrue("full config was invalidated", orig ne changed)
    }

    @Test
    def canUseSomeValuesWithoutResolving(): Unit = {
        val conf = ConfigFactory.parseString("a=42,b=${NOPE}")
        assertEquals(42, conf.getInt("a"))
        intercept[ConfigException.NotResolved] {
            conf.getInt("b")
        }
    }

    @Test
    def heuristicIncludeChecksClasspath(): Unit = {
        // from https://github.com/lightbend/config/issues/188
        withScratchDirectory("heuristicIncludeChecksClasspath") { dir =>
            val f = new File(dir, "foo.conf")
            writeFile(f, """
include "onclasspath"
""")
            val conf = ConfigFactory.parseFile(f)
            assertEquals(42, conf.getInt("onclasspath"))
        }
    }

    @Test
    def fileIncludeStatements(): Unit = {
        val file = resourceFile("file-include.conf")
        val conf = ConfigFactory.parseFile(file)
        assertEquals("got file-include.conf", 41, conf.getInt("base"))
        assertEquals("got subdir/foo.conf", 42, conf.getInt("foo"))
        assertEquals("got bar.conf", 43, conf.getInt("bar"))

        // these two do not work right now, because we do not
        // treat the filename as relative to the including file
        // if file() is specified, so `include file("bar-file.conf")`
        // fails.
        //assertEquals("got bar-file.conf", 44, conf.getInt("bar-file"))
        //assertEquals("got subdir/baz.conf", 45, conf.getInt("baz"))
        assertFalse("did not get bar-file.conf", conf.hasPath("bar-file"))
        assertFalse("did not get subdir/baz.conf", conf.hasPath("baz"))
    }

    @Test
    def hasPathOrNullWorks(): Unit = {
        val conf = ConfigFactory.parseString("x.a=null,x.b=42")
        assertFalse("hasPath says false for null", conf.hasPath("x.a"))
        assertTrue("hasPathOrNull says true for null", conf.hasPathOrNull("x.a"))

        assertTrue("hasPath says true for non-null", conf.hasPath("x.b"))
        assertTrue("hasPathOrNull says true for non-null", conf.hasPathOrNull("x.b"))

        assertFalse("hasPath says false for missing", conf.hasPath("x.c"))
        assertFalse("hasPathOrNull says false for missing", conf.hasPathOrNull("x.c"))

        // this is to be sure we handle a null along the path correctly
        assertFalse("hasPath says false for missing under null", conf.hasPath("x.a.y"))
        assertFalse("hasPathOrNull says false for missing under null", conf.hasPathOrNull("x.a.y"))

        // this is to be sure we handle missing along the path correctly
        assertFalse("hasPath says false for missing under missing", conf.hasPath("x.c.y"))
        assertFalse("hasPathOrNull says false for missing under missing", conf.hasPathOrNull("x.c.y"))
    }

    @Test
    def getIsNullWorks(): Unit = {
        val conf = ConfigFactory.parseString("x.a=null,x.b=42")

        assertTrue("getIsNull says true for null", conf.getIsNull("x.a"))
        assertFalse("getIsNull says false for non-null", conf.getIsNull("x.b"))
        intercept[ConfigException.Missing] { conf.getIsNull("x.c") }
        // missing underneath null
        intercept[ConfigException.Missing] { conf.getIsNull("x.a.y") }
        // missing underneath missing
        intercept[ConfigException.Missing] { conf.getIsNull("x.c.y") }
    }

    @Test
    def applicationConfCanOverrideReferenceConf(): Unit = {
        val loader = new TestClassLoader(this.getClass.getClassLoader,
            Map(
                "reference.conf" -> resourceFile("test13-reference-with-substitutions.conf").toURI.toURL,
                "application.conf" -> resourceFile("test13-application-override-substitutions.conf").toURI.toURL))

        assertEquals("b", ConfigFactory.defaultReference(loader).getString("a"))
        assertEquals("overridden", ConfigFactory.load(loader).getString("a"))
    }

    @Test(expected = classOf[ConfigException.UnresolvedSubstitution])
    def referenceConfMustResolveIndependently(): Unit = {
        val loader = new TestClassLoader(this.getClass.getClassLoader,
            Map(
                "reference.conf" -> resourceFile("test13-reference-bad-substitutions.conf").toURI.toURL,
                "application.conf" -> resourceFile("test13-application-override-substitutions.conf").toURI.toURL))

        ConfigFactory.load(loader)
    }

}

class TestStrategy extends DefaultConfigLoadingStrategy {
    override def parseApplicationConfig(parseOptions: ConfigParseOptions): Config = {
        TestStrategy.increment()
        super.parseApplicationConfig(parseOptions)
    }
}

object TestStrategy {
    private var invocations = 0
    def getIncovations() = invocations
    def increment() = invocations += 1
}
