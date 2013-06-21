/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
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
import java.util.concurrent.TimeUnit.{ SECONDS, NANOSECONDS, MICROSECONDS, MILLISECONDS, MINUTES, DAYS, HOURS }
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigSyntax

class ConfigTest extends TestUtils {

    private def resolveNoSystem(v: AbstractConfigValue, root: AbstractConfigObject) = {
        ResolveContext.resolve(v, root, ConfigResolveOptions.noSystem())
    }

    private def resolveNoSystem(v: SimpleConfig, root: SimpleConfig) = {
        ResolveContext.resolve(v.root, root.root,
            ConfigResolveOptions.noSystem()).asInstanceOf[AbstractConfigObject].toConfig
    }

    def mergeUnresolved(toMerge: AbstractConfigObject*) = {
        if (toMerge.isEmpty) {
            SimpleConfigObject.empty()
        } else {
            toMerge.reduce((first, second) => first.withFallback(second))
        }
    }

    def merge(toMerge: AbstractConfigObject*) = {
        val obj = mergeUnresolved(toMerge: _*)
        resolveNoSystem(obj, obj) match {
            case x: AbstractConfigObject => x
        }
    }

    // Merging should always be associative (same results however the values are grouped,
    // as long as they remain in the same order)
    private def associativeMerge(allObjects: Seq[AbstractConfigObject])(assertions: SimpleConfig => Unit) {
        def makeTrees(objects: Seq[AbstractConfigObject]): Iterator[AbstractConfigObject] = {
            objects.length match {
                case 0 => Iterator.empty
                case 1 => {
                    Iterator(objects(0))
                }
                case 2 => {
                    Iterator(objects(0).withFallback(objects(1)))
                }
                case n => {
                    val leftSplits = for {
                        i <- (1 until n)
                        val pair = objects.splitAt(i)
                        val first = pair._1.reduceLeft(_.withFallback(_))
                        val second = pair._2.reduceLeft(_.withFallback(_))
                    } yield first.withFallback(second)
                    val rightSplits = for {
                        i <- (1 until n)
                        val pair = objects.splitAt(i)
                        val first = pair._1.reduceRight(_.withFallback(_))
                        val second = pair._2.reduceRight(_.withFallback(_))
                    } yield first.withFallback(second)
                    leftSplits.iterator ++ rightSplits.iterator
                }
            }
        }

        val trees = makeTrees(allObjects).toSeq
        for (tree <- trees) {
            // if this fails, we were not associative.
            if (!trees(0).equals(tree))
                throw new AssertionError("Merge was not associative, " +
                    "verify that it should not be, then don't use associativeMerge " +
                    "for this one. two results were: \none: " + trees(0) + "\ntwo: " +
                    tree + "\noriginal list: " + allObjects)
        }

        for (tree <- trees) {
            assertions(tree.toConfig)
        }
    }

    @Test
    def mergeTrivial() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "b" : 2 }""")
        val merged = merge(obj1, obj2).toConfig

        assertEquals(1, merged.getInt("a"))
        assertEquals(2, merged.getInt("b"))
        assertEquals(2, merged.root.size)
    }

    @Test
    def mergeEmpty() {
        val merged = merge().toConfig

        assertEquals(0, merged.root.size)
    }

    @Test
    def mergeOne() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val merged = merge(obj1).toConfig

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.root.size)
    }

    @Test
    def mergeOverride() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val merged = merge(obj1, obj2).toConfig

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.root.size)

        val merged2 = merge(obj2, obj1).toConfig

        assertEquals(2, merged2.getInt("a"))
        assertEquals(1, merged2.root.size)
    }

    @Test
    def mergeN() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "b" : 2 }""")
        val obj3 = parseObject("""{ "c" : 3 }""")
        val obj4 = parseObject("""{ "d" : 4 }""")

        associativeMerge(Seq(obj1, obj2, obj3, obj4)) { merged =>
            assertEquals(1, merged.getInt("a"))
            assertEquals(2, merged.getInt("b"))
            assertEquals(3, merged.getInt("c"))
            assertEquals(4, merged.getInt("d"))
            assertEquals(4, merged.root.size)
        }
    }

    @Test
    def mergeOverrideN() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val obj3 = parseObject("""{ "a" : 3 }""")
        val obj4 = parseObject("""{ "a" : 4 }""")
        associativeMerge(Seq(obj1, obj2, obj3, obj4)) { merged =>
            assertEquals(1, merged.getInt("a"))
            assertEquals(1, merged.root.size)
        }

        associativeMerge(Seq(obj4, obj3, obj2, obj1)) { merged2 =>
            assertEquals(4, merged2.getInt("a"))
            assertEquals(1, merged2.root.size)
        }
    }

    @Test
    def mergeNested() {
        val obj1 = parseObject("""{ "root" : { "a" : 1, "z" : 101 } }""")
        val obj2 = parseObject("""{ "root" : { "b" : 2, "z" : 102 } }""")
        val merged = merge(obj1, obj2).toConfig

        assertEquals(1, merged.getInt("root.a"))
        assertEquals(2, merged.getInt("root.b"))
        assertEquals(101, merged.getInt("root.z"))
        assertEquals(1, merged.root.size)
        assertEquals(3, merged.getConfig("root").root.size)
    }

    @Test
    def mergeWithEmpty() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ }""")
        val merged = merge(obj1, obj2).toConfig

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.root.size)

        val merged2 = merge(obj2, obj1).toConfig

        assertEquals(1, merged2.getInt("a"))
        assertEquals(1, merged2.root.size)
    }

    @Test
    def mergeOverrideObjectAndPrimitive() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val merged = merge(obj1, obj2).toConfig

        assertEquals(1, merged.getInt("a"))
        assertEquals(1, merged.root.size)

        val merged2 = merge(obj2, obj1).toConfig

        assertEquals(42, merged2.getConfig("a").getInt("b"))
        assertEquals(42, merged2.getInt("a.b"))
        assertEquals(1, merged2.root.size)
        assertEquals(1, merged2.getObject("a").size)
    }

    @Test
    def mergeOverrideObjectAndSubstitution() {
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : ${c} }, "c" : 42 }""")
        val merged = merge(obj1, obj2).toConfig

        assertEquals(1, merged.getInt("a"))
        assertEquals(2, merged.root.size)

        val merged2 = merge(obj2, obj1).toConfig

        assertEquals(42, merged2.getConfig("a").getInt("b"))
        assertEquals(42, merged2.getInt("a.b"))
        assertEquals(2, merged2.root.size)
        assertEquals(1, merged2.getObject("a").size)
    }

    @Test
    def mergeObjectThenPrimitiveThenObject() {
        // the semantic here is that the primitive blocks the
        // object that occurs at lower priority. This is consistent
        // with duplicate keys in the same file.
        val obj1 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val obj3 = parseObject("""{ "a" : { "b" : 43, "c" : 44 } }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            assertEquals(42, merged.getInt("a.b"))
            assertEquals(1, merged.root.size)
            assertEquals(1, merged.getObject("a").size())
        }

        associativeMerge(Seq(obj3, obj2, obj1)) { merged2 =>
            assertEquals(43, merged2.getInt("a.b"))
            assertEquals(44, merged2.getInt("a.c"))
            assertEquals(1, merged2.root.size)
            assertEquals(2, merged2.getObject("a").size())
        }
    }

    @Test
    def mergeObjectThenSubstitutionThenObject() {
        // the semantic here is that the primitive blocks the
        // object that occurs at lower priority. This is consistent
        // with duplicate keys in the same file.
        val obj1 = parseObject("""{ "a" : { "b" : ${f} } }""")
        val obj2 = parseObject("""{ "a" : 2 }""")
        val obj3 = parseObject("""{ "a" : { "b" : ${d}, "c" : ${e} }, "d" : 43, "e" : 44, "f" : 42 }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { unresolved =>
            val merged = resolveNoSystem(unresolved, unresolved)
            assertEquals(42, merged.getInt("a.b"))
            assertEquals(4, merged.root.size)
            assertEquals(1, merged.getObject("a").size())
        }

        associativeMerge(Seq(obj3, obj2, obj1)) { unresolved =>
            val merged2 = resolveNoSystem(unresolved, unresolved)
            assertEquals(43, merged2.getInt("a.b"))
            assertEquals(44, merged2.getInt("a.c"))
            assertEquals(4, merged2.root.size)
            assertEquals(2, merged2.getObject("a").size())
        }
    }

    @Test
    def mergePrimitiveThenObjectThenPrimitive() {
        // the primitive should override the object
        val obj1 = parseObject("""{ "a" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj3 = parseObject("""{ "a" : 3 }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            assertEquals(1, merged.getInt("a"))
            assertEquals(1, merged.root.size)
        }
    }

    @Test
    def mergeSubstitutionThenObjectThenSubstitution() {
        // the substitution should override the object
        val obj1 = parseObject("""{ "a" : ${b}, "b" : 1 }""")
        val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
        val obj3 = parseObject("""{ "a" : ${c}, "c" : 2 }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            val resolved = resolveNoSystem(merged, merged)

            assertEquals(1, resolved.getInt("a"))
            assertEquals(3, resolved.root.size)
        }
    }

    @Test
    def mergeSubstitutedValues() {
        val obj1 = parseObject("""{ "a" : { "x" : 1, "z" : 4 }, "c" : ${a} }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "z" : 5 }, "c" : ${b} }""")

        val resolved = merge(obj1, obj2).toConfig

        assertEquals(3, resolved.getObject("c").size())
        assertEquals(1, resolved.getInt("c.x"))
        assertEquals(2, resolved.getInt("c.y"))
        assertEquals(4, resolved.getInt("c.z"))
    }

    @Test
    def mergeObjectWithSubstituted() {
        val obj1 = parseObject("""{ "a" : { "x" : 1, "z" : 4 }, "c" : { "z" : 42 } }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "z" : 5 }, "c" : ${b} }""")

        val resolved = merge(obj1, obj2).toConfig

        assertEquals(2, resolved.getObject("c").size())
        assertEquals(2, resolved.getInt("c.y"))
        assertEquals(42, resolved.getInt("c.z"))

        val resolved2 = merge(obj2, obj1).toConfig

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
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            val v = resolveNoSystem(subst("foo"), cycleObject)
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))

        val fixUpCycle = parseObject(""" { "a" : { "b" : { "c" : 57 } } } """)
        val merged = mergeUnresolved(fixUpCycle, cycleObject)
        val v = resolveNoSystem(subst("foo"), merged)
        assertEquals(intValue(57), v);
    }

    @Test
    def mergeWithObjectInFrontKeepsCycles() {
        // the point here is that if our eventual value will be an object, then
        // we have to evaluate the substitution to see if it's an object to merge,
        // so we don't avoid the cycle.
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            val v = resolveNoSystem(subst("foo"), cycleObject)
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))

        val fixUpCycle = parseObject(""" { "a" : { "b" : { "c" : { "q" : "u" } } } } """)
        val merged = mergeUnresolved(fixUpCycle, cycleObject)
        val e2 = intercept[ConfigException.UnresolvedSubstitution] {
            val v = resolveNoSystem(subst("foo"), merged)
        }
        // TODO: it would be nicer if the above threw BadValue with an
        // explanation about the cycle.
        //assertTrue(e2.getMessage().contains("cycle"))
    }

    @Test
    def mergeSeriesOfSubstitutions() {
        val obj1 = parseObject("""{ "a" : { "x" : 1, "q" : 4 }, "j" : ${a} }""")
        val obj2 = parseObject("""{ "b" : { "y" : 2, "q" : 5 }, "j" : ${b} }""")
        val obj3 = parseObject("""{ "c" : { "z" : 3, "q" : 6 }, "j" : ${c} }""")

        associativeMerge(Seq(obj1, obj2, obj3)) { merged =>
            val resolved = resolveNoSystem(merged, merged)

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
            val resolved = resolveNoSystem(merged, merged)

            assertEquals(3, resolved.root.size())
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
            val resolved = resolveNoSystem(merged, merged)

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
            val resolved = resolveNoSystem(merged, merged)

            assertEquals(5, resolved.getObject("j").size())
            assertEquals(1, resolved.getInt("j.w"))
            assertEquals(2, resolved.getInt("j.x"))
            assertEquals(3, resolved.getInt("j.y"))
            assertEquals(4, resolved.getInt("j.z"))
            assertEquals(5, resolved.getInt("j.q"))
        }
    }

    private def ignoresFallbacks(m: ConfigMergeable) = {
        m match {
            case v: AbstractConfigValue =>
                v.ignoresFallbacks()
            case c: SimpleConfig =>
                c.root.ignoresFallbacks()
        }
    }

    private def testIgnoredMergesDoNothing(nonEmpty: ConfigMergeable) {
        // falling back to a primitive once should switch us to "ignoreFallbacks" mode
        // and then twice should "return this". Falling back to an empty object should
        // return this unless the empty object was ignoreFallbacks and then we should
        // "catch" its ignoreFallbacks.

        // some of what this tests is just optimization, not API contract (withFallback
        // can return a new object anytime it likes) but want to be sure we do the
        // optimizations.

        val empty = SimpleConfigObject.empty(null)
        val primitive = intValue(42)
        val emptyIgnoringFallbacks = empty.withFallback(primitive)
        val nonEmptyIgnoringFallbacks = nonEmpty.withFallback(primitive)

        assertEquals(false, empty.ignoresFallbacks())
        assertEquals(true, primitive.ignoresFallbacks())
        assertEquals(true, emptyIgnoringFallbacks.ignoresFallbacks())
        assertEquals(false, ignoresFallbacks(nonEmpty))
        assertEquals(true, ignoresFallbacks(nonEmptyIgnoringFallbacks))

        assertTrue(nonEmpty ne nonEmptyIgnoringFallbacks)
        assertTrue(empty ne emptyIgnoringFallbacks)

        // falling back from one object to another should not make us ignore fallbacks
        assertEquals(false, ignoresFallbacks(nonEmpty.withFallback(empty)))
        assertEquals(false, ignoresFallbacks(empty.withFallback(nonEmpty)))
        assertEquals(false, ignoresFallbacks(empty.withFallback(empty)))
        assertEquals(false, ignoresFallbacks(nonEmpty.withFallback(nonEmpty)))

        // falling back from primitive just returns this
        assertTrue(primitive eq primitive.withFallback(empty))
        assertTrue(primitive eq primitive.withFallback(nonEmpty))
        assertTrue(primitive eq primitive.withFallback(nonEmptyIgnoringFallbacks))

        // falling back again from an ignoreFallbacks should be a no-op, return this
        assertTrue(nonEmptyIgnoringFallbacks eq nonEmptyIgnoringFallbacks.withFallback(empty))
        assertTrue(nonEmptyIgnoringFallbacks eq nonEmptyIgnoringFallbacks.withFallback(primitive))
        assertTrue(emptyIgnoringFallbacks eq emptyIgnoringFallbacks.withFallback(empty))
        assertTrue(emptyIgnoringFallbacks eq emptyIgnoringFallbacks.withFallback(primitive))
    }

    @Test
    def ignoredMergesDoNothing() {
        val conf = parseConfig("{ a : 1 }")
        testIgnoredMergesDoNothing(conf)
    }

    @Test
    def testNoMergeAcrossArray() {
        val conf = parseConfig("a: {b:1}, a: [2,3], a:{c:4}")
        assertFalse("a.b found in: " + conf, conf.hasPath("a.b"))
        assertTrue("a.c not found in: " + conf, conf.hasPath("a.c"))
    }

    @Test
    def testNoMergeAcrossUnresolvedArray() {
        val conf = parseConfig("a: {b:1}, a: [2,${x}], a:{c:4}, x: 42")
        assertFalse("a.b found in: " + conf, conf.hasPath("a.b"))
        assertTrue("a.c not found in: " + conf, conf.hasPath("a.c"))
    }

    @Test
    def integerRangeChecks() {
        val conf = parseConfig("{ tooNegative: " + (Integer.MIN_VALUE - 1L) + ", tooPositive: " + (Integer.MAX_VALUE + 1L) + "}")
        val en = intercept[ConfigException.WrongType] {
            conf.getInt("tooNegative")
        }
        assertTrue(en.getMessage.contains("range"))

        val ep = intercept[ConfigException.WrongType] {
            conf.getInt("tooPositive")
        }
        assertTrue(ep.getMessage.contains("range"))
    }

    @Test
    def test01Getting() {
        val conf = ConfigFactory.load("test01")

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
        assertNull(conf.root.get("notinthefile"))

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
        val conf = ConfigFactory.load("test01")

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
            conf.getBytes("nulls.null")
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
            conf.getBytes("ints")
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
            conf.getBytes("strings.a")
        }
    }

    @Test
    def test01Conversions() {
        val conf = ConfigFactory.load("test01")

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

        def assertDurationAsTimeUnit(unit: TimeUnit): Unit = {
          def ms2unit(l: Long) = unit.convert(l, MILLISECONDS)
          def s2unit(i: Int) = unit.convert(i, SECONDS)
          assertEquals(ms2unit(1000L), conf.getDuration("durations.second", unit))
          assertEquals(s2unit(1), conf.getDuration("durations.second", unit))
          assertEquals(ms2unit(1000L), conf.getDuration("durations.secondAsNumber", unit))
          assertEquals(s2unit(1), conf.getDuration("durations.secondAsNumber", unit))
          assertEquals(Seq(1000L, 2000L, 3000L, 4000L) map ms2unit,
            conf.getDurationList("durations.secondsList", unit).asScala)
          assertEquals(Seq(1, 2, 3, 4) map s2unit,
            conf.getDurationList("durations.secondsList", unit).asScala)
          assertEquals(ms2unit(500L), conf.getDuration("durations.halfSecond", unit))
        }

        assertDurationAsTimeUnit(NANOSECONDS)
        assertDurationAsTimeUnit(MICROSECONDS)
        assertDurationAsTimeUnit(MILLISECONDS)
        assertDurationAsTimeUnit(SECONDS)
        assertDurationAsTimeUnit(MINUTES)
        assertDurationAsTimeUnit(HOURS)
        assertDurationAsTimeUnit(DAYS)

        // should get size in bytes
        assertEquals(1024 * 1024L, conf.getBytes("memsizes.meg"))
        assertEquals(1024 * 1024L, conf.getBytes("memsizes.megAsNumber"))
        assertEquals(Seq(1024 * 1024L, 1024 * 1024L, 1024L * 1024L),
            conf.getBytesList("memsizes.megsList").asScala)
        assertEquals(512 * 1024L, conf.getBytes("memsizes.halfMeg"))
    }

    @Test
    def test01MergingOtherFormats() {
        val conf = ConfigFactory.load("test01")

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
        val conf = ConfigFactory.load("test01")

        // toString() on conf objects doesn't throw (toString is just a debug string so not testing its result)
        conf.toString()
    }

    @Test
    def test01SystemFallbacks() {
        val conf = ConfigFactory.load("test01")
        val jv = System.getProperty("java.version")
        assertNotNull(jv)
        assertEquals(jv, conf.getString("system.javaversion"))
        val home = System.getenv("HOME")
        if (home != null) {
            assertEquals(home, conf.getString("system.home"))
        } else {
            assertEquals(nullValue, conf.getObject("system").get("home"))
        }
    }

    @Test
    def test01Origins() {
        val conf = ConfigFactory.load("test01")

        val o1 = conf.getValue("ints.fortyTwo").origin()
        assertEquals("test01.conf: 3", o1.description)
        assertEquals("test01.conf", o1.resource)
        assertEquals(3, o1.lineNumber)

        val o2 = conf.getValue("fromJson1").origin()
        assertEquals("test01.json: 2", o2.description)
        assertEquals("test01.json", o2.resource)
        assertEquals(2, o2.lineNumber)

        val o3 = conf.getValue("fromProps.bool").origin()
        assertEquals("test01.properties", o3.description)
        assertEquals("test01.properties", o3.resource)
        // we don't have line numbers for properties files
        assertEquals(-1, o3.lineNumber)
    }

    @Test
    def test01EntrySet() {
        val conf = ConfigFactory.load("test01")

        val javaEntries = conf.entrySet()
        val entries = Map((javaEntries.asScala.toSeq map { e => (e.getKey(), e.getValue()) }): _*)
        assertEquals(Some(intValue(42)), entries.get("ints.fortyTwo"))
        assertEquals(None, entries.get("nulls.null"))
    }

    @Test
    def test01Serializable() {
        // we can't ever test an expected serialization here because it
        // will have system props in it that vary by test system,
        // and the ConfigOrigin in there will also vary by test system
        val conf = ConfigFactory.load("test01")
        val confCopy = checkSerializable(conf)
    }

    @Test
    def test02SubstitutionsWithWeirdPaths() {
        val conf = ConfigFactory.load("test02")

        assertEquals(42, conf.getInt("42_a"))
        assertEquals(42, conf.getInt("42_b"))
        assertEquals(57, conf.getInt("57_a"))
        assertEquals(57, conf.getInt("57_b"))
        assertEquals(103, conf.getInt("103_a"))
    }

    @Test
    def test02UseWeirdPathsWithConfigObject() {
        val conf = ConfigFactory.load("test02")

        // we're checking that the getters in ConfigObject support
        // these weird path expressions
        assertEquals(42, conf.getInt(""" "".""."" """))
        assertEquals(57, conf.getInt("a.b.c"))
        assertEquals(57, conf.getInt(""" "a"."b"."c" """))
        assertEquals(103, conf.getInt(""" "a.b.c" """))
    }

    @Test
    def test03Includes() {
        val conf = ConfigFactory.load("test03")

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
            assertEquals(nullValue, conf.getObject("test01.system").get("home"))
        }
        val concatenated = conf.getString("test01.system.concatenated")
        assertTrue(concatenated.contains("Your Java version"))
        assertTrue(concatenated.contains(jv))
        assertTrue(concatenated.contains(conf.getString("test01.system.userhome")))

        // check that includes into the root object work and that
        // "substitutions look relative-to-included-file first then at root second" works
        assertEquals("This is in the included file", conf.getString("a"));
        assertEquals("This is in the including file", conf.getString("b"));
        assertEquals("This is in the included file", conf.getString("subtree.a"));
        assertEquals("This is in the including file", conf.getString("subtree.b"));
    }

    @Test
    def test04LoadAkkaReference() {
        val conf = ConfigFactory.load("test04")

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
        val conf = ConfigFactory.load("test05")

        assertEquals("prod", conf.getString("%prod.application.mode"))
        assertEquals("Yet another blog", conf.getString("blog.title"))
    }

    @Test
    def test06Merge() {
        // test06 mostly exists because its render() round trip is tricky
        val conf = ConfigFactory.load("test06")

        assertEquals(2, conf.getInt("x"))
        assertEquals(10, conf.getInt("y.foo"))
        assertEquals("world", conf.getString("y.hello"))
    }

    @Test
    def test07IncludingResourcesFromFiles() {
        // first, check that when loading from classpath we include another classpath resource
        val fromClasspath = ConfigFactory.parseResources(classOf[ConfigTest], "/test07.conf")

        assertEquals("This is to test classpath searches.", fromClasspath.getString("test-lib.description"))

        // second, check that when loading from a file it falls back to classpath
        val fromFile = ConfigFactory.parseFile(resourceFile("test07.conf"))

        assertEquals("This is to test classpath searches.", fromFile.getString("test-lib.description"))

        // third, check that a file: URL is the same
        val fromURL = ConfigFactory.parseURL(resourceFile("test07.conf").toURI.toURL)

        assertEquals("This is to test classpath searches.", fromURL.getString("test-lib.description"))
    }

    @Test
    def test08IncludingSlashPrefixedResources() {
        // first, check that when loading from classpath we include another classpath resource
        val fromClasspath = ConfigFactory.parseResources(classOf[ConfigTest], "/test08.conf")

        assertEquals("This is to test classpath searches.", fromClasspath.getString("test-lib.description"))

        // second, check that when loading from a file it falls back to classpath
        val fromFile = ConfigFactory.parseFile(resourceFile("test08.conf"))

        assertEquals("This is to test classpath searches.", fromFile.getString("test-lib.description"))

        // third, check that a file: URL is the same
        val fromURL = ConfigFactory.parseURL(resourceFile("test08.conf").toURI.toURL)

        assertEquals("This is to test classpath searches.", fromURL.getString("test-lib.description"))
    }

    @Test
    def test09DelayedMerge() {
        val conf = ConfigFactory.parseResources(classOf[ConfigTest], "/test09.conf")
        assertEquals(classOf[ConfigDelayedMergeObject].getSimpleName,
            conf.root.get("a").getClass.getSimpleName)
        assertEquals(classOf[ConfigDelayedMerge].getSimpleName,
            conf.root.get("b").getClass.getSimpleName)

        // a.c should work without resolving because no more merging is needed to compute it
        assertEquals(3, conf.getInt("a.c"))

        intercept[ConfigException.NotResolved] {
            conf.getInt("a.q")
        }

        // be sure resolving doesn't throw
        val resolved = conf.resolve()
        assertEquals(3, resolved.getInt("a.c"))
        assertEquals(5, resolved.getInt("b"))
        assertEquals(10, resolved.getInt("a.q"))
    }

    @Test
    def test10DelayedMergeRelativizing() {
        val conf = ConfigFactory.parseResources(classOf[ConfigTest], "/test10.conf")
        val resolved = conf.resolve()
        assertEquals(3, resolved.getInt("foo.a.c"))
        assertEquals(5, resolved.getInt("foo.b"))
        assertEquals(10, resolved.getInt("foo.a.q"))
    }

    @Test
    def renderRoundTrip() {
        val allBooleans = true :: false :: Nil
        val optionsCombos = {
            for (
                formatted <- allBooleans;
                originComments <- allBooleans;
                comments <- allBooleans;
                json <- allBooleans
            ) yield ConfigRenderOptions.defaults()
                .setFormatted(formatted)
                .setOriginComments(originComments)
                .setComments(comments)
                .setJson(json)
        } toSeq

        for (i <- 1 to 10) {
            val numString = i.toString
            val name = "/test" + { if (numString.size == 1) "0" else "" } + numString
            val conf = ConfigFactory.parseResourcesAnySyntax(classOf[ConfigTest], name,
                ConfigParseOptions.defaults().setAllowMissing(false))
            for (renderOptions <- optionsCombos) {
                val unresolvedRender = conf.root.render(renderOptions)
                val resolved = conf.resolve()
                val resolvedRender = resolved.root.render(renderOptions)
                val unresolvedParsed = ConfigFactory.parseString(unresolvedRender, ConfigParseOptions.defaults())
                val resolvedParsed = ConfigFactory.parseString(resolvedRender, ConfigParseOptions.defaults())
                try {
                    assertEquals("unresolved options=" + renderOptions, conf.root, unresolvedParsed.root)
                    assertEquals("resolved options=" + renderOptions, resolved.root, resolvedParsed.root)
                } catch {
                    case e: Throwable =>
                        System.err.println("UNRESOLVED diff:")
                        showDiff(conf.root, unresolvedParsed.root)
                        System.err.println("RESOLVED diff:")
                        showDiff(resolved.root, resolvedParsed.root)
                        throw e
                }
                if (renderOptions.getJson() && !(renderOptions.getComments() || renderOptions.getOriginComments())) {
                    // should get valid JSON if we don't have comments and are resolved
                    val json = try {
                        ConfigFactory.parseString(resolvedRender, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON));
                    } catch {
                        case e: Exception =>
                            System.err.println("resolvedRender is not valid json: " + resolvedRender)
                            throw e
                    }
                }
            }
        }
    }

    @Test
    def serializeRoundTrip() {
        for (i <- 1 to 10) {
            val numString = i.toString
            val name = "/test" + { if (numString.size == 1) "0" else "" } + numString
            val conf = ConfigFactory.parseResourcesAnySyntax(classOf[ConfigTest], name,
                ConfigParseOptions.defaults().setAllowMissing(false))
            val resolved = conf.resolve()
            checkSerializable(resolved)
        }
    }
}
