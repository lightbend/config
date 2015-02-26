/**
 * Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config

import java.io.{ InputStream, InputStreamReader }
import java.time.Duration;

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
        assertEquals(beanConfig.getHalfSecond, Duration.ofMillis(500))
        assertEquals(beanConfig.getSecond, Duration.ofMillis(1000))
        assertEquals(beanConfig.getSecondAsNumber, Duration.ofMillis(1000))
    }

    @Test
    def testCreateBytes() {
        val beanConfig: BytesConfig = ConfigBeanFactory.create(loadConfig().getConfig("bytes"), classOf[BytesConfig])
        assertNotNull(beanConfig)
        assertEquals(beanConfig.getKibibyte, ConfigMemorySize.ofBytes(1024))
        assertEquals(beanConfig.getKilobyte, ConfigMemorySize.ofBytes(1000))
        assertEquals(beanConfig.getThousandBytes, ConfigMemorySize.ofBytes(1000))
    }

    private def loadConfig(): Config = {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
            ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
        config
    }

}
