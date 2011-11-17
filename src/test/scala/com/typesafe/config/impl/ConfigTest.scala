package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigException
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigResolveOptions
import java.io.File
import com.typesafe.config.ConfigParseOptions

class ConfigTest extends TestUtils {

    def merge(toMerge: AbstractConfigObject*) = {
        AbstractConfigObject.merge(toMerge.toList.asJava)
    }

    // In many cases, we expect merging to be associative. It is
    // not, however, when an object value is the first value,
    // a non-object value follows, and then an object after that;
    // in that case, if an association starts with the non-object
    // value, then the value after the non-object value gets lost.
    private def associativeMerge(allObjects: Seq[AbstractConfigObject])(assertions: AbstractConfigObject => Unit) {
        def m(toMerge: AbstractConfigObject*) = merge(toMerge: _*)

        def makeTrees(objects: Seq[AbstractConfigObject]): Iterator[AbstractConfigObject] = {
            objects.length match {
                case 0 => Iterator.empty
                case 1 => {
                    Iterator(objects(0))
                }
                case 2 => {
                    Iterator(m(objects(0), objects(1)))
                }
                case 3 => {
                    Seq(m(m(objects(0), objects(1)), objects(2)),
                        m(objects(0), m(objects(1), objects(2))),
                        m(objects(0), objects(1), objects(2))).iterator
                }
                case n => {
                    // obviously if n gets very high we will be sad ;-)
                    val trees = for {
                        i <- (1 until n)
                        val pair = objects.splitAt(i)
                        first <- makeTrees(pair._1)
                        second <- makeTrees(pair._2)
                    } yield m(first, second)
                    Iterator(m(objects: _*)) ++ trees.iterator
                }
            }
        }

        // the extra m(allObjects: _*) here is redundant but want to
        // be sure we do it first, and guard against makeTrees
        // being insane.
        val trees = Seq(m(allObjects: _*)) ++ makeTrees(allObjects)
        for (tree <- trees) {
            // if this fails, we were not associative.
            assertEquals(trees(0), tree)
        }

        for (tree <- trees) {
            assertions(tree)
        }
    }

    @Test
    def mergeTrivial() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "b" : 2 }""")
        val merged = merge(obj1, obj2)

        assertEquals(1, merged.getInt("a"))
        assertEquals(2, merged.getInt("b"))
        assertEquals(2, merged.keySet().size)
    }

    @Test
    def mergeEmpty() {
        val merged = merge()

        assertEquals(0, merged.keySet().size)
    }

    @Test
    def mergeOne() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val merged = merge(obj1)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)
    }

    @Test
    def mergeOverride() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val merged = merge(obj1, obj2)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = merge(obj2, obj1)

        assertEquals(2, merged2.getInt("a"))
        assertEquals(1, merged2.keySet().size)
    }

    @Test
    def mergeN() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "b" : 2 }""")
        val obj3 = parseObject("""{ "c" : 3 }""")
        val obj4 = parseObject("""{ "d" : 4 }""")
        val merged = merge(obj1, obj2, obj3, obj4)

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
        val merged = merge(obj1, obj2, obj3, obj4)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = merge(obj4, obj3, obj2, obj1)

        assertEquals(4, merged2.getInt("a"))
        assertEquals(1, merged2.keySet().size)
    }

    @Test
    def mergeNested() {
        val obj1 = parseObject("""{ "root" : { "a" : 1, "z" : 101 } }""")
        val obj2 = parseObject("""{ "root" : { "b" : 2, "z" : 102 } }""")
        val merged = merge(obj1, obj2)

        assertEquals(1, merged.getInt("root.a"))
        assertEquals(2, merged.getInt("root.b"))
        assertEquals(101, merged.getInt("root.z"))
        assertEquals(1, merged.keySet().size)
        assertEquals(3, merged.getObject("root").keySet().size)
    }

    @Test
    def mergeWithEmpty() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ }""")
        val merged = merge(obj1, obj2)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = merge(obj2, obj1)

        assertEquals(1, merged2.getInt("a"))
        assertEquals(1, merged2.keySet().size)
    }

    @Test
    def mergeOverrideObjectAndPrimitive() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val merged = merge(obj1, obj2)

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.keySet().size)

        val merged2 = merge(obj2, obj1)

        assertEquals(42, merged2.getObject("a").getInt("b"))
        assertEquals(42, merged2.getInt("a.b"))
        assertEquals(1, merged2.keySet().size)
        assertEquals(1, merged2.getObject("a").keySet().size)
    }

    @Test
    def mergeObjectThenPrimitiveThenObject() {
        // the semantic here is that the primitive gets ignored, because
        // it can't be merged with the object. But potentially it should
        // throw an exception even, or warn.
        val obj1 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val obj3 = parseObject("""{ "a" : { "b" : 43, "c" : 44 } }""")

        val merged = merge(obj1, obj2, obj3)
        assertEquals(42, merged.getInt("a.b"))
        assertEquals(1, merged.size)
        assertEquals(44, merged.getInt("a.c"))
        assertEquals(2, merged.getObject("a").size())
    }

    @Test
    def mergePrimitiveThenObjectThenPrimitive() {
        // the primitive should override the object
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj3 = parseObject("""{ "a" : 3 }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            assertEquals(1, merged.getInt("a"))
            assertEquals(1, merged.keySet().size)
        }
    }

    private def resolveNoSystem(v: AbstractConfigValue, root: AbstractConfigObject) = {
        SubstitutionResolver.resolve(v, root, ConfigResolveOptions.noSystem())
    }

    @Test
    def mergeSubstitutedValues() {
        val obj1 = parseObject("""{ "a" : { "x" : 1, "z" : 4 }, "c" : ${a} }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "z" : 5 }, "c" : ${b} }""")

        val merged = merge(obj1, obj2)
        val resolved = resolveNoSystem(merged, merged) match {
            case x: ConfigObject => x
        }

        assertEquals(3, resolved.getObject("c").size())
        assertEquals(1, resolved.getInt("c.x"))
        assertEquals(2, resolved.getInt("c.y"))
        assertEquals(4, resolved.getInt("c.z"))
    }

    @Test
    def mergeObjectWithSubstituted() {
        val obj1 = parseObject("""{ "a" : { "x" : 1, "z" : 4 }, "c" : { "z" : 42 } }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "z" : 5 }, "c" : ${b} }""")

        val merged = merge(obj1, obj2)
        val resolved = resolveNoSystem(merged, merged) match {
            case x: ConfigObject => x
        }

        assertEquals(2, resolved.getObject("c").size())
        assertEquals(2, resolved.getInt("c.y"))
        assertEquals(42, resolved.getInt("c.z"))

        val merged2 = merge(obj2, obj1)
        val resolved2 = resolveNoSystem(merged2, merged2) match {
            case x: ConfigObject => x
        }

        assertEquals(2, resolved2.getObject("c").size())
        assertEquals(2, resolved2.getInt("c.y"))
        assertEquals(5, resolved2.getInt("c.z"))
    }

    private val cycleObject = {
        parseObject("""
{
    "foo" : ${bar},
    "bar" : ${a.b.c},
    "a" : { "b" : { "c" : ${foo} } }
}
""")
    }

    @Test
    def mergeHidesCycles() {
        // the point here is that we should not try to evaluate a substitution
        // that's been overridden, and thus not end up with a cycle as long
        // as we override the problematic link in the cycle.
        val e = intercept[ConfigException.BadValue] {
            val v = resolveNoSystem(subst("foo"), cycleObject)
        }
        assertTrue(e.getMessage().contains("cycle"))

        val fixUpCycle = parseObject(""" { "a" : { "b" : { "c" : 57 } } } """)
        val merged = merge(fixUpCycle, cycleObject)
        val v = resolveNoSystem(subst("foo"), merged)
        assertEquals(intValue(57), v);
    }

    @Test
    def mergeWithObjectInFrontKeepsCycles() {
        // the point here is that if our eventual value will be an object, then
        // we have to evaluate the substitution to see if it's an object to merge,
        // so we don't avoid the cycle.
        val e = intercept[ConfigException.BadValue] {
            val v = resolveNoSystem(subst("foo"), cycleObject)
        }
        assertTrue(e.getMessage().contains("cycle"))

        val fixUpCycle = parseObject(""" { "a" : { "b" : { "c" : { "q" : "u" } } } } """)
        val merged = merge(fixUpCycle, cycleObject)
        val e2 = intercept[ConfigException.BadValue] {
            val v = resolveNoSystem(subst("foo"), merged)
        }
        assertTrue(e2.getMessage().contains("cycle"))
    }

    @Test
    def mergeSeriesOfSubstitutions() {
        val obj1 = parseObject("""{ "a" : { "x" : 1, "q" : 4 }, "j" : ${a} }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "q" : 5 }, "j" : ${b} }""")
        val obj3 = parseObject("""{ "c" : { "z" : 3, "q" : 6 }, "j" : ${c} }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            val resolved = resolveNoSystem(merged, merged) match {
                case x: ConfigObject => x
            }

            assertEquals(4, resolved.getObject("j").size())
            assertEquals(1, resolved.getInt("j.x"))
            assertEquals(2, resolved.getInt("j.y"))
            assertEquals(3, resolved.getInt("j.z"))
            assertEquals(4, resolved.getInt("j.q"))
        }
    }

    @Test
    def mergePrimitiveAndTwoSubstitutions() {
        val obj1 = parseObject("""{ "j" : 42 }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "q" : 5 }, "j" : ${b} }""")
        val obj3 = parseObject("""{ "c" : { "z" : 3, "q" : 6 }, "j" : ${c} }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            val resolved = resolveNoSystem(merged, merged) match {
                case x: ConfigObject => x
            }

            assertEquals(3, resolved.size())
            assertEquals(42, resolved.getInt("j"));
            assertEquals(2, resolved.getInt("b.y"))
            assertEquals(3, resolved.getInt("c.z"))
        }
    }

    @Test
    def mergeObjectAndTwoSubstitutions() {
        val obj1 = parseObject("""{ "j" : { "x" : 1, "q" : 4 } }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "q" : 5 }, "j" : ${b} }""")
        val obj3 = parseObject("""{ "c" : { "z" : 3, "q" : 6 }, "j" : ${c} }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            val resolved = resolveNoSystem(merged, merged) match {
                case x: ConfigObject => x
            }

            assertEquals(4, resolved.getObject("j").size())
            assertEquals(1, resolved.getInt("j.x"))
            assertEquals(2, resolved.getInt("j.y"))
            assertEquals(3, resolved.getInt("j.z"))
            assertEquals(4, resolved.getInt("j.q"))
        }
    }

    @Test
    def mergeObjectSubstitutionObjectSubstitution() {
        val obj1 = parseObject("""{ "j" : { "w" : 1, "q" : 5 } }""")
        val obj2 = parseObject("""{ "b" : { "x" : 2, "q" : 6 }, "j" : ${b} }""")
        val obj3 = parseObject("""{ "j" : { "y" : 3, "q" : 7 } }""")
        val obj4 = parseObject("""{ "c" : { "z" : 4, "q" : 8 }, "j" : ${c} }""")

        associativeMerge(Seq(obj1, obj2, obj3, obj4)) { merged =>
            val resolved = resolveNoSystem(merged, merged) match {
                case x: ConfigObject => x
            }

            assertEquals(5, resolved.getObject("j").size())
            assertEquals(1, resolved.getInt("j.w"))
            assertEquals(2, resolved.getInt("j.x"))
            assertEquals(3, resolved.getInt("j.y"))
            assertEquals(4, resolved.getInt("j.z"))
            assertEquals(5, resolved.getInt("j.q"))
        }
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
        assertEquals(42, conf.getAnyRef("ints.fortyTwo"))
        assertEquals("abcd", conf.getAnyRef("strings.abcd"))
        assertEquals(false, conf.getAnyRef("booleans.falseAgain"))

        // get empty array as any type of array
        assertEquals(Seq(), conf.getAnyRefList("arrays.empty").asScala)
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
        assertEquals(Seq(null, null, null), conf.getAnyRefList("arrays.ofNull").asScala)
        assertEquals(Seq(true, false), conf.getBooleanList("arrays.ofBoolean").asScala)
        val listOfLists = conf.getAnyRefList("arrays.ofArray").asScala map { _.asInstanceOf[java.util.List[_]].asScala }
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
            conf.getMemorySizeInBytes("nulls.null")
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
            conf.getMemorySizeInBytes("ints")
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
            conf.getMemorySizeInBytes("strings.a")
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
        assertEquals(1024 * 1024L, conf.getMemorySizeInBytes("memsizes.meg"))
        assertEquals(1024 * 1024L, conf.getMemorySizeInBytes("memsizes.megAsNumber"))
        assertEquals(Seq(1024 * 1024L, 1024 * 1024L, 1024L * 1024L),
            conf.getMemorySizeInBytesList("memsizes.megsList").asScala)
        assertEquals(512 * 1024L, conf.getMemorySizeInBytes("memsizes.halfMeg"))
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
    def test01SystemFallbacks() {
        val conf = Config.load("test01")
        val jv = System.getProperty("java.version")
        assertNotNull(jv)
        assertEquals(jv, conf.getString("system.javaversion"))
        val home = System.getenv("HOME")
        if (home != null) {
            assertEquals(home, conf.getString("system.home"))
        } else {
            assertEquals(nullValue, conf.get("system.home"))
        }
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

    @Test
    def test03Includes() {
        val conf = Config.load("test03")

        // include should have overridden the "ints" value in test03
        assertEquals(42, conf.getInt("test01.ints.fortyTwo"))
        // include should have been overridden by 42
        assertEquals(42, conf.getInt("test01.booleans"));
        assertEquals(42, conf.getInt("test01.booleans"));
        // include should have gotten .properties and .json also
        assertEquals("abc", conf.getString("test01.fromProps.abc"))
        assertEquals("A", conf.getString("test01.fromJsonA"))
        // test02 was included
        assertEquals(57, conf.getInt("test02.a.b.c"))
        // equiv01/original.json was included (it has a slash in the name)
        assertEquals("a", conf.getString("equiv01.strings.a"))

        // Now check that substitutions still work
        assertEquals(42, conf.getInt("test01.ints.fortyTwoAgain"))
        assertEquals(Seq("a", "b", "c"), conf.getStringList("test01.arrays.ofString").asScala)
        assertEquals(103, conf.getInt("test02.103_a"))

        // and system fallbacks still work
        val jv = System.getProperty("java.version")
        assertNotNull(jv)
        assertEquals(jv, conf.getString("test01.system.javaversion"))
        val home = System.getenv("HOME")
        if (home != null) {
            assertEquals(home, conf.getString("test01.system.home"))
        } else {
            assertEquals(nullValue, conf.get("test01.system.home"))
        }
        val concatenated = conf.getString("test01.system.concatenated")
        assertTrue(concatenated.contains("Your Java version"))
        assertTrue(concatenated.contains(jv))
        assertTrue(concatenated.contains(conf.getString("test01.system.userhome")))
    }

    @Test
    def test04LoadAkkaReference() {
        val conf = Config.load("test04")

        // Note, test04 is an unmodified old-style akka.conf,
        // which means it has an outer akka{} namespace.
        // that namespace wouldn't normally be used with
        // this library because the conf object is not global,
        // it's per-module already.
        assertEquals("2.0-SNAPSHOT", conf.getString("akka.version"))
        assertEquals(8, conf.getInt("akka.event-handler-dispatcher.max-pool-size"))
        assertEquals("round-robin", conf.getString("akka.actor.deployment.\"/app/service-ping\".router"))
        assertEquals(true, conf.getBoolean("akka.stm.quick-release"))
    }

    @Test
    def test05LoadPlayApplicationConf() {
        val conf = Config.load("test05")

        assertEquals("prod", conf.getString("%prod.application.mode"))
        assertEquals("Yet another blog", conf.getString("blog.title"))
    }
}
