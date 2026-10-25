package com.typesafe.config.impl

import com.typesafe.config._
import org.junit.Assert._
import org.junit.Test

class LongRangeTest extends TestUtils {
  @Test
  def longRangeChecks(): Unit = {
    val conf = ConfigFactory.parseString(
      "{ tooPositive: 9223372036854775808," +
        " bigDouble: 1e30, overflowingDouble: 1e999, negative: -1e30, negativeInfinity: -1e999, roundedMinimum: -9223372036854775809, exponent: 9.223372036854776e18, nan: \"NaN\" }"
    )
    for (path <- Seq(
          "tooPositive",
          "bigDouble",
          "overflowingDouble",
          "negative",
          "negativeInfinity",
          "roundedMinimum",
          "exponent",
          "nan"
        )) {
      val e = intercept[ConfigException.WrongType] {
        conf.getLong(path)
      }
      assertTrue(e.getMessage.contains("range"))
    }
  }

  @Test
  def longRangeChecksOnLists(): Unit = {
    for (value <- Seq(
          "9223372036854775808",
          "-1e30",
          "-9223372036854775809",
          "\"NaN\"",
          "\"Infinity\""
        )) {
      val conf = ConfigFactory.parseString(s"{ sizes: [1, $value] }")
      val e = intercept[ConfigException.WrongType] { conf.getLongList("sizes") }
      assertTrue(e.getMessage.contains("range"))
      assertTrue(e.getMessage.contains("sizes"))
    }
  }

  @Test
  def longRangeBoundariesStillRead(): Unit = {
    val conf = ConfigFactory.parseString(
      "{ max: 9223372036854775807, min: -9223372036854775808, fraction: 1.5 }"
    )
    assertEquals(Long.MaxValue, conf.getLong("max"))
    assertEquals(Long.MinValue, conf.getLong("min"))
    assertEquals(1L, conf.getLong("fraction"))
    val strings = ConfigFactory.parseString(
      "{ max: \"9223372036854775807\", min: \"-9223372036854775808\" }"
    )
    assertEquals(Long.MaxValue, strings.getLong("max"))
    assertEquals(Long.MinValue, strings.getLong("min"))
    val list = ConfigFactory.parseString(
      "{ values: [\"9223372036854775807\", \"-9223372036854775808\", -1.5, 1.5] }"
    )
    assertEquals(
      java.util.Arrays
        .asList[java.lang.Long](Long.MaxValue, Long.MinValue, -1L, 1L),
      list.getLongList("values")
    )
  }

  @Test
  def byteSizesBeyondLongRejectInsteadOfClamping(): Unit = {
    val conf = ConfigFactory.parseString("{ cache: { max-bytes: 9223372036854775808 } }")
    val e = intercept[ConfigException.BadValue] {
      conf.getBytes("cache.max-bytes")
    }
    assertTrue(e.getMessage.contains("max-bytes"))
  }

  @Test
  def programmaticLongRangeChecks(): Unit = {
    for (number <- Seq(
          Double.NaN,
          Double.PositiveInfinity,
          Double.NegativeInfinity,
          Long.MaxValue.toDouble
        )) {
      val conf = ConfigValueFactory
        .fromAnyRef(java.lang.Double.valueOf(number))
        .atKey("value")
      intercept[ConfigException.WrongType] { conf.getLong("value") }
    }
    val minimum = ConfigValueFactory
      .fromAnyRef(java.lang.Double.valueOf(Long.MinValue.toDouble))
      .atKey("value")
    assertEquals(Long.MinValue, minimum.getLong("value"))
    val belowMaximum = ConfigValueFactory
      .fromAnyRef(java.lang.Double.valueOf(9223372036854774784.0))
      .atKey("value")
    assertEquals(9223372036854774784L, belowMaximum.getLong("value"))
  }
  @Test
  def roundedMinimumUsesOriginalDecimalText(): Unit = {
    for (text <- Seq("-9223372036854775809", "-9223372036854775808.1", "-9.223372036854775809e18");
         quoted <- Seq(false, true)) {
      val value = if (quoted) "\"" + text + "\"" else text
      val conf = ConfigFactory.parseString("value = " + value + "\nvalues = [1, " + value + "]")
      val error = intercept[ConfigException.WrongType] { conf.getLong("value") }
      assertEquals(conf.getValue("value").origin(), error.origin())
      assertTrue(error.getMessage.contains("value"))
      intercept[ConfigException.WrongType] { conf.getLongList("values") }
    }
    for (text <- Seq("-9223372036854775808.0", "-9223372036854775807.9", "-9.223372036854775808e18")) {
      assertEquals(Long.MinValue, ConfigFactory.parseString("value = " + text).getLong("value"))
    }
  }

}
