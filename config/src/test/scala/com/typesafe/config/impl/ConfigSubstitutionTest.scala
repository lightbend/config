/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class ConfigSubstitutionTest extends TestUtils {

    private def resolveWithoutFallbacks(v: AbstractConfigObject) = {
        val options = ConfigResolveOptions.noSystem()
        ResolveContext.resolve(v, v, options).asInstanceOf[AbstractConfigObject].toConfig
    }
    private def resolveWithoutFallbacks(s: AbstractConfigValue, root: AbstractConfigObject) = {
        val options = ConfigResolveOptions.noSystem()
        ResolveContext.resolve(s, root, options)
    }

    private def resolve(v: AbstractConfigObject) = {
        val options = ConfigResolveOptions.defaults()
        ResolveContext.resolve(v, v, options).asInstanceOf[AbstractConfigObject].toConfig
    }
    private def resolve(s: AbstractConfigValue, root: AbstractConfigObject) = {
        val options = ConfigResolveOptions.defaults()
        ResolveContext.resolve(s, root, options)
    }

    private val simpleObject = {
        parseObject("""
{
   "foo" : 42,
   "bar" : {
       "int" : 43,
       "bool" : true,
       "null" : null,
       "string" : "hello",
       "double" : 3.14
    }
}
""")
    }

    @Test
    def resolveTrivialKey() {
        val s = subst("foo")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(intValue(42), v)
    }

    @Test
    def resolveTrivialPath() {
        val s = subst("bar.int")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(intValue(43), v)
    }

    @Test
    def resolveInt() {
        val s = subst("bar.int")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(intValue(43), v)
    }

    @Test
    def resolveBool() {
        val s = subst("bar.bool")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(boolValue(true), v)
    }

    @Test
    def resolveNull() {
        val s = subst("bar.null")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(nullValue(), v)
    }

    @Test
    def resolveString() {
        val s = subst("bar.string")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(stringValue("hello"), v)
    }

    @Test
    def resolveDouble() {
        val s = subst("bar.double")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(doubleValue(3.14), v)
    }

    @Test
    def resolveMissingThrows() {
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            val s = subst("bar.missing")
            val v = resolveWithoutFallbacks(s, simpleObject)
        }
        assertTrue("wrong exception: " + e.getMessage,
            !e.getMessage.contains("cycle"))
    }

    @Test
    def resolveIntInString() {
        val s = substInString("bar.int")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(stringValue("start<43>end"), v)
    }

    @Test
    def resolveNullInString() {
        val s = substInString("bar.null")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(stringValue("start<null>end"), v)

        // when null is NOT a subst, it should also not become empty
        val o = parseConfig("""{ "a" : null foo bar }""")
        assertEquals("null foo bar", o.getString("a"))
    }

    @Test
    def resolveMissingInString() {
        val s = substInString("bar.missing", true /* optional */ )
        val v = resolveWithoutFallbacks(s, simpleObject)
        // absent object becomes empty string
        assertEquals(stringValue("start<>end"), v)

        intercept[ConfigException.UnresolvedSubstitution] {
            val s2 = substInString("bar.missing", false /* optional */ )
            resolveWithoutFallbacks(s2, simpleObject)
        }
    }

    @Test
    def resolveBoolInString() {
        val s = substInString("bar.bool")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(stringValue("start<true>end"), v)
    }

    @Test
    def resolveStringInString() {
        val s = substInString("bar.string")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(stringValue("start<hello>end"), v)
    }

    @Test
    def resolveDoubleInString() {
        val s = substInString("bar.double")
        val v = resolveWithoutFallbacks(s, simpleObject)
        assertEquals(stringValue("start<3.14>end"), v)
    }

    @Test
    def missingInArray() {
        import scala.collection.JavaConverters._

        val obj = parseObject("""
    a : [ ${?missing}, ${?also.missing} ]
""")

        val resolved = resolve(obj)

        assertEquals(Seq(), resolved.getList("a").asScala)
    }

    @Test
    def missingInObject() {
        import scala.collection.JavaConverters._

        val obj = parseObject("""
    a : ${?missing}, b : ${?also.missing}, c : ${?b}, d : ${?c}
""")

        val resolved = resolve(obj)

        assertTrue(resolved.isEmpty())
    }

    private val substChainObject = {
        parseObject("""
{
    "foo" : ${bar},
    "bar" : ${a.b.c},
    "a" : { "b" : { "c" : 57 } }
}
""")
    }

    @Test
    def chainSubstitutions() {
        val s = subst("foo")
        val v = resolveWithoutFallbacks(s, substChainObject)
        assertEquals(intValue(57), v)
    }

    @Test
    def substitutionsLookForward() {
        val obj = parseObject("""a=1,b=${a},a=2""")
        val resolved = resolve(obj)
        assertEquals(2, resolved.getInt("b"))
    }

    @Test
    def throwOnIncrediblyTrivialCycle() {
        val s = subst("a")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            val v = resolveWithoutFallbacks(s, parseObject("a: ${a}"))
        }
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("${a}"))
    }

    private val substCycleObject = {
        parseObject("""
{
    "foo" : ${bar},
    "bar" : ${a.b.c},
    "a" : { "b" : { "c" : ${foo} } }
}
""")
    }

    @Test
    def throwOnCycles() {
        val s = subst("foo")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            val v = resolveWithoutFallbacks(s, substCycleObject)
        }
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("${foo}, ${bar}, ${a.b.c}, ${foo}"))
    }

    @Test
    def throwOnOptionalReferenceToNonOptionalCycle() {
        // we look up ${?foo}, but the cycle has hard
        // non-optional links in it so still has to throw.
        val s = subst("foo", optional = true)
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            val v = resolveWithoutFallbacks(s, substCycleObject)
        }
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))
    }

    // ALL the links have to be optional here for the cycle to be ignored
    private val substCycleObjectOptionalLink = {
        parseObject("""
{
    "foo" : ${?bar},
    "bar" : ${?a.b.c},
    "a" : { "b" : { "c" : ${?foo} } }
}
""")
    }

    @Test
    def optionalLinkCyclesActLikeUndefined() {
        val s = subst("foo", optional = true)
        val v = resolveWithoutFallbacks(s, substCycleObjectOptionalLink)
        assertNull("Cycle with optional links in it resolves to null if it's a cycle", v)
    }

    @Test
    def throwOnTwoKeyCycle() {
        val obj = parseObject("""a:${b},b:${a}""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))
    }

    @Test
    def throwOnFourKeyCycle() {
        val obj = parseObject("""a:${b},b:${c},c:${d},d:${a}""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("Wrong exception: " + e.getMessage, e.getMessage().contains("cycle"))
    }

    @Test
    def resolveObject() {
        val resolved = resolveWithoutFallbacks(substChainObject)
        assertEquals(57, resolved.getInt("foo"))
        assertEquals(57, resolved.getInt("bar"))
        assertEquals(57, resolved.getInt("a.b.c"))
    }

    private val substSideEffectCycle = {
        parseObject("""
{
    "foo" : ${a.b.c},
    "a" : { "b" : { "c" : 42, "cycle" : ${foo} }, "cycle" : ${foo} }
}
""")
    }

    @Test
    def avoidSideEffectCycles() {
        // The point of this test is that in traversing objects
        // to resolve a path, we need to avoid resolving
        // substitutions that are in the traversed objects but
        // are not directly required to resolve the path.
        // i.e. there should not be a cycle in this test.

        val resolved = resolveWithoutFallbacks(substSideEffectCycle)

        assertEquals(42, resolved.getInt("foo"))
        assertEquals(42, resolved.getInt("a.b.cycle"))
        assertEquals(42, resolved.getInt("a.cycle"))
    }

    @Test
    def ignoreHiddenUndefinedSubst() {
        // if a substitution is overridden then it shouldn't matter that it's undefined
        val obj = parseObject("""a=${nonexistent},a=42""")
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("a"))
    }

    @Test
    def objectDoesNotHideUndefinedSubst() {
        // if a substitution is overridden by an object we still need to
        // evaluate the substitution
        val obj = parseObject("""a=${nonexistent},a={ b : 42 }""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("Could not resolve"))
    }

    @Test
    def ignoreHiddenCircularSubst() {
        // if a substitution is overridden then it shouldn't matter that it's circular
        val obj = parseObject("""a=${a},a=42""")
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("a"))
    }

    private val delayedMergeObjectResolveProblem1 = {
        parseObject("""
  defaults {
    a = 1
    b = 2
  }
  // make item1 into a ConfigDelayedMergeObject
  item1 = ${defaults}
  // note that we'll resolve to a non-object value
  // so item1.b will ignoreFallbacks and not depend on
  // ${defaults}
  item1.b = 3
  // be sure we can resolve a substitution to a value in
  // a delayed-merge object.
  item2.b = ${item1.b}
""")
    }

    @Test
    def avoidDelayedMergeObjectResolveProblem1() {
        assertTrue(delayedMergeObjectResolveProblem1.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])

        val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem1)

        assertEquals(3, resolved.getInt("item1.b"))
        assertEquals(3, resolved.getInt("item2.b"))
    }

    private val delayedMergeObjectResolveProblem2 = {
        parseObject("""
  defaults {
    a = 1
    b = 2
  }
  // make item1 into a ConfigDelayedMergeObject
  item1 = ${defaults}
  // note that we'll resolve to an object value
  // so item1.b will depend on also looking up ${defaults}
  item1.b = { c : 43 }
  // be sure we can resolve a substitution to a value in
  // a delayed-merge object.
  item2.b = ${item1.b}
""")
    }

    @Test
    def avoidDelayedMergeObjectResolveProblem2() {
        assertTrue(delayedMergeObjectResolveProblem2.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])

        val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem2)

        assertEquals(parseObject("{ c : 43 }"), resolved.getObject("item1.b"))
        assertEquals(43, resolved.getInt("item1.b.c"))
        assertEquals(43, resolved.getInt("item2.b.c"))
    }

    // in this case, item1 is self-referential because
    // it refers to ${defaults} which refers back to
    // ${item1}. When self-referencing, only the
    // value of ${item1} "looking back" should be
    // visible. This is really a test of the
    // self-referencing semantics.
    private val delayedMergeObjectResolveProblem3 = {
        parseObject("""
  item1.b.c = 100
  defaults {
    // we depend on item1.b.c
    a = ${item1.b.c}
    b = 2
  }
  // make item1 into a ConfigDelayedMergeObject
  item1 = ${defaults}
  // the ${item1.b.c} above in ${defaults} should ignore
  // this because it only looks back
  item1.b = { c : 43 }
  // be sure we can resolve a substitution to a value in
  // a delayed-merge object.
  item2.b = ${item1.b}
""")
    }

    @Test
    def avoidDelayedMergeObjectResolveProblem3() {
        assertTrue(delayedMergeObjectResolveProblem3.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])

        val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem3)

        assertEquals(parseObject("{ c : 43 }"), resolved.getObject("item1.b"))
        assertEquals(43, resolved.getInt("item1.b.c"))
        assertEquals(43, resolved.getInt("item2.b.c"))
        assertEquals(100, resolved.getInt("defaults.a"))
    }

    private val delayedMergeObjectResolveProblem4 = {
        parseObject("""
  defaults {
    a = 1
    b = 2
  }

  item1.b = 7
  // make item1 into a ConfigDelayedMerge
  item1 = ${defaults}
  // be sure we can resolve a substitution to a value in
  // a delayed-merge object.
  item2.b = ${item1.b}
""")
    }

    @Test
    def avoidDelayedMergeObjectResolveProblem4() {
        // in this case we have a ConfigDelayedMerge not a ConfigDelayedMergeObject
        assertTrue(delayedMergeObjectResolveProblem4.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMerge])

        val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem4)

        assertEquals(2, resolved.getInt("item1.b"))
        assertEquals(2, resolved.getInt("item2.b"))
    }

    private val delayedMergeObjectResolveProblem5 = {
        parseObject("""
  defaults {
    a = ${item1.b} // tricky cycle - we won't see ${defaults}
                   // as we resolve this
    b = 2
  }

  item1.b = 7
  // make item1 into a ConfigDelayedMerge
  item1 = ${defaults}
  // be sure we can resolve a substitution to a value in
  // a delayed-merge object.
  item2.b = ${item1.b}
""")
    }

    @Test
    def avoidDelayedMergeObjectResolveProblem5() {
        // in this case we have a ConfigDelayedMerge not a ConfigDelayedMergeObject
        assertTrue(delayedMergeObjectResolveProblem5.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMerge])

        val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem5)

        assertEquals("item1.b", 2, resolved.getInt("item1.b"))
        assertEquals("item2.b", 2, resolved.getInt("item2.b"))
        assertEquals("defaults.a", 7, resolved.getInt("defaults.a"))
    }

    private val delayedMergeObjectResolveProblem6 = {
        parseObject("""
  z = 15
  defaults-defaults-defaults {
    m = ${z}
    n.o.p = ${z}
  }
  defaults-defaults {
    x = 10
    y = 11
    asdf = ${z}
  }
  defaults {
    a = 1
    b = 2
  }
  defaults-alias = ${defaults}
  // make item1 into a ConfigDelayedMergeObject several layers deep
  // that will NOT become resolved just because we resolve one path
  // through it.
  item1 = 345
  item1 = ${?NONEXISTENT}
  item1 = ${defaults-defaults-defaults}
  item1 = {}
  item1 = ${defaults-defaults}
  item1 = ${defaults-alias}
  item1 = ${defaults}
  item1.b = { c : 43 }
  item1.xyz = 101
  // be sure we can resolve a substitution to a value in
  // a delayed-merge object.
  item2.b = ${item1.b}
""")
    }

    @Test
    def avoidDelayedMergeObjectResolveProblem6() {
        assertTrue(delayedMergeObjectResolveProblem6.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])

        // should be able to attemptPeekWithPartialResolve() a known non-object without resolving
        assertEquals(101, delayedMergeObjectResolveProblem6.toConfig().getObject("item1").attemptPeekWithPartialResolve("xyz").unwrapped())

        val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem6)

        assertEquals(parseObject("{ c : 43 }"), resolved.getObject("item1.b"))
        assertEquals(43, resolved.getInt("item1.b.c"))
        assertEquals(43, resolved.getInt("item2.b.c"))
        assertEquals(15, resolved.getInt("item1.n.o.p"))
    }

    private val delayedMergeObjectWithKnownValue = {
        parseObject("""
  defaults {
    a = 1
    b = 2
  }
  // make item1 into a ConfigDelayedMergeObject
  item1 = ${defaults}
  // note that we'll resolve to a non-object value
  // so item1.b will ignoreFallbacks and not depend on
  // ${defaults}
  item1.b = 3
""")
    }

    @Test
    def fetchKnownValueFromDelayedMergeObject() {
        assertTrue(delayedMergeObjectWithKnownValue.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])

        assertEquals(3, delayedMergeObjectWithKnownValue.toConfig.getConfig("item1").getInt("b"))
    }

    private val delayedMergeObjectNeedsFullResolve = {
        parseObject("""
  defaults {
    a = 1
    b = { c : 31 }
  }
  item1 = ${defaults}
  // because b is an object, fetching it requires resolving ${defaults} above
  // to see if there are more keys to merge with b.
  item1.b = { c : 41 }
""")
    }

    @Test
    def failToFetchFromDelayedMergeObjectNeedsFullResolve() {
        assertTrue(delayedMergeObjectWithKnownValue.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])

        val e = intercept[ConfigException.NotResolved] {
            delayedMergeObjectNeedsFullResolve.toConfig().getObject("item1.b")
        }

        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("item1.b"))
    }

    // objects that mutually refer to each other
    private val delayedMergeObjectEmbrace = {
        parseObject("""
  defaults {
    a = 1
    b = 2
  }

  item1 = ${defaults}
  // item1.c refers to a field in item2 that refers to item1
  item1.c = ${item2.d}
  // item1.x refers to a field in item2 that doesn't go back to item1
  item1.x = ${item2.y}

  item2 = ${defaults}
  // item2.d refers to a field in item1
  item2.d = ${item1.a}
  item2.y = 15
""")
    }

    @Test
    def resolveDelayedMergeObjectEmbrace() {
        assertTrue(delayedMergeObjectEmbrace.attemptPeekWithPartialResolve("item1").isInstanceOf[ConfigDelayedMergeObject])
        assertTrue(delayedMergeObjectEmbrace.attemptPeekWithPartialResolve("item2").isInstanceOf[ConfigDelayedMergeObject])

        val resolved = delayedMergeObjectEmbrace.toConfig.resolve()
        assertEquals(1, resolved.getInt("item1.c"))
        assertEquals(1, resolved.getInt("item2.d"))
        assertEquals(15, resolved.getInt("item1.x"))
    }

    // objects that mutually refer to each other
    private val plainObjectEmbrace = {
        parseObject("""
  item1.a = 10
  item1.b = ${item2.d}
  item2.c = 12
  item2.d = 14
  item2.e = ${item1.a}
  item2.f = ${item1.b}   // item1.b goes back to item2
  item2.g = ${item2.f}   // goes back to ourselves
""")
    }

    @Test
    def resolvePlainObjectEmbrace() {
        assertTrue(plainObjectEmbrace.attemptPeekWithPartialResolve("item1").isInstanceOf[SimpleConfigObject])
        assertTrue(plainObjectEmbrace.attemptPeekWithPartialResolve("item2").isInstanceOf[SimpleConfigObject])

        val resolved = plainObjectEmbrace.toConfig.resolve()
        assertEquals(14, resolved.getInt("item1.b"))
        assertEquals(10, resolved.getInt("item2.e"))
        assertEquals(14, resolved.getInt("item2.f"))
        assertEquals(14, resolved.getInt("item2.g"))
    }

    @Test
    def useRelativeToSameFileWhenRelativized() {
        val child = parseObject("""foo=in child,bar=${foo}""")

        val values = new java.util.HashMap[String, AbstractConfigValue]()

        values.put("a", child.relativized(new Path("a")))
        // this "foo" should NOT be used.
        values.put("foo", stringValue("in parent"));

        val resolved = resolve(new SimpleConfigObject(fakeOrigin(), values));

        assertEquals("in child", resolved.getString("a.bar"))
    }

    @Test
    def useRelativeToRootWhenRelativized() {
        // here, "foo" is not defined in the child
        val child = parseObject("""bar=${foo}""")

        val values = new java.util.HashMap[String, AbstractConfigValue]()

        values.put("a", child.relativized(new Path("a")))
        // so this "foo" SHOULD be used
        values.put("foo", stringValue("in parent"));

        val resolved = resolve(new SimpleConfigObject(fakeOrigin(), values));

        assertEquals("in parent", resolved.getString("a.bar"))
    }

    private val substComplexObject = {
        parseObject("""
{
    "foo" : ${bar},
    "bar" : ${a.b.c},
    "a" : { "b" : { "c" : 57, "d" : ${foo}, "e" : { "f" : ${foo} } } },
    "objA" : ${a},
    "objB" : ${a.b},
    "objE" : ${a.b.e},
    "foo.bar" : 37,
    "arr" : [ ${foo}, ${a.b.c}, ${"foo.bar"}, ${objB.d}, ${objA.b.e.f}, ${objE.f} ],
    "ptrToArr" : ${arr},
    "x" : { "y" : { "ptrToPtrToArr" : ${ptrToArr} } }
}
""")
    }

    @Test
    def complexResolve() {
        import scala.collection.JavaConverters._

        val resolved = resolveWithoutFallbacks(substComplexObject)

        assertEquals(57, resolved.getInt("foo"))
        assertEquals(57, resolved.getInt("bar"))
        assertEquals(57, resolved.getInt("a.b.c"))
        assertEquals(57, resolved.getInt("a.b.d"))
        assertEquals(57, resolved.getInt("objB.d"))
        assertEquals(Seq(57, 57, 37, 57, 57, 57), resolved.getIntList("arr").asScala)
        assertEquals(Seq(57, 57, 37, 57, 57, 57), resolved.getIntList("ptrToArr").asScala)
        assertEquals(Seq(57, 57, 37, 57, 57, 57), resolved.getIntList("x.y.ptrToPtrToArr").asScala)
    }

    private val substSystemPropsObject = {
        parseObject("""
{
    "a" : ${configtest.a},
    "b" : ${configtest.b}
}
""")
    }

    @Test
    def doNotSerializeUnresolvedObject() {
        checkNotSerializable(substComplexObject)
    }

    // this is a weird test, it used to test fallback to system props which made more sense.
    // Now it just tests that if you override with system props, you can use system props
    // in substitutions.
    @Test
    def overrideWithSystemProps() {
        System.setProperty("configtest.a", "1234")
        System.setProperty("configtest.b", "5678")
        ConfigImpl.reloadSystemPropertiesConfig()

        val resolved = resolve(ConfigFactory.systemProperties().withFallback(substSystemPropsObject).root.asInstanceOf[AbstractConfigObject])

        assertEquals("1234", resolved.getString("a"))
        assertEquals("5678", resolved.getString("b"))
    }

    private val substEnvVarObject = {
        parseObject("""
{
    "home" : ${?HOME},
    "pwd" : ${?PWD},
    "shell" : ${?SHELL},
    "lang" : ${?LANG},
    "path" : ${?PATH},
    "not_here" : ${?NOT_HERE}
}
""")
    }

    @Test
    def fallbackToEnv() {
        import scala.collection.JavaConverters._

        val resolved = resolve(substEnvVarObject)

        var existed = 0
        for (k <- resolved.root.keySet().asScala) {
            val e = System.getenv(k.toUpperCase());
            if (e != null) {
                existed += 1
                assertEquals(e, resolved.getString(k))
            } else {
                assertNull(resolved.root.get(k))
            }
        }
        if (existed == 0) {
            throw new Exception("None of the env vars we tried to use for testing were set")
        }
    }

    @Test
    def noFallbackToEnvIfValuesAreNull() {
        import scala.collection.JavaConverters._

        // create a fallback object with all the env var names
        // set to null. we want to be sure this blocks
        // lookup in the environment. i.e. if there is a
        // { HOME : null } then ${HOME} should be null.
        val nullsMap = new java.util.HashMap[String, Object]
        for (k <- substEnvVarObject.keySet().asScala) {
            nullsMap.put(k.toUpperCase(), null);
        }
        val nulls = ConfigFactory.parseMap(nullsMap, "nulls map")

        val resolved = resolve(substEnvVarObject.withFallback(nulls))

        for (k <- resolved.root.keySet().asScala) {
            assertNotNull(resolved.root.get(k))
            assertEquals(nullValue, resolved.root.get(k))
        }
    }

    @Test
    def fallbackToEnvWhenRelativized() {
        import scala.collection.JavaConverters._

        val values = new java.util.HashMap[String, AbstractConfigValue]()

        values.put("a", substEnvVarObject.relativized(new Path("a")))

        val resolved = resolve(new SimpleConfigObject(fakeOrigin(), values));

        var existed = 0
        for (k <- resolved.getObject("a").keySet().asScala) {
            val e = System.getenv(k.toUpperCase());
            if (e != null) {
                existed += 1
                assertEquals(e, resolved.getConfig("a").getString(k))
            } else {
                assertNull(resolved.getObject("a").get(k))
            }
        }
        if (existed == 0) {
            throw new Exception("None of the env vars we tried to use for testing were set")
        }
    }

    @Test
    def throwWhenEnvNotFound() {
        val obj = parseObject("""{ a : ${NOT_HERE} }""")
        intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
    }

    @Test
    def optionalOverrideNotProvided() {
        val obj = parseObject("""{ a: 42, a : ${?NOT_HERE} }""")
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("a"))
    }

    @Test
    def optionalOverrideProvided() {
        val obj = parseObject("""{ HERE : 43, a: 42, a : ${?HERE} }""")
        val resolved = resolve(obj)
        assertEquals(43, resolved.getInt("a"))
    }

    @Test
    def optionalOverrideOfObjectNotProvided() {
        val obj = parseObject("""{ a: { b : 42 }, a : ${?NOT_HERE} }""")
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("a.b"))
    }

    @Test
    def optionalOverrideOfObjectProvided() {
        val obj = parseObject("""{ HERE : 43, a: { b : 42 }, a : ${?HERE} }""")
        val resolved = resolve(obj)
        assertEquals(43, resolved.getInt("a"))
        assertFalse(resolved.hasPath("a.b"))
    }

    @Test
    def optionalVanishesFromArray() {
        import scala.collection.JavaConverters._
        val obj = parseObject("""{ a : [ 1, 2, 3, ${?NOT_HERE} ] }""")
        val resolved = resolve(obj)
        assertEquals(Seq(1, 2, 3), resolved.getIntList("a").asScala)
    }

    @Test
    def optionalUsedInArray() {
        import scala.collection.JavaConverters._
        val obj = parseObject("""{ HERE: 4, a : [ 1, 2, 3, ${?HERE} ] }""")
        val resolved = resolve(obj)
        assertEquals(Seq(1, 2, 3, 4), resolved.getIntList("a").asScala)
    }

    @Test
    def substSelfReference() {
        val obj = parseObject("""a=1, a=${a}""")
        val resolved = resolve(obj)
        assertEquals(1, resolved.getInt("a"))
    }

    @Test
    def substSelfReferenceUndefined() {
        val obj = parseObject("""a=${a}""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("cycle"))
    }

    @Test
    def substSelfReferenceOptional() {
        val obj = parseObject("""a=${?a}""")
        val resolved = resolve(obj)
        assertEquals("optional self reference disappears", 0, resolved.root.size)
    }

    @Test
    def substSelfReferenceAlongPath() {
        val obj = parseObject("""a.b=1, a.b=${a.b}""")
        val resolved = resolve(obj)
        assertEquals(1, resolved.getInt("a.b"))
    }

    @Test
    def substSelfReferenceAlongLongerPath() {
        val obj = parseObject("""a.b.c=1, a.b.c=${a.b.c}""")
        val resolved = resolve(obj)
        assertEquals(1, resolved.getInt("a.b.c"))
    }

    @Test
    def substSelfReferenceAlongPathMoreComplex() {
        // this is an example from the spec
        val obj = parseObject("""
    foo : { a : { c : 1 } }
    foo : ${foo.a}
    foo : { a : 2 }
                """)
        val resolved = resolve(obj)
        assertEquals(1, resolved.getInt("foo.c"))
        assertEquals(2, resolved.getInt("foo.a"))
    }

    @Test
    def substSelfReferenceIndirect() {
        val obj = parseObject("""a=1, b=${a}, a=${b}""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("cycle"))
    }

    @Test
    def substSelfReferenceDoubleIndirect() {
        val obj = parseObject("""a=1, b=${c}, c=${a}, a=${b}""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("cycle"))
    }

    @Test
    def substSelfReferenceIndirectStackCycle() {
        // this situation is undefined, depends on
        // whether we resolve a or b first.
        val obj = parseObject("""a=1, b={c=5}, b=${a}, a=${b}""")
        val resolved = resolve(obj)
        val option1 = parseObject(""" b={c=5}, a={c=5} """).toConfig()
        val option2 = parseObject(""" b=1, a=1 """).toConfig()
        assertTrue("not an expected possibility: " + resolved +
            " expected 1: " + option1 + " or 2: " + option2,
            resolved == option1 || resolved == option2)
    }

    @Test
    def substSelfReferenceObject() {
        val obj = parseObject("""a={b=5}, a=${a}""")
        val resolved = resolve(obj)
        assertEquals(5, resolved.getInt("a.b"))
    }

    @Test
    def substSelfReferenceObjectAlongPath() {
        val obj = parseObject("""a.b={c=5}, a.b=${a.b}""")
        val resolved = resolve(obj)
        assertEquals(5, resolved.getInt("a.b.c"))
    }

    @Test
    def substSelfReferenceInConcat() {
        val obj = parseObject("""a=1, a=${a}foo""")
        val resolved = resolve(obj)
        assertEquals("1foo", resolved.getString("a"))
    }

    @Test
    def substSelfReferenceIndirectInConcat() {
        // this situation is undefined, depends on
        // whether we resolve a or b first. If b first
        // then there's an error because ${a} is undefined.
        // if a first then b=1foo and a=1foo.
        val obj = parseObject("""a=1, b=${a}foo, a=${b}""")
        val either = try {
            Left(resolve(obj))
        } catch {
            case e: ConfigException.UnresolvedSubstitution =>
                Right(e)
        }
        val option1 = Left(parseObject("""a:1foo,b:1foo""").toConfig)
        assertTrue("not an expected possibility: " + either +
            " expected value " + option1 + " or an exception",
            either == option1 || either.isRight)
    }

    @Test
    def substOptionalSelfReferenceInConcat() {
        val obj = parseObject("""a=${?a}foo""")
        val resolved = resolve(obj)
        assertEquals("foo", resolved.getString("a"))
    }

    @Test
    def substOptionalIndirectSelfReferenceInConcat() {
        val obj = parseObject("""a=${?b}foo,b=${a}""")
        val resolved = resolve(obj)
        assertEquals("foo", resolved.getString("a"))
    }

    @Test
    def substTwoOptionalSelfReferencesInConcat() {
        val obj = parseObject("""a=${?a}foo${?a}""")
        val resolved = resolve(obj)
        assertEquals("foo", resolved.getString("a"))
    }

    @Test
    def substTwoOptionalSelfReferencesInConcatWithPriorValue() {
        val obj = parseObject("""a=1,a=${?a}foo${?a}""")
        val resolved = resolve(obj)
        assertEquals("1foo1", resolved.getString("a"))
    }

    @Test
    def substSelfReferenceMiddleOfStack() {
        val obj = parseObject("""a=1, a=${a}, a=2""")
        val resolved = resolve(obj)
        // the substitution would be 1, but then 2 overrides
        assertEquals(2, resolved.getInt("a"))
    }

    @Test
    def substSelfReferenceObjectMiddleOfStack() {
        val obj = parseObject("""a={b=5}, a=${a}, a={c=6}""")
        val resolved = resolve(obj)
        assertEquals(5, resolved.getInt("a.b"))
        assertEquals(6, resolved.getInt("a.c"))
    }

    @Test
    def substOptionalSelfReferenceMiddleOfStack() {
        val obj = parseObject("""a=1, a=${?a}, a=2""")
        val resolved = resolve(obj)
        // the substitution would be 1, but then 2 overrides
        assertEquals(2, resolved.getInt("a"))
    }

    @Test
    def substSelfReferenceBottomOfStack() {
        // self-reference should just be ignored since it's
        // overridden
        val obj = parseObject("""a=${a}, a=1, a=2""")
        val resolved = resolve(obj)
        assertEquals(2, resolved.getInt("a"))
    }

    @Test
    def substOptionalSelfReferenceBottomOfStack() {
        val obj = parseObject("""a=${?a}, a=1, a=2""")
        val resolved = resolve(obj)
        assertEquals(2, resolved.getInt("a"))
    }

    @Test
    def substSelfReferenceTopOfStack() {
        val obj = parseObject("""a=1, a=2, a=${a}""")
        val resolved = resolve(obj)
        assertEquals(2, resolved.getInt("a"))
    }

    @Test
    def substOptionalSelfReferenceTopOfStack() {
        val obj = parseObject("""a=1, a=2, a=${?a}""")
        val resolved = resolve(obj)
        assertEquals(2, resolved.getInt("a"))
    }

    @Test
    def substSelfReferenceAlongAPath() {
        // ${a} in the middle of the stack means "${a} in the stack
        // below us" and so ${a.b} means b inside the "${a} below us"
        // not b inside the final "${a}"
        val obj = parseObject("""a={b={c=5}}, a=${a.b}, a={b=2}""")
        val resolved = resolve(obj)
        assertEquals(5, resolved.getInt("a.c"))
    }

    @Test
    def substSelfReferenceAlongAPathInsideObject() {
        // if the ${a.b} is _inside_ a field value instead of
        // _being_ the field value, it does not look backward.
        val obj = parseObject("""a={b={c=5}}, a={ x : ${a.b} }, a={b=2}""")
        val resolved = resolve(obj)
        assertEquals(2, resolved.getInt("a.x"))
    }

    @Test
    def substInChildFieldNotASelfReference1() {
        // here, ${bar.foo} is not a self reference because
        // it's the value of a child field of bar, not bar
        // itself; so we use bar's current value, rather than
        // looking back in the merge stack
        val obj = parseObject("""
         bar : { foo : 42,
                 baz : ${bar.foo}
         }
            """)
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("bar.baz"))
        assertEquals(42, resolved.getInt("bar.foo"))
    }

    @Test
    def substInChildFieldNotASelfReference2() {
        // checking that having bar.foo later in the stack
        // doesn't break the behavior
        val obj = parseObject("""
         bar : { foo : 42,
                 baz : ${bar.foo}
         }
         bar : { foo : 43 }
            """)
        val resolved = resolve(obj)
        assertEquals(43, resolved.getInt("bar.baz"))
        assertEquals(43, resolved.getInt("bar.foo"))
    }

    @Test
    def substInChildFieldNotASelfReference3() {
        // checking that having bar.foo earlier in the merge
        // stack doesn't break the behavior.
        val obj = parseObject("""
         bar : { foo : 43 }
         bar : { foo : 42,
                 baz : ${bar.foo}
         }
            """)
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("bar.baz"))
        assertEquals(42, resolved.getInt("bar.foo"))
    }

    @Test
    def substInChildFieldNotASelfReference4() {
        // checking that having bar set to non-object earlier
        // doesn't break the behavior.
        val obj = parseObject("""
         bar : 101
         bar : { foo : 42,
                 baz : ${bar.foo}
         }
            """)
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("bar.baz"))
        assertEquals(42, resolved.getInt("bar.foo"))
    }

    @Test
    def substInChildFieldNotASelfReference5() {
        // checking that having bar set to unresolved array earlier
        // doesn't break the behavior.
        val obj = parseObject("""
         x : 0
         bar : [ ${x}, 1, 2, 3 ]
         bar : { foo : 42,
                 baz : ${bar.foo}
         }
            """)
        val resolved = resolve(obj)
        assertEquals(42, resolved.getInt("bar.baz"))
        assertEquals(42, resolved.getInt("bar.foo"))
    }

    @Test
    def mutuallyReferringNotASelfReference() {
        val obj = parseObject("""
    // bar.a should end up as 4
    bar : { a : ${foo.d}, b : 1 }
    bar.b = 3
    // foo.c should end up as 3
    foo : { c : ${bar.b}, d : 2 }
    foo.d = 4
                """)
        val resolved = resolve(obj)
        assertEquals(4, resolved.getInt("bar.a"))
        assertEquals(3, resolved.getInt("foo.c"))
    }

    @Test
    def substSelfReferenceMultipleTimes() {
        val obj = parseObject("""a=1,a=${a},a=${a},a=${a}""")
        val resolved = resolve(obj)
        assertEquals(1, resolved.getInt("a"))
    }

    @Test
    def substSelfReferenceInConcatMultipleTimes() {
        val obj = parseObject("""a=1,a=${a}x,a=${a}y,a=${a}z""")
        val resolved = resolve(obj)
        assertEquals("1xyz", resolved.getString("a"))
    }

    @Test
    def substSelfReferenceInArray() {
        // never "look back" from "inside" an array
        val obj = parseObject("""a=1,a=[${a}, 2]""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("cycle") && e.getMessage.contains("${a}"))
    }

    @Test
    def substSelfReferenceInObject() {
        // never "look back" from "inside" an object
        val obj = parseObject("""a=1,a={ x : ${a} }""")
        val e = intercept[ConfigException.UnresolvedSubstitution] {
            resolve(obj)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("cycle") && e.getMessage.contains("${a}"))
    }

    @Test
    def selfReferentialObjectNotAffectedByOverriding() {
        // this is testing that we can still refer to another
        // field in the same object, even though we are overriding
        // an earlier object.
        val obj = parseObject("""a={ x : 42, y : ${a.x} }""")
        val resolved = resolve(obj)
        assertEquals(parseObject("{ x : 42, y : 42 }"), resolved.getConfig("a").root)

        // this is expected because if adding "a=1" here affects the outcome,
        // it would be flat-out bizarre.
        val obj2 = parseObject("""a=1, a={ x : 42, y : ${a.x} }""")
        val resolved2 = resolve(obj2)
        assertEquals(parseObject("{ x : 42, y : 42 }"), resolved2.getConfig("a").root)
    }
}
