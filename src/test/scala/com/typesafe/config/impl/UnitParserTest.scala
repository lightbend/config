/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config._
import java.util.concurrent.TimeUnit

class UnitParserTest extends TestUtils {

    @Test
    def parseDuration() {
        val oneSecs = List("1s", "1 s", "1seconds", "1 seconds", "   1s    ", "   1    s   ",
            "1second",
            "1000", "1000ms", "1000 ms", "1000   milliseconds", "   1000       milliseconds    ",
            "1000millisecond",
            "1000000us", "1000000   us", "1000000 microseconds", "1000000microsecond",
            "1000000000ns", "1000000000 ns", "1000000000  nanoseconds", "1000000000nanosecond",
            "0.01666666666666666666666m", "0.01666666666666666666666 minutes", "0.01666666666666666666666 minute",
            "0.00027777777777777777777h", "0.00027777777777777777777 hours", "0.00027777777777777777777hour",
            "1.1574074074074073e-05d", "1.1574074074074073e-05  days", "1.1574074074074073e-05day")
        val oneSecInNanos = TimeUnit.SECONDS.toNanos(1)
        for (s <- oneSecs) {
            val result = SimpleConfig.parseDuration(s, fakeOrigin(), "test")
            assertEquals(oneSecInNanos, result)
        }

        // bad units
        val e = intercept[ConfigException.BadValue] {
            SimpleConfig.parseDuration("100 dollars", fakeOrigin(), "test")
        }
        assertTrue(e.getMessage().contains("time unit"))

        // bad number
        val e2 = intercept[ConfigException.BadValue] {
            SimpleConfig.parseDuration("1 00 seconds", fakeOrigin(), "test")
        }
        assertTrue(e2.getMessage().contains("duration number"))
    }

    @Test
    def parseMemorySizeInBytes() {
        val oneMegs = List("1048576", "1048576b", "1048576bytes", "1048576byte",
            "1048576  b", "1048576  bytes",
            "    1048576  b   ", "  1048576  bytes   ",
            "1048576B",
            "1024k", "1024K", "1024 kilobytes", "1024 kilobyte",
            "1m", "1M", "1 M", "1 megabytes", "1 megabyte",
            "0.0009765625g", "0.0009765625G", "0.0009765625 gigabytes", "0.0009765625 gigabyte")

        def parseMem(s: String) = SimpleConfig.parseMemorySizeInBytes(s, fakeOrigin(), "test")

        for (s <- oneMegs) {
            val result = parseMem(s)
            assertEquals(1024 * 1024, result)
        }

        assertEquals(1024 * 1024 * 1024 * 1024, parseMem("1t"))
        assertEquals(1024 * 1024 * 1024 * 1024, parseMem(" 1 T "))
        assertEquals(1024 * 1024 * 1024 * 1024, parseMem("1 terabyte"))
        assertEquals(1024 * 1024 * 1024 * 1024, parseMem(" 1  terabytes  "))

        // bad units
        val e = intercept[ConfigException.BadValue] {
            SimpleConfig.parseMemorySizeInBytes("100 dollars", fakeOrigin(), "test")
        }
        assertTrue(e.getMessage().contains("size unit"))

        // bad number
        val e2 = intercept[ConfigException.BadValue] {
            SimpleConfig.parseMemorySizeInBytes("1 00 bytes", fakeOrigin(), "test")
        }
        assertTrue(e2.getMessage().contains("size number"))
    }
}
