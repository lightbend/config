package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import java.util.Collections
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigObject

class ConfigValueTest extends TestUtils {

    @Test
    def configIntEquality() {
        val a = new ConfigInt(fakeOrigin(), 42)
        val sameAsA = new ConfigInt(fakeOrigin(), 42)
        val b = new ConfigInt(fakeOrigin(), 43)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configLongEquality() {
        val a = new ConfigLong(fakeOrigin(), Integer.MAX_VALUE + 42L)
        val sameAsA = new ConfigLong(fakeOrigin(), Integer.MAX_VALUE + 42L)
        val b = new ConfigLong(fakeOrigin(), Integer.MAX_VALUE + 43L)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configIntAndLongEquality() {
        val longValue = new ConfigLong(fakeOrigin(), 42L)
        val intValue = new ConfigLong(fakeOrigin(), 42)
        val longValueB = new ConfigLong(fakeOrigin(), 43L)
        val intValueB = new ConfigLong(fakeOrigin(), 43)

        checkEqualObjects(intValue, longValue)
        checkEqualObjects(intValueB, longValueB)
        checkNotEqualObjects(intValue, longValueB)
        checkNotEqualObjects(intValueB, longValue)
    }

    private def configMap(pairs: (String, Int)*): java.util.Map[String, AbstractConfigValue] = {
        val m = new java.util.HashMap[String, AbstractConfigValue]()
        for (p <- pairs) {
            m.put(p._1, new ConfigInt(fakeOrigin(), p._2))
        }
        m
    }

    @Test
    def configObjectEquality() {
        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val sameAsAMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val bMap = configMap("a" -> 3, "b" -> 4, "c" -> 5)
        val a = new SimpleConfigObject(fakeOrigin(), null, aMap)
        val sameAsA = new SimpleConfigObject(fakeOrigin(), null, sameAsAMap)
        val b = new SimpleConfigObject(fakeOrigin(), null, bMap)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configSubstitutionEquality() {
        val a = subst("foo")
        val sameAsA = subst("foo")
        val b = subst("bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def valuesToString() {
        // just check that these don't throw, the exact output
        // isn't super important since it's just for debugging
        intValue(10).toString()
        longValue(11).toString()
        doubleValue(3.14).toString()
        stringValue("hi").toString()
        nullValue().toString()
        boolValue(true).toString()
        (new SimpleConfigObject(fakeOrigin(), null, Collections.emptyMap[String, AbstractConfigValue]())).toString()
        (new ConfigList(fakeOrigin(), Collections.emptyList[AbstractConfigValue]())).toString()
        subst("a").toString()
        substInString("b").toString()
    }

    private def unsupported(body: => Unit) {
        intercept[UnsupportedOperationException] {
            body
        }
    }

    @Test
    def configObjectUnwraps() {
        val m = new SimpleConfigObject(fakeOrigin(), null, configMap("a" -> 1, "b" -> 2, "c" -> 3))
        assertEquals(Map("a" -> 1, "b" -> 2, "c" -> 3), m.unwrapped().asScala)
    }

    @Test
    def configObjectImplementsMap() {
        val m: ConfigObject = new SimpleConfigObject(fakeOrigin(), null, configMap("a" -> 1, "b" -> 2, "c" -> 3))

        assertEquals(intValue(1), m.get("a"))
        assertEquals(intValue(2), m.get("b"))
        assertEquals(intValue(3), m.get("c"))
        assertNull(m.get("d"))

        assertTrue(m.containsKey("a"))
        assertFalse(m.containsKey("z"))

        assertTrue(m.containsValue(intValue(1)))
        assertFalse(m.containsValue(intValue(10)))

        assertFalse(m.isEmpty())

        assertEquals(3, m.size())

        val values = Set(intValue(1), intValue(2), intValue(3))
        assertEquals(values, m.values().asScala.toSet)
        assertEquals(values, m.entrySet().asScala map { _.getValue() } toSet)

        val keys = Set("a", "b", "c")
        assertEquals(keys, m.keySet().asScala.toSet)
        assertEquals(keys, m.entrySet().asScala map { _.getKey() } toSet)

        unsupported { m.clear() }
        unsupported { m.put("hello", intValue(42)) }
        unsupported { m.putAll(Collections.emptyMap[String, AbstractConfigValue]()) }
        unsupported { m.remove("a") }
    }
}
