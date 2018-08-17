package com.typesafe.config.impl

import org.junit.Assert._
import org.junit.Test

class BadMapTest extends TestUtils {
    @Test
    def copyingPut(): Unit = {
        val map = new BadMap[String, String]()
        val copy = map.copyingPut("key", "value")

        assertNull(map.get("key"))
        assertEquals("value", copy.get("key"))
    }

    @Test
    def retrieveOldElement(): Unit = {
        val map = new BadMap[String, String]()
            .copyingPut("key1", "value1")
            .copyingPut("key2", "value2")
            .copyingPut("key3", "value3")

        assertEquals("value1", map.get("key1"))
        assertEquals("value2", map.get("key2"))
        assertEquals("value3", map.get("key3"))
    }

    @Test
    def putOverride(): Unit = {
        val map = new BadMap[String, String]()
            .copyingPut("key", "value1")
            .copyingPut("key", "value2")
            .copyingPut("key", "value3")

        assertEquals("value3", map.get("key"))
    }

    @Test
    def notFound(): Unit = {
        val map = new BadMap[String, String]()

        assertNull(map.get("invalid key"))
    }

    @Test
    def putMany(): Unit = {
        val entries = (1 to 1000).map(i => (s"key$i", s"value$i"))
        var map = new BadMap[String, String]()

        for ((key, value) <- entries) {
            map = map.copyingPut(key, value)
        }

        for ((key, value) <- entries) {
            assertEquals(value, map.get(key))
        }
    }

    @Test
    def putSameHash(): Unit = {
        val hash = 2
        val entries = (1 to 10).map(i => (new UniqueKeyWithHash(hash), s"value$i"))
        var map = new BadMap[UniqueKeyWithHash, String]()

        for ((key, value) <- entries) {
            map = map.copyingPut(key, value)
        }

        for ((key, value) <- entries) {
            assertEquals(value, map.get(key))
        }
    }

    @Test
    def putSameHashModLength(): Unit = {
        // given that the table will eventually be the following size, we insert entries who should
        // eventually all share the same index and then later be redistributed once rehashed
        val size = 11
        val entries = (1 to size * 2).map(i => (new UniqueKeyWithHash(size * i), s"value$i"))
        var map = new BadMap[UniqueKeyWithHash, String]()

        for ((key, value) <- entries) {
            map = map.copyingPut(key, value)
        }

        for ((key, value) <- entries) {
            assertEquals(value, map.get(key))
        }
    }

    private class UniqueKeyWithHash(hash: Int) {
        override def hashCode(): Int = hash
    }
}
