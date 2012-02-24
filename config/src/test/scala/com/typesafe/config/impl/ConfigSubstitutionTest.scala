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
        SubstitutionResolver.resolve(v, v, options).asInstanceOf[AbstractConfigObject].toConfig
    }
    private def resolveWithoutFallbacks(s: ConfigSubstitution, root: AbstractConfigObject) = {
        val options = ConfigResolveOptions.noSystem()
        SubstitutionResolver.resolve(s, root, options)
    }

    private def resolve(v: AbstractConfigObject) = {
        val options = ConfigResolveOptions.defaults()
        SubstitutionResolver.resolve(v, v, options).asInstanceOf[AbstractConfigObject].toConfig
    }
    private def resolve(s: ConfigSubstitution, root: AbstractConfigObject) = {
        val options = ConfigResolveOptions.defaults()
        SubstitutionResolver.resolve(s, root, options)
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
        intercept[ConfigException.UnresolvedSubstitution] {
            val s = subst("bar.missing")
            val v = resolveWithoutFallbacks(s, simpleObject)
        }
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
        val e = intercept[ConfigException.BadValue] {
            val v = resolveWithoutFallbacks(s, substCycleObject)
        }
        assertTrue(e.getMessage().contains("cycle"))
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
}
