/**
 * Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.{InputStream, InputStreamReader}
import java.util.concurrent.TimeUnit

import beanconfig._
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}
import org.junit.Assert._
import org.junit._

class ConfigBeanFactoryTest {

  @Test
  def toCamelCase() {
    assertEquals("configProp", ConfigBeanFactory.toCamelCase("config-prop"))
    assertEquals("fooBar", ConfigBeanFactory.toCamelCase("foo-----bar"))
    assertEquals("foo", ConfigBeanFactory.toCamelCase("-foo"))
    assertEquals("bar", ConfigBeanFactory.toCamelCase("bar-"))
  }

  @Test
  def testCreate() {
    val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
    val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
      ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
    val beanConfig: TestBeanConfig = ConfigBeanFactory.create(config, classOf[TestBeanConfig])
    assertNotNull(beanConfig)
  }


  @Test
  def testCreateBool() {
    val beanConfig: BooleansConfig = ConfigBeanFactory.create(loadConfig().getConfig("booleans"), classOf[BooleansConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateNumber() {
    val beanConfig: NumbersConfig = ConfigBeanFactory.create(loadConfig().getConfig("numbers"), classOf[NumbersConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateList() {
    val beanConfig: ArraysConfig = ConfigBeanFactory.create(loadConfig().getConfig("arrays"), classOf[ArraysConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateDuration() {
    val beanConfig: DurationsConfig = ConfigBeanFactory.create(loadConfig().getConfig("durations"), classOf[DurationsConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateBytes() {
    val beanConfig: BytesConfig = ConfigBeanFactory.create(loadConfig().getConfig("bytes"), classOf[BytesConfig])
    assertNotNull(beanConfig)
  }

  private def loadConfig(): Config = {
    val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
    val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
      ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
    config
  }

}
