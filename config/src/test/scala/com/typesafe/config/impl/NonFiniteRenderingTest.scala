package com.typesafe.config.impl

import org.junit._
import org.junit.Assert._

import com.typesafe.config.{
  ConfigFactory,
  ConfigParseOptions,
  ConfigSyntax,
  ConfigRenderOptions,
  ConfigValueFactory,
  ConfigValueType
}

// A non-finite double renders as a quoted string so the output
// stays valid JSON and survives a round trip
class NonFiniteRenderingTest {
  private val json = ConfigRenderOptions.concise
  private val hocon = ConfigRenderOptions.concise.setJson(false)

  @Test def nonFiniteDoublesRenderAsQuotedStrings(): Unit = {
    assertEquals(
      """{"a":"Infinity"}""",
      ConfigFactory.parseString("a = 1e999").root.render(json)
    )
    assertEquals(
      """{"a":"-Infinity"}""",
      ConfigFactory.parseString("a = -1e999").root.render(json)
    )
    assertEquals(
      "\"NaN\"",
      ConfigValueFactory
        .fromAnyRef(java.lang.Double.valueOf(Double.NaN))
        .render(json)
    )
  }

  @Test def hoconModeMatchesJsonMode(): Unit = {
    assertEquals(
      "a=\"Infinity\"",
      ConfigFactory.parseString("a = 1e999").root.render(hocon)
    )
  }

  @Test def nonFiniteDoublesInListsAreQuoted(): Unit = {
    assertEquals(
      """{"a":["Infinity"]}""",
      ConfigFactory.parseString("a = [1e999]").root.render(json)
    )
  }

  @Test def renderIsAFixedPoint(): Unit = {
    val first = ConfigFactory.parseString("a = 1e999").root.render(json)
    assertEquals(
      first,
      ConfigFactory.parseString(first).root.render(json)
    )
    // in HOCON a re-parsed "Infinity" is an unquoted-safe string, so the
    // spelling settles on a=Infinity from the second render on
    val hoconFirst = ConfigFactory.parseString("a = 1e999").root.render(hocon)
    val hoconSecond =
      ConfigFactory.parseString(hoconFirst).root.render(hocon)
    assertEquals(
      hoconSecond,
      ConfigFactory.parseString(hoconSecond).root.render(hocon)
    )
  }

  @Test def reParsedValueRoundTripsThroughGetDouble(): Unit = {
    val rendered = ConfigFactory.parseString("a = 1e999").root.render(json)
    val reparsed = ConfigFactory.parseString(rendered)
    // "Infinity" parses back as a string, the only JSON type that holds it
    assertEquals(
      ConfigValueType.STRING,
      reparsed.getValue("a").valueType
    )
    assertEquals(
      Double.PositiveInfinity,
      reparsed.getDouble("a"),
      0.0
    )
  }
  @Test def allNonFiniteValuesRoundTripInObjectsAndLists(): Unit = {
    for (number <- Seq(
          Double.PositiveInfinity,
          Double.NegativeInfinity,
          Double.NaN
        )) {
      val value =
        ConfigValueFactory.fromAnyRef(java.lang.Double.valueOf(number))
      for (options <- Seq(
            json,
            hocon,
            ConfigRenderOptions.defaults
              .setComments(false)
              .setOriginComments(false)
          )) {
        val rendered = value.atKey("a").root.render(options)
        val parseOptions = ConfigParseOptions.defaults.setSyntax(
          if (options.getJson) ConfigSyntax.JSON else ConfigSyntax.CONF
        )
        val parsed = ConfigFactory.parseString(rendered, parseOptions)
        val actual = parsed.getDouble("a")
        if (number.isNaN) assertTrue(actual.isNaN)
        else assertEquals(number, actual, 0.0)
        assertEquals(ConfigValueType.STRING, parsed.getValue("a").valueType)
      }
      val list = ConfigValueFactory.fromIterable(
        java.util.Arrays.asList(java.lang.Double.valueOf(number))
      )
      val parsed = ConfigFactory.parseString(
        list.atKey("a").root.render(json),
        ConfigParseOptions.defaults.setSyntax(ConfigSyntax.JSON)
      )
      val actual = parsed.getDoubleList("a").get(0).doubleValue()
      if (number.isNaN) assertTrue(actual.isNaN)
      else assertEquals(number, actual, 0.0)
    }
  }

  @Test def finiteDoublesKeepTheirNumericType(): Unit = {
    for (number <- Seq(1.5, -1.5, Double.MaxValue, Double.MinPositiveValue)) {
      val value =
        ConfigValueFactory.fromAnyRef(java.lang.Double.valueOf(number))
      val parsed = ConfigFactory.parseString(value.atKey("a").root.render(json))
      assertEquals(ConfigValueType.NUMBER, parsed.getValue("a").valueType)
      assertEquals(number, parsed.getDouble("a"), 0.0)
    }
  }

  @Test def environmentValuesRespectTheRenderOption(): Unit = {
    val origin = SimpleConfigOrigin.newEnvVariable("TEST_SECRET")
    val value = new ConfigDouble(origin, Double.PositiveInfinity, null)
    val hidden = json.setShowEnvVariableValues(false)
    assertEquals(
      new ConfigDouble(origin, 1.5, null).render(hidden),
      value.render(hidden)
    )
    assertFalse(value.render(hidden).contains("Infinity"))
    assertEquals(
      "\"Infinity\"",
      value.render(json.setShowEnvVariableValues(true))
    )
  }

}
