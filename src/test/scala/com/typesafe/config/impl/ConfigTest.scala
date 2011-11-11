package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigException
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigConfig

class ConfigTest extends TestUtils {

    @Test
    def mergeTrivial() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "b" : 2 }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(2, merged.getInt("b"))
        assertEquals(2, merged.keySet().size)
    }

    @Test
    def mergeEmpty() {
        val merged = AbstractConfigObject.merge(fakeOrigin(), List[AbstractConfigObject]().asJava, null)

        assertEquals(0, merged.keySet().size)
    }

    @Test
    def mergeOne() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)
    }

    @Test
    def mergeOverride() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = AbstractConfigObject.merge(fakeOrigin(), List(obj2, obj1).asJava, null)

        assertEquals(2, merged2.getInt("a"))
        assertEquals(1, merged2.keySet().size)
    }

    @Test
    def mergeN() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "b" : 2 }""")
        val obj3 = parseObject("""{ "c" : 3 }""")
        val obj4 = parseObject("""{ "d" : 4 }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2, obj3, obj4).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(2, merged.getInt("b"))
        assertEquals(3, merged.getInt("c"))
        assertEquals(4, merged.getInt("d"))
        assertEquals(4, merged.keySet().size)
    }

    @Test
    def mergeOverrideN() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val obj3 = parseObject("""{ "a" : 3 }""")
        val obj4 = parseObject("""{ "a" : 4 }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2, obj3, obj4).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = AbstractConfigObject.merge(fakeOrigin(), List(obj4, obj3, obj2, obj1).asJava, null)

        assertEquals(4, merged2.getInt("a"))
        assertEquals(1, merged2.keySet().size)
    }

    @Test
    def mergeNested() {
        val obj1 = parseObject("""{ "root" : { "a" : 1, "z" : 101 } }""")
        val obj2 = parseObject("""{ "root" : { "b" : 2, "z" : 102 } }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2).asJava, null)

        assertEquals(1, merged.getInt("root.a"))
        assertEquals(2, merged.getInt("root.b"))
        assertEquals(101, merged.getInt("root.z"))
        assertEquals(1, merged.keySet().size)
        assertEquals(3, merged.getObject("root").keySet().size)
    }

    @Test
    def mergeOverrideObjectAndPrimitive() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = AbstractConfigObject.merge(fakeOrigin(), List(obj2, obj1).asJava, null)

        assertEquals(42, merged2.getObject("a").getInt("b"))
        assertEquals(42, merged2.getInt("a.b"))
        assertEquals(1, merged2.keySet().size)
        assertEquals(1, merged2.getObject("a").keySet().size)
    }

    @Test
    def mergeObjectThenPrimitiveThenObject() {
        val obj1 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val obj3 = parseObject("""{ "a" : { "b" : 43 } }""")

        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2, obj3).asJava, null)

        assertEquals(42, merged.getInt("a.b"))
        assertEquals(1, merged.keySet().size)
        assertEquals(1, merged.getObject("a").keySet().size())
    }

    @Test
    def mergePrimitiveThenObjectThenPrimitive() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj3 = parseObject("""{ "a" : 3 }""")

        val merged = AbstractConfigObject.merge(fakeOrigin(), List(obj1, obj2, obj3).asJava, null)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)
    }

    @Test
    def test01Getting() {
        val conf = Config.load("test01")

        // get all the primitive types
        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertEquals(42, conf.getInt("ints.fortyTwoAgain"))
        assertEquals(42L, conf.getLong("ints.fortyTwoAgain"))
        assertEquals(42.1, conf.getDouble("floats.fortyTwoPointOne"), 1e-6)
        assertEquals(42.1, conf.getDouble("floats.fortyTwoPointOneAgain"), 1e-6)
        assertEquals("abcd", conf.getString("strings.abcd"))
        assertEquals("abcd", conf.getString("strings.abcdAgain"))
        assertEquals("null bar 42 baz true 3.14 hi", conf.getString("strings.concatenated"))
        assertEquals(true, conf.getBoolean("booleans.trueAgain"))
        assertEquals(false, conf.getBoolean("booleans.falseAgain"))

        // to get null we have to use the get() method from Map,
        // which takes a key and not a path
        assertEquals(nullValue(), conf.getObject("nulls").get("null"))
        assertNull(conf.get("notinthefile"))

        // get stuff with getValue
        assertEquals(intValue(42), conf.getValue("ints.fortyTwo"))
        assertEquals(stringValue("abcd"), conf.getValue("strings.abcd"))

        // get stuff with getAny
        assertEquals(42L, conf.getAny("ints.fortyTwo"))
        assertEquals("abcd", conf.getAny("strings.abcd"))
        assertEquals(false, conf.getAny("booleans.falseAgain"))

        // get empty array as any type of array
        assertEquals(Seq(), conf.getAnyList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getIntList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getLongList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getStringList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getLongList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getDoubleList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getObjectList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getBooleanList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getNumberList("arrays.empty").asScala)
        assertEquals(Seq(), conf.getList("arrays.empty").asScala)

        // get typed arrays
        assertEquals(Seq(1, 2, 3), conf.getIntList("arrays.ofInt").asScala)
        assertEquals(Seq(1L, 2L, 3L), conf.getLongList("arrays.ofInt").asScala)
        assertEquals(Seq("a", "b", "c"), conf.getStringList("arrays.ofString").asScala)
        assertEquals(Seq(3.14, 4.14, 5.14), conf.getDoubleList("arrays.ofDouble").asScala)
        assertEquals(Seq(null, null, null), conf.getAnyList("arrays.ofNull").asScala)
        assertEquals(Seq(true, false), conf.getBooleanList("arrays.ofBoolean").asScala)
        val listOfLists = conf.getAnyList("arrays.ofArray").asScala map { _.asInstanceOf[java.util.List[_]].asScala }
        assertEquals(Seq(Seq("a", "b", "c"), Seq("a", "b", "c"), Seq("a", "b", "c")), listOfLists)
        assertEquals(3, conf.getObjectList("arrays.ofObject").asScala.length)

        assertEquals(Seq("a", "b"), conf.getStringList("arrays.firstElementNotASubst").asScala)

        // plain getList should work
        assertEquals(Seq(intValue(1), intValue(2), intValue(3)), conf.getList("arrays.ofInt").asScala)
        assertEquals(Seq(stringValue("a"), stringValue("b"), stringValue("c")), conf.getList("arrays.ofString").asScala)
    }

    @Test
    def test01Exceptions() {
        val conf = Config.load("test01")

        // should throw Missing if key doesn't exist
        intercept[ConfigException.Missing] {
            conf.getInt("doesnotexist")
        }

        // should throw Null if key is null
        intercept[ConfigException.Null] {
            conf.getInt("nulls.null")
        }

        intercept[ConfigException.Null] {
            conf.getIntList("nulls.null")
        }

        intercept[ConfigException.Null] {
            conf.getMilliseconds("nulls.null")
        }

        intercept[ConfigException.Null] {
            conf.getNanoseconds("nulls.null")
        }

        intercept[ConfigException.Null] {
            conf.getMemorySize("nulls.null")
        }

        // should throw WrongType if key is wrong type and not convertible
        intercept[ConfigException.WrongType] {
            conf.getInt("booleans.trueAgain")
        }

        intercept[ConfigException.WrongType] {
            conf.getBooleanList("arrays.ofInt")
        }

        intercept[ConfigException.WrongType] {
            conf.getIntList("arrays.ofBoolean")
        }

        intercept[ConfigException.WrongType] {
            conf.getObjectList("arrays.ofInt")
        }

        intercept[ConfigException.WrongType] {
            conf.getMilliseconds("ints")
        }

        intercept[ConfigException.WrongType] {
            conf.getNanoseconds("ints")
        }

        intercept[ConfigException.WrongType] {
            conf.getMemorySize("ints")
        }

        // should throw BadPath on various bad paths
        intercept[ConfigException.BadPath] {
            conf.getInt(".bad")
        }

        intercept[ConfigException.BadPath] {
            conf.getInt("bad.")
        }

        intercept[ConfigException.BadPath] {
            conf.getInt("bad..bad")
        }

        // should throw BadValue on things that don't parse
        // as durations and sizes
        intercept[ConfigException.BadValue] {
            conf.getMilliseconds("strings.a")
        }

        intercept[ConfigException.BadValue] {
            conf.getNanoseconds("strings.a")
        }

        intercept[ConfigException.BadValue] {
            conf.getMemorySize("strings.a")
        }
    }

    @Test
    def test01Conversions() {
        val conf = Config.load("test01")

        // should convert numbers to string
        assertEquals("42", conf.getString("ints.fortyTwo"))
        assertEquals("42.1", conf.getString("floats.fortyTwoPointOne"))

        // should convert string to number
        assertEquals(57, conf.getInt("strings.number"))
        assertEquals(3.14, conf.getDouble("strings.double"), 1e-6)

        // should convert strings to boolean
        assertEquals(true, conf.getBoolean("strings.true"))
        assertEquals(true, conf.getBoolean("strings.yes"))
        assertEquals(false, conf.getBoolean("strings.false"))
        assertEquals(false, conf.getBoolean("strings.no"))

        // converting some random string to boolean fails though
        intercept[ConfigException.WrongType] {
            conf.getBoolean("strings.abcd")
        }

        // FIXME test convert string "null" to a null value

        // should not convert strings to object or list
        intercept[ConfigException.WrongType] {
            conf.getObject("strings.a")
        }

        intercept[ConfigException.WrongType] {
            conf.getList("strings.a")
        }

        // should not convert object or list to string
        intercept[ConfigException.WrongType] {
            conf.getString("ints")
        }

        intercept[ConfigException.WrongType] {
            conf.getString("arrays.ofInt")
        }

        // should get durations
        def asNanos(secs: Int) = TimeUnit.SECONDS.toNanos(secs)
        assertEquals(1000L, conf.getMilliseconds("durations.second"))
        assertEquals(asNanos(1), conf.getNanoseconds("durations.second"))
        assertEquals(1000L, conf.getMilliseconds("durations.secondAsNumber"))
        assertEquals(asNanos(1), conf.getNanoseconds("durations.secondAsNumber"))
        assertEquals(Seq(1000L, 2000L, 3000L, 4000L),
            conf.getMillisecondsList("durations.secondsList").asScala)
        assertEquals(Seq(asNanos(1), asNanos(2), asNanos(3), asNanos(4)),
            conf.getNanosecondsList("durations.secondsList").asScala)
        assertEquals(500L, conf.getMilliseconds("durations.halfSecond"))

        // should get size in bytes
        assertEquals(1024 * 1024L, conf.getMemorySize("memsizes.meg"))
        assertEquals(1024 * 1024L, conf.getMemorySize("memsizes.megAsNumber"))
        assertEquals(Seq(1024 * 1024L, 1024 * 1024L, 1024L * 1024L),
            conf.getMemorySizeList("memsizes.megsList").asScala)
        assertEquals(512 * 1024L, conf.getMemorySize("memsizes.halfMeg"))
    }

    @Test
    def test01MergingOtherFormats() {
        val conf = Config.load("test01")

        // should have loaded stuff from .json
        assertEquals(1, conf.getInt("fromJson1"))
        assertEquals("A", conf.getString("fromJsonA"))

        // should have loaded stuff from .properties
        assertEquals("abc", conf.getString("fromProps.abc"))
        assertEquals(1, conf.getInt("fromProps.one"))
        assertEquals(true, conf.getBoolean("fromProps.bool"))
    }

    @Test
    def test01ToString() {
        val conf = Config.load("test01")

        // toString() on conf objects doesn't throw (toString is just a debug string so not testing its result)
        conf.toString()
    }

    @Test
    def test01LoadWithConfigConfig() {
        val conf = Config.load(new ConfigConfig("test01"))
    }

    @Test
    def test02SubstitutionsWithWeirdPaths() {
        val conf = Config.load("test02")

        assertEquals(42, conf.getInt("42_a"))
        assertEquals(42, conf.getInt("42_b"))
        assertEquals(42, conf.getInt("42_c"))
        assertEquals(57, conf.getInt("57_a"))
        assertEquals(57, conf.getInt("57_b"))
        assertEquals(103, conf.getInt("103_a"))
    }

    @Test
    def test02UseWeirdPathsWithConfigObject() {
        val conf = Config.load("test02")

        // we're checking that the getters in ConfigObject support
        // these weird path expressions
        assertEquals(42, conf.getInt(""" "".""."" """))
        assertEquals(57, conf.getInt("a.b.c"))
        assertEquals(57, conf.getInt(""" "a"."b"."c" """))
        assertEquals(103, conf.getInt(""" "a.b.c" """))
    }
}
