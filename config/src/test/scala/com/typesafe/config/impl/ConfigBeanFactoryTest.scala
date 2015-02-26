/**
 * Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config._

import java.io.{ InputStream, InputStreamReader }
import java.time.Duration;

import beanconfig._
import org.junit.Assert._
import org.junit._

import scala.collection.JavaConverters._

class ConfigBeanFactoryTest extends TestUtils {

    @Test
    def toCamelCase() {
        assertEquals("configProp", ConfigImplUtil.toCamelCase("config-prop"))
        assertEquals("fooBar", ConfigImplUtil.toCamelCase("foo-----bar"))
        assertEquals("foo", ConfigImplUtil.toCamelCase("-foo"))
        assertEquals("bar", ConfigImplUtil.toCamelCase("bar-"))
    }

    @Test
    def testCreate() {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
            ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
        val beanConfig: TestBeanConfig = ConfigBeanFactory.create(config, classOf[TestBeanConfig])
        assertNotNull(beanConfig)
        // recursive bean inside the first bean
        assertEquals(3, beanConfig.getNumbers.getIntVal)
    }

    @Test
    def testUnknownProp() {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
            ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
        val expected = intercept[ConfigException.Missing] {
            ConfigBeanFactory.create(config, classOf[NoFoundPropBeanConfig])
        }
        // TODO this error message should have the config name not the camelcase name
        assertTrue(expected.getMessage.contains("propNotListedInConfig"))
    }

    @Test
    def testCreateBool() {
        val beanConfig: BooleansConfig = ConfigBeanFactory.create(loadConfig().getConfig("booleans"), classOf[BooleansConfig])
        assertNotNull(beanConfig)
        assertEquals(true, beanConfig.getTrueVal)
        assertEquals(false, beanConfig.getFalseVal)
    }

    @Test
    def testCreateString() {
        val beanConfig: StringsConfig = ConfigBeanFactory.create(loadConfig().getConfig("strings"), classOf[StringsConfig])
        assertNotNull(beanConfig)
        assertEquals("abcd", beanConfig.getAbcd)
        assertEquals("yes", beanConfig.getYes)
    }

    @Test
    def testCreateNumber() {
        val beanConfig: NumbersConfig = ConfigBeanFactory.create(loadConfig().getConfig("numbers"), classOf[NumbersConfig])
        assertNotNull(beanConfig)
        assertEquals(1: Byte, beanConfig.getByteVal)
        assertEquals(1: Byte, beanConfig.getByteObj)
        assertEquals(2: Short, beanConfig.getShortVal)
        assertEquals(2: Short, beanConfig.getShortObj)

        assertEquals(3, beanConfig.getIntVal)
        assertEquals(3, beanConfig.getIntObj)

        assertEquals(4L, beanConfig.getLongVal)
        assertEquals(4L, beanConfig.getLongObj)

        assertEquals(1.0, beanConfig.getDoubleVal, 1e-6)
        assertEquals(1.0, beanConfig.getDoubleObj, 1e-6)
    }

    @Test
    def testCreateList() {
        val beanConfig: ArraysConfig = ConfigBeanFactory.create(loadConfig().getConfig("arrays"), classOf[ArraysConfig])
        assertNotNull(beanConfig)
        assertEquals(List().asJava, beanConfig.getEmpty)
        assertEquals(List(1, 2, 3).asJava, beanConfig.getOfInt)
        assertEquals(List("a", "b", "c").asJava, beanConfig.getOfString)
        assertEquals(List(List("a", "b", "c").asJava,
            List("a", "b", "c").asJava,
            List("a", "b", "c").asJava).asJava,
            beanConfig.getOfArray)
        assertEquals(3, beanConfig.getOfObject.size)
    }

    @Test
    def testCreateDuration() {
        val beanConfig: DurationsConfig = ConfigBeanFactory.create(loadConfig().getConfig("durations"), classOf[DurationsConfig])
        assertNotNull(beanConfig)
        assertEquals(Duration.ofMillis(500), beanConfig.getHalfSecond)
        assertEquals(Duration.ofMillis(1000), beanConfig.getSecond)
        assertEquals(Duration.ofMillis(1000), beanConfig.getSecondAsNumber)
    }

    @Test
    def testCreateBytes() {
        val beanConfig: BytesConfig = ConfigBeanFactory.create(loadConfig().getConfig("bytes"), classOf[BytesConfig])
        assertNotNull(beanConfig)
        assertEquals(ConfigMemorySize.ofBytes(1024), beanConfig.getKibibyte)
        assertEquals(ConfigMemorySize.ofBytes(1000), beanConfig.getKilobyte)
        assertEquals(ConfigMemorySize.ofBytes(1000), beanConfig.getThousandBytes)
    }

    private def loadConfig(): Config = {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        try {
            val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
                ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
            config
        } finally {
            configIs.close()
        }
    }

}
