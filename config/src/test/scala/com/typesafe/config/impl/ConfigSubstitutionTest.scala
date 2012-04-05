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
    private def resolveWithoutFallbacks(s: ConfigSubstitution, root: AbstractConfigObject) = {
        val options = ConfigResolveOptions.noSystem()
        ResolveContext.resolve(s, root, options)
    }

    private def resolve(v: AbstractConfigObject) = {
        val options = ConfigResolveOptions.defaults()
        ResolveContext.resolve(v, v, options).asInstanceOf[AbstractConfigObject].toConfig
    }
    private def resolve(s: ConfigSubstitution, root: AbstractConfigObject) = {
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
    a = ${item1.b} // tricky cycle
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

        assertEquals(2, resolved.getInt("item1.b"))
        assertEquals(2, resolved.getInt("item2.b"))
        assertEquals(2, resolved.getInt("defaults.a"))
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
    def deserializeOldUnresolvedObject() {
        val expectedSerialization = "" +
            "aced00057372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c6543" +
            "6f6e6669674f626a65637400000000000000010200035a001069676e6f72657346616c6c6261636b" +
            "735a00087265736f6c7665644c000576616c756574000f4c6a6176612f7574696c2f4d61703b7872" +
            "002d636f6d2e74797065736166652e636f6e6669672e696d706c2e4162737472616374436f6e6669" +
            "674f626a65637400000000000000010200014c0006636f6e6669677400274c636f6d2f7479706573" +
            "6166652f636f6e6669672f696d706c2f53696d706c65436f6e6669673b7872002c636f6d2e747970" +
            "65736166652e636f6e6669672e696d706c2e4162737472616374436f6e66696756616c7565000000" +
            "00000000010200014c00066f726967696e74002d4c636f6d2f74797065736166652f636f6e666967" +
            "2f696d706c2f53696d706c65436f6e6669674f726967696e3b78707372002b636f6d2e7479706573" +
            "6166652e636f6e6669672e696d706c2e53696d706c65436f6e6669674f726967696e000000000000" +
            "000102000649000d656e644c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d" +
            "6d656e74734f724e756c6c7400104c6a6176612f7574696c2f4c6973743b4c000b64657363726970" +
            "74696f6e7400124c6a6176612f6c616e672f537472696e673b4c000a6f726967696e547970657400" +
            "254c636f6d2f74797065736166652f636f6e6669672f696d706c2f4f726967696e547970653b4c00" +
            "0975726c4f724e756c6c71007e0009787000000002000000027074000b7465737420737472696e67" +
            "7e720023636f6d2e74797065736166652e636f6e6669672e696d706c2e4f726967696e5479706500" +
            "000000000000001200007872000e6a6176612e6c616e672e456e756d000000000000000012000078" +
            "7074000747454e455249437073720025636f6d2e74797065736166652e636f6e6669672e696d706c" +
            "2e53696d706c65436f6e66696700000000000000010200014c00066f626a65637474002f4c636f6d" +
            "2f74797065736166652f636f6e6669672f696d706c2f4162737472616374436f6e6669674f626a65" +
            "63743b787071007e00060000737200116a6176612e7574696c2e486173684d61700507dac1c31660" +
            "d103000246000a6c6f6164466163746f724900097468726573686f6c6478703f4000000000000c77" +
            "08000000100000000a7400046f626a457372002b636f6d2e74797065736166652e636f6e6669672e" +
            "696d706c2e436f6e666967537562737469747574696f6e00000000000000010200035a001069676e" +
            "6f72657346616c6c6261636b7349000c7072656669784c656e6774684c000670696563657371007e" +
            "00087871007e00047371007e000700000008000000087071007e000c71007e000f70000000000073" +
            "7200146a6176612e7574696c2e4c696e6b65644c6973740c29535d4a608822030000787077040000" +
            "00017372002f636f6d2e74797065736166652e636f6e6669672e696d706c2e537562737469747574" +
            "696f6e45787072657373696f6e00000000000000010200025a00086f7074696f6e616c4c00047061" +
            "746874001f4c636f6d2f74797065736166652f636f6e6669672f696d706c2f506174683b78700073" +
            "72001d636f6d2e74797065736166652e636f6e6669672e696d706c2e506174680000000000000001" +
            "0200024c0005666972737471007e00094c000972656d61696e64657271007e001d78707400016173" +
            "71007e001f740001627371007e001f740001657078740007666f6f2e62617273720022636f6d2e74" +
            "797065736166652e636f6e6669672e696d706c2e436f6e666967496e740000000000000001020001" +
            "49000576616c756578720025636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e" +
            "6669674e756d62657200000000000000010200014c000c6f726967696e616c5465787471007e0009" +
            "7871007e00047371007e000700000009000000097071007e000c71007e000f707400023337000000" +
            "257400046f626a427371007e00177371007e000700000007000000077071007e000c71007e000f70" +
            "00000000007371007e001a7704000000017371007e001c007371007e001f740001617371007e001f" +
            "74000162707874000361727273720029636f6d2e74797065736166652e636f6e6669672e696d706c" +
            "2e53696d706c65436f6e6669674c69737400000000000000010200025a00087265736f6c7665644c" +
            "000576616c756571007e00087871007e00047371007e00070000000a0000000a7071007e000c7100" +
            "7e000f70007371007e001a7704000000067371007e00177371007e00070000000a0000000a707100" +
            "7e000c71007e000f7000000000007371007e001a7704000000017371007e001c007371007e001f74" +
            "0003666f6f70787371007e001771007e003b00000000007371007e001a7704000000017371007e00" +
            "1c007371007e001f740001617371007e001f740001627371007e001f7400016370787371007e0017" +
            "71007e003b00000000007371007e001a7704000000017371007e001c007371007e001f740007666f" +
            "6f2e62617270787371007e001771007e003b00000000007371007e001a7704000000017371007e00" +
            "1c007371007e001f7400046f626a427371007e001f7400016470787371007e001771007e003b0000" +
            "0000007371007e001a7704000000017371007e001c007371007e001f7400046f626a417371007e00" +
            "1f740001627371007e001f740001657371007e001f7400016670787371007e001771007e003b0000" +
            "0000007371007e001a7704000000017371007e001c007371007e001f7400046f626a457371007e00" +
            "1f740001667078787400046f626a417371007e00177371007e000700000006000000067071007e00" +
            "0c71007e000f7000000000007371007e001a7704000000017371007e001c007371007e001f740001" +
            "617078740001617371007e00007371007e000700000005000000057071007e000c71007e000f7073" +
            "71007e001171007e006f00007371007e00143f4000000000000c7708000000100000000174000162" +
            "7371007e00007371007e000700000005000000057071007e000c71007e000f707371007e00117100" +
            "7e007400007371007e00143f4000000000000c77080000001000000003740001647371007e001773" +
            "71007e000700000005000000057071007e000c71007e000f7000000000007371007e001a77040000" +
            "00017371007e001c007371007e001f740003666f6f7078740001657371007e00007371007e000700" +
            "000005000000057071007e000c71007e000f707371007e001171007e008000007371007e00143f40" +
            "00000000000c77080000001000000001740001667371007e001771007e007a00000000007371007e" +
            "001a7704000000017371007e001c007371007e001f740003666f6f707878740001637371007e0027" +
            "71007e007a7400023537000000397878740003666f6f7371007e00177371007e0007000000030000" +
            "00037071007e000c71007e000f7000000000007371007e001a7704000000017371007e001c007371" +
            "007e001f7400036261727078740008707472546f4172727371007e00177371007e00070000000b00" +
            "00000b7071007e000c71007e000f7000000000007371007e001a7704000000017371007e001c0073" +
            "71007e001f74000361727270787400036261727371007e00177371007e0007000000040000000470" +
            "71007e000c71007e000f7000000000007371007e001a7704000000017371007e001c007371007e00" +
            "1f740001617371007e001f740001627371007e001f740001637078740001787371007e0000737100" +
            "7e00070000000c0000000c7071007e000c71007e000f707371007e001171007e00a700007371007e" +
            "00143f4000000000000c77080000001000000001740001797371007e00007371007e00070000000c" +
            "0000000c7071007e000c71007e000f707371007e001171007e00ac00007371007e00143f40000000" +
            "00000c7708000000100000000174000d707472546f507472546f4172727371007e00177371007e00" +
            "070000000c0000000c7071007e000c71007e000f7000000000007371007e001a7704000000017371" +
            "007e001c007371007e001f740008707472546f4172727078787878"

        checkSerializableOldFormat(expectedSerialization, substComplexObject)
    }

    @Test
    def serializeUnresolvedObject() {
        val expectedSerialization = "" +
            "aced00057372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c6543" +
            "6f6e6669674f626a65637400000000000000010200035a001069676e6f72657346616c6c6261636b" +
            "735a00087265736f6c7665644c000576616c756574000f4c6a6176612f7574696c2f4d61703b7872" +
            "002d636f6d2e74797065736166652e636f6e6669672e696d706c2e4162737472616374436f6e6669" +
            "674f626a65637400000000000000010200014c0006636f6e6669677400274c636f6d2f7479706573" +
            "6166652f636f6e6669672f696d706c2f53696d706c65436f6e6669673b7872002c636f6d2e747970" +
            "65736166652e636f6e6669672e696d706c2e4162737472616374436f6e66696756616c7565000000" +
            "00000000010200014c00066f726967696e74002d4c636f6d2f74797065736166652f636f6e666967" +
            "2f696d706c2f53696d706c65436f6e6669674f726967696e3b78707372002b636f6d2e7479706573" +
            "6166652e636f6e6669672e696d706c2e53696d706c65436f6e6669674f726967696e000000000000" +
            "000102000649000d656e644c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d" +
            "6d656e74734f724e756c6c7400104c6a6176612f7574696c2f4c6973743b4c000b64657363726970" +
            "74696f6e7400124c6a6176612f6c616e672f537472696e673b4c000a6f726967696e547970657400" +
            "254c636f6d2f74797065736166652f636f6e6669672f696d706c2f4f726967696e547970653b4c00" +
            "0975726c4f724e756c6c71007e0009787000000002000000027074000b7465737420737472696e67" +
            "7e720023636f6d2e74797065736166652e636f6e6669672e696d706c2e4f726967696e5479706500" +
            "000000000000001200007872000e6a6176612e6c616e672e456e756d000000000000000012000078" +
            "7074000747454e455249437073720025636f6d2e74797065736166652e636f6e6669672e696d706c" +
            "2e53696d706c65436f6e66696700000000000000010200014c00066f626a65637474002f4c636f6d" +
            "2f74797065736166652f636f6e6669672f696d706c2f4162737472616374436f6e6669674f626a65" +
            "63743b787071007e00060000737200116a6176612e7574696c2e486173684d61700507dac1c31660" +
            "d103000246000a6c6f6164466163746f724900097468726573686f6c6478703f4000000000000c77" +
            "08000000100000000a7400046f626a4573720028636f6d2e74797065736166652e636f6e6669672e" +
            "696d706c2e436f6e6669675265666572656e6365000000000000000102000249000c707265666978" +
            "4c656e6774684c0004657870727400314c636f6d2f74797065736166652f636f6e6669672f696d70" +
            "6c2f537562737469747574696f6e45787072657373696f6e3b7871007e00047371007e0007000000" +
            "08000000087071007e000c71007e000f70000000007372002f636f6d2e74797065736166652e636f" +
            "6e6669672e696d706c2e537562737469747574696f6e45787072657373696f6e0000000000000001" +
            "0200025a00086f7074696f6e616c4c00047061746874001f4c636f6d2f74797065736166652f636f" +
            "6e6669672f696d706c2f506174683b7870007372001d636f6d2e74797065736166652e636f6e6669" +
            "672e696d706c2e5061746800000000000000010200024c0005666972737471007e00094c00097265" +
            "6d61696e64657271007e001c7870740001617371007e001e740001627371007e001e740001657074" +
            "0007666f6f2e62617273720022636f6d2e74797065736166652e636f6e6669672e696d706c2e436f" +
            "6e666967496e74000000000000000102000149000576616c756578720025636f6d2e747970657361" +
            "66652e636f6e6669672e696d706c2e436f6e6669674e756d62657200000000000000010200014c00" +
            "0c6f726967696e616c5465787471007e00097871007e00047371007e000700000009000000097071" +
            "007e000c71007e000f707400023337000000257400046f626a427371007e00177371007e00070000" +
            "0007000000077071007e000c71007e000f70000000007371007e001b007371007e001e7400016173" +
            "71007e001e740001627074000361727273720029636f6d2e74797065736166652e636f6e6669672e" +
            "696d706c2e53696d706c65436f6e6669674c69737400000000000000010200025a00087265736f6c" +
            "7665644c000576616c756571007e00087871007e00047371007e00070000000a0000000a7071007e" +
            "000c71007e000f7000737200146a6176612e7574696c2e4c696e6b65644c6973740c29535d4a6088" +
            "2203000078707704000000067371007e00177371007e00070000000a0000000a7071007e000c7100" +
            "7e000f70000000007371007e001b007371007e001e740003666f6f707371007e001771007e003a00" +
            "0000007371007e001b007371007e001e740001617371007e001e740001627371007e001e74000163" +
            "707371007e001771007e003a000000007371007e001b007371007e001e740007666f6f2e62617270" +
            "7371007e001771007e003a000000007371007e001b007371007e001e7400046f626a427371007e00" +
            "1e74000164707371007e001771007e003a000000007371007e001b007371007e001e7400046f626a" +
            "417371007e001e740001627371007e001e740001657371007e001e74000166707371007e00177100" +
            "7e003a000000007371007e001b007371007e001e7400046f626a457371007e001e74000166707874" +
            "00046f626a417371007e00177371007e000700000006000000067071007e000c71007e000f700000" +
            "00007371007e001b007371007e001e7400016170740001617371007e00007371007e000700000005" +
            "000000057071007e000c71007e000f707371007e001171007e006700007371007e00143f40000000" +
            "00000c77080000001000000001740001627371007e00007371007e00070000000500000005707100" +
            "7e000c71007e000f707371007e001171007e006c00007371007e00143f4000000000000c77080000" +
            "001000000003740001647371007e00177371007e000700000005000000057071007e000c71007e00" +
            "0f70000000007371007e001b007371007e001e740003666f6f70740001657371007e00007371007e" +
            "000700000005000000057071007e000c71007e000f707371007e001171007e007700007371007e00" +
            "143f4000000000000c77080000001000000001740001667371007e001771007e0072000000007371" +
            "007e001b007371007e001e740003666f6f7078740001637371007e002671007e0072740002353700" +
            "0000397878740003666f6f7371007e00177371007e000700000003000000037071007e000c71007e" +
            "000f70000000007371007e001b007371007e001e74000362617270740008707472546f4172727371" +
            "007e00177371007e00070000000b0000000b7071007e000c71007e000f70000000007371007e001b" +
            "007371007e001e740003617272707400036261727371007e00177371007e00070000000400000004" +
            "7071007e000c71007e000f70000000007371007e001b007371007e001e740001617371007e001e74" +
            "0001627371007e001e7400016370740001787371007e00007371007e00070000000c0000000c7071" +
            "007e000c71007e000f707371007e001171007e009a00007371007e00143f4000000000000c770800" +
            "00001000000001740001797371007e00007371007e00070000000c0000000c7071007e000c71007e" +
            "000f707371007e001171007e009f00007371007e00143f4000000000000c77080000001000000001" +
            "74000d707472546f507472546f4172727371007e00177371007e00070000000c0000000c7071007e" +
            "000c71007e000f70000000007371007e001b007371007e001e740008707472546f41727270787878"
        checkSerializable(expectedSerialization, substComplexObject)
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
}
