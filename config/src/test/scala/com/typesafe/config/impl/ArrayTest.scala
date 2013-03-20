/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import java.util.Properties
import com.typesafe.config.Config
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigFactory

class ArrayTest extends TestUtils {

  @Test
  def booleanOption() {
    val conf1 = ConfigFactory.parseArray(Array("-debug"))
    assertEquals(conf1.getBoolean("debug"), true)

    val conf2 = ConfigFactory.parseArray(Array("-debug", "-foo"))
    assertEquals(conf2.getBoolean("debug"), true)
    assertEquals(conf2.getBoolean("foo"), true)

    val conf3 = ConfigFactory.parseArray(Array("-debug", "-bar", "false", "-foo"))
    assertEquals(conf3.getBoolean("debug"), true)
    assertEquals(conf3.getBoolean("foo"), true)
    assertEquals(conf3.getBoolean("bar"), false)
  }

  @Test
  def mixedArguments() {
    val conf = ConfigFactory.parseArray(Array("-foo", "bar", "-debug", "-min", "0.123"))
    assertEquals(conf.getString("foo"), "bar")
    assertEquals(conf.getBoolean("debug"), true)
    assertEquals(conf.getDouble("min"), 0.123, 0.0)
  }

  @Test
  def mixedArgumentsWithPath() {
    val conf = ConfigFactory.parseArray(Array("-foo.bar", "1.0", "-dev.debug"))
    assertEquals(conf.getConfig("foo").getDouble("bar"), 1.0, 0.0)
    assertEquals(conf.getBoolean("dev.debug"), true)
  }
}
