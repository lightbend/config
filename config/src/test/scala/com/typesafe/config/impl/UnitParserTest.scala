/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.math.BigInteger
import java.time.{ LocalDate, Period }
import java.time.temporal.ChronoUnit

import org.junit.Assert._
import org.junit._
import com.typesafe.config._
import java.util.concurrent.TimeUnit

class UnitParserTest extends TestUtils {

    @Test
    def parseDuration(): Unit = {
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
        assertTrue(e.getMessage.contains("time unit"))

        // bad number
        val e2 = intercept[ConfigException.BadValue] {
            SimpleConfig.parseDuration("1 00 seconds", fakeOrigin(), "test")
        }
        assertTrue(e2.getMessage.contains("duration number"))
    }

    @Test
    def parsePeriod(): Unit = {
        val oneYears = List(
            "1y", "1 y", "1year", "1 years", "   1y   ", "   1   y    ",
            "365", "365d", "365 d", "365 days", "   365   days   ", "365day",
            "12m", "12mo", "12 m", "   12   mo   ", "12 months", "12month")
        val epochDate = LocalDate.ofEpochDay(0)
        val oneYear = ChronoUnit.DAYS.between(epochDate, epochDate.plus(Period.ofYears(1)))
        for (y <- oneYears) {
            val period = SimpleConfig.parsePeriod(y, fakeOrigin(), "test")
            val dayCount = ChronoUnit.DAYS.between(epochDate, epochDate.plus(period))
            assertEquals(oneYear, dayCount)
        }

        // bad units
        val e = intercept[ConfigException.BadValue] {
            SimpleConfig.parsePeriod("100 dollars", fakeOrigin(), "test")
        }
        assertTrue(s"${e.getMessage} was not the expected error message", e.getMessage.contains("time unit"))

        // bad number
        val e2 = intercept[ConfigException.BadValue] {
            SimpleConfig.parsePeriod("1 00 seconds", fakeOrigin(), "test")
        }
        assertTrue(s"${e2.getMessage} was not the expected error message", e2.getMessage.contains("time unit 'seconds'"))
    }

    // https://github.com/lightbend/config/issues/117
    // this broke because "1d" is a valid double for parseDouble
    @Test
    def parseOneDayAsMilliseconds(): Unit = {
        val result = SimpleConfig.parseDuration("1d", fakeOrigin(), "test")
        val dayInNanos = TimeUnit.DAYS.toNanos(1)
        assertEquals("could parse 1d", dayInNanos, result)

        val conf = parseConfig("foo = 1d")
        assertEquals("could get 1d from conf as days",
            1L, conf.getDuration("foo", TimeUnit.DAYS))
        assertEquals("could get 1d from conf as nanos",
            dayInNanos, conf.getNanoseconds("foo"))
        assertEquals("could get 1d from conf as millis",
            TimeUnit.DAYS.toMillis(1), conf.getMilliseconds("foo"))
    }

    @Test
    def parseMemorySizeInBytes(): Unit = {
        def parseMem(s: String): BigInteger = SimpleConfig.parseBytes(s, fakeOrigin(), "test")

        assertEquals(BigInteger.valueOf(Long.MaxValue), parseMem(s"${Long.MaxValue} bytes"))
        assertEquals(BigInteger.valueOf(Long.MinValue), parseMem(s"${Long.MinValue} bytes"))

        val oneMebis = List("1048576", "1048576b", "1048576bytes", "1048576byte",
            "1048576  b", "1048576  bytes",
            "    1048576  b   ", "  1048576  bytes   ",
            "1048576B",
            "1024k", "1024K", "1024Ki", "1024KiB", "1024 kibibytes", "1024 kibibyte",
            "1m", "1M", "1 M", "1Mi", "1MiB", "1 mebibytes", "1 mebibyte",
            "0.0009765625g", "0.0009765625G", "0.0009765625Gi", "0.0009765625GiB", "0.0009765625 gibibytes", "0.0009765625 gibibyte")

        for (s <- oneMebis) {
            val result = parseMem(s)
            assertEquals(BigInteger.valueOf(1024 * 1024), result)
        }

        val oneMegas = List("1000000", "1000000b", "1000000bytes", "1000000byte",
            "1000000  b", "1000000  bytes",
            "    1000000  b   ", "  1000000  bytes   ",
            "1000000B",
            "1000kB", "1000 kilobytes", "1000 kilobyte",
            "1MB", "1 megabytes", "1 megabyte",
            ".001GB", ".001 gigabytes", ".001 gigabyte")

        for (s <- oneMegas) {
            val result = parseMem(s)
            assertEquals(BigInteger.valueOf(1000 * 1000), result)
        }

        var result = BigInteger.valueOf(1024L * 1024 * 1024)
        for (unit <- Seq("tebi", "pebi", "exbi", "zebi", "yobi")) {
            val first = unit.substring(0, 1).toUpperCase()
            result = result.multiply(BigInteger.valueOf(1024))
            assertEquals(result, parseMem("1" + first))
            assertEquals(result, parseMem("1" + first + "i"))
            assertEquals(result, parseMem("1" + first + "iB"))
            assertEquals(result, parseMem("1" + unit + "byte"))
            assertEquals(result, parseMem("1" + unit + "bytes"))
        }

        result = BigInteger.valueOf(1000L * 1000 * 1000)
        for (unit <- Seq("tera", "peta", "exa", "zetta", "yotta")) {
            val first = unit.substring(0, 1).toUpperCase()
            result = result.multiply(BigInteger.valueOf(1000))
            assertEquals(result, parseMem("1" + first + "B"))
            assertEquals(result, parseMem("1" + unit + "byte"))
            assertEquals(result, parseMem("1" + unit + "bytes"))
        }

        // bad units
        val e = intercept[ConfigException.BadValue] {
            SimpleConfig.parseBytes("100 dollars", fakeOrigin(), "test")
        }
        assertTrue(e.getMessage.contains("size-in-bytes unit"))

        // bad number
        val e2 = intercept[ConfigException.BadValue] {
            SimpleConfig.parseBytes("1 00 bytes", fakeOrigin(), "test")
        }
        assertTrue(e2.getMessage.contains("size-in-bytes number"))
    }

    // later on we'll want to check this with BigInteger version of getBytes
    @Test
    def parseHugeMemorySizes(): Unit = {
        def parseMem(s: String): Long = ConfigFactory.parseString(s"v = $s").getBytes("v")
        def assertOutOfRange(s: String): Unit = {
            val fail = intercept[ConfigException.BadValue] {
                parseMem(s)
            }
            assertTrue("number was too big", fail.getMessage.contains("out of range"))
        }

        def assertNegativeNumber(s: String): Unit = {
            val fail = intercept[ConfigException.BadValue] {
                parseMem(s)
            }
            assertTrue("number was negative", fail.getMessage.contains("negative number"))
        }

        import java.math.BigInteger
        assertOutOfRange(s"${BigInteger.valueOf(Long.MaxValue).add(BigInteger.valueOf(1)).toString} bytes")
        assertNegativeNumber(s"${BigInteger.valueOf(Long.MinValue).subtract(BigInteger.valueOf(1)).toString} bytes")

        for (unit <- Seq("zebi", "yobi")) {
            val first = unit.substring(0, 1).toUpperCase()
            assertOutOfRange("1" + first)
            assertOutOfRange("1" + first + "i")
            assertOutOfRange("1" + first + "iB")
            assertOutOfRange("1" + unit + "byte")
            assertOutOfRange("1" + unit + "bytes")
            assertOutOfRange("1.1" + first)
            assertNegativeNumber("-1" + first)
        }

        for (unit <- Seq("zetta", "yotta")) {
            val first = unit.substring(0, 1).toUpperCase()
            assertOutOfRange("1" + first + "B")
            assertOutOfRange("1" + unit + "byte")
            assertOutOfRange("1" + unit + "bytes")
            assertOutOfRange("1.1" + first + "B")
            assertNegativeNumber("-1" + first + "B")
        }

        assertOutOfRange("1000 exabytes")
        assertOutOfRange("10000000 petabytes")
    }
}
