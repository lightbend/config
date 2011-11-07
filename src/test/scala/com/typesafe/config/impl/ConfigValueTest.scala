package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue

class ConfigValueTest extends TestUtils {

    @org.junit.Before
    def setup() {
    }

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

    private def configMap(pairs: (String, Int)*): java.util.Map[String, ConfigValue] = {
        val m = new java.util.HashMap[String, ConfigValue]()
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
}
