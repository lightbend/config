package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigException
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._

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
    def test01() {
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
        // FIXME need to add a way to get a null
        //assertEquals(null, conf.getAny("nulls.null"))

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

        // plain getList should work
        assertEquals(Seq(intValue(1), intValue(2), intValue(3)), conf.getList("arrays.ofInt").asScala)
        assertEquals(Seq(stringValue("a"), stringValue("b"), stringValue("c")), conf.getList("arrays.ofString").asScala)

        // should throw Missing if key doesn't exist
        intercept[ConfigException.Missing] {
            conf.getInt("doesnotexist")
        }

        // should throw Null if key is null
        intercept[ConfigException.Null] {
            conf.getInt("nulls.null")
        }

        // should throw WrongType if key is wrong type and not convertible
        intercept[ConfigException.WrongType] {
            conf.getInt("booleans.trueAgain")
        }

        // should convert numbers to string
        assertEquals("42", conf.getString("ints.fortyTwo"))
        assertEquals("42.1", conf.getString("floats.fortyTwoPointOne"))

        // should convert string to number
        assertEquals(57, conf.getInt("strings.number"))

        // should get durations
        def asNanos(secs: Int) = TimeUnit.SECONDS.toNanos(secs)
        assertEquals(1000L, conf.getMilliseconds("durations.second"))
        assertEquals(asNanos(1), conf.getNanoseconds("durations.second"))
        assertEquals(Seq(1000L, 2000L, 3000L),
            conf.getMillisecondsList("durations.secondsList").asScala)
        assertEquals(Seq(asNanos(1), asNanos(2), asNanos(3)),
            conf.getNanosecondsList("durations.secondsList").asScala)

        // should get size in bytes
        assertEquals(1024 * 1024L, conf.getMemorySize("memsizes.meg"))
        assertEquals(Seq(1024 * 1024L, 1024 * 1024L),
            conf.getMemorySizeList("memsizes.megsList").asScala)

        // should have loaded stuff from .json
        assertEquals(1, conf.getInt("fromJson1"))
        assertEquals("A", conf.getString("fromJsonA"))

        // should have loaded stuff from .properties
        assertEquals("abc", conf.getString("fromProps.abc"))
        assertEquals(1, conf.getInt("fromProps.one"))
        assertEquals(true, conf.getBoolean("fromProps.bool"))

        // toString() on conf objects doesn't throw (toString is just a debug string so not testing its result)
        conf.toString()
    }
}
