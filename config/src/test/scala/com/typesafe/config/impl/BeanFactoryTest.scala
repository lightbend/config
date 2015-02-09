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

class BeanFactoryTest {

  @Test
  def toCamelCase() {
    assertEquals("configProp", BeanFactory.toCamelCase("config-prop"))
    assertEquals("fooBar", BeanFactory.toCamelCase("foo-----bar"))
    assertEquals("foo", BeanFactory.toCamelCase("-foo"))
    assertEquals("bar", BeanFactory.toCamelCase("bar-"))
  }

  @Test
  def getTimeUnit() {
    assertEquals(TimeUnit.MILLISECONDS, BeanFactory.getTimeUnit("30ms"))

  }

  @Test
  def testCreate() {
    val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
    val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
      ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
    val beanConfig: TestBeanConfig = BeanFactory.create(config, classOf[TestBeanConfig])
    assertNotNull(beanConfig)
  }


  @Test
  def testCreateBool() {
    val beanConfig: BooleansConfig = BeanFactory.create(loadConfig().getConfig("booleans"), classOf[BooleansConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateNumber() {
    val beanConfig: NumbersConfig = BeanFactory.create(loadConfig().getConfig("numbers"), classOf[NumbersConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateList() {
    val beanConfig: ArraysConfig = BeanFactory.create(loadConfig().getConfig("arrays"), classOf[ArraysConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateDuration() {
    val beanConfig: DurationsConfig = BeanFactory.create(loadConfig().getConfig("durations"), classOf[DurationsConfig])
    assertNotNull(beanConfig)
  }

  @Test
  def testCreateBytes() {
    val beanConfig: BytesConfig = BeanFactory.create(loadConfig().getConfig("bytes"), classOf[BytesConfig])
    assertNotNull(beanConfig)
  }

  private def loadConfig(): Config = {
    val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
    val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
      ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
    config
  }

}
