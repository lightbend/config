/**
 * Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config

import java.io.{ InputStream, InputStreamReader }
import java.util.concurrent.TimeUnit

import beanconfig._
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
    def testUnknownProp() {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
            ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
        var expected: ConfigException.Generic = null
        var beanConfig: NoFoundPropBeanConfig = null
        try {
            beanConfig = ConfigBeanFactory.create(config, classOf[NoFoundPropBeanConfig])
        } catch {
            case cge: ConfigException.Generic => expected = cge
            case e: Exception => expected = null
        }
        assertNotNull(expected)
        assertEquals("Could not find property 'propNotListedInConfig' from class 'beanconfig.NoFoundPropBeanConfig' in config.",
            expected.getMessage)
        assertNull(beanConfig)
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
        assertEquals(beanConfig.getHalfSecond, TimeUnit.MILLISECONDS.toNanos(500))
        assertEquals(beanConfig.getSecond, TimeUnit.SECONDS.toNanos(1))
        assertEquals(beanConfig.getSecondAsNumber, TimeUnit.SECONDS.toNanos(1))
    }

    @Test
    def testCreateBytes() {
        val beanConfig: BytesConfig = ConfigBeanFactory.create(loadConfig().getConfig("bytes"), classOf[BytesConfig])
        assertNotNull(beanConfig)
        assertEquals(beanConfig.getKibibyte, 1024L)
        assertEquals(beanConfig.getKilobyte, 1000L)
        assertEquals(beanConfig.getThousandBytes, 1000L)
    }

    private def loadConfig(): Config = {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
            ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
        config
    }

}
