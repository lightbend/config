/**
 * Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import beanconfig.EnumsConfig.{ Problem, Solution }
import com.typesafe.config._

import java.io.{ InputStream, InputStreamReader }
import java.time.Duration
import beanconfig._
import com.typesafe.config.ConfigException.BadPath
import org.junit.Assert._
import org.junit._

import java.util
import java.util.Collections
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class ConfigBeanFactoryTest extends TestUtils {

    @Test
    def testToCamelCase() {
        assertEquals("configProp", ConfigImplUtil.toCamelCase("config-prop"))
        assertEquals("configProp", ConfigImplUtil.toCamelCase("configProp"))
        assertEquals("fooBar", ConfigImplUtil.toCamelCase("foo-----bar"))
        assertEquals("fooBar", ConfigImplUtil.toCamelCase("fooBar"))
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
    def testValidation() {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")
        val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
            ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve.getConfig("validation")
        val e = intercept[ConfigException.ValidationFailed] {
            ConfigBeanFactory.create(config, classOf[ValidationBeanConfig])
        }

        val expecteds = Seq(Missing("propNotListedInConfig", 77, "string"),
            WrongType("shouldBeInt", 78, "number", "boolean"),
            WrongType("should-be-boolean", 79, "boolean", "number"),
            WrongType("should-be-list", 80, "list", "string"))

        checkValidationException(e, expecteds)
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
    def testCreateEnum() {
        val beanConfig: EnumsConfig = ConfigBeanFactory.create(loadConfig().getConfig("enums"), classOf[EnumsConfig])
        assertNotNull(beanConfig)
        assertEquals(Problem.P1, beanConfig.getProblem)
        assertEquals(ArrayBuffer(Solution.S1, Solution.S3), beanConfig.getSolutions.asScala)
    }

    @Test
    def testCreateNumber() {
        val beanConfig: NumbersConfig = ConfigBeanFactory.create(loadConfig().getConfig("numbers"), classOf[NumbersConfig])
        assertNotNull(beanConfig)

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
        assertEquals(List(32L, 42L, 52L).asJava, beanConfig.getOfLong)
        assertEquals(List("a", "b", "c").asJava, beanConfig.getOfString)
        //assertEquals(List(List("a", "b", "c").asJava,
        //    List("a", "b", "c").asJava,
        //    List("a", "b", "c").asJava).asJava,
        //    beanConfig.getOfArray)
        assertEquals(3, beanConfig.getOfObject.size)
        assertEquals(3, beanConfig.getOfDouble.size)
        assertEquals(3, beanConfig.getOfConfig.size)
        assertTrue(beanConfig.getOfConfig.get(0).isInstanceOf[Config])
        assertEquals(3, beanConfig.getOfConfigObject.size)
        assertTrue(beanConfig.getOfConfigObject.get(0).isInstanceOf[ConfigObject])
        assertEquals(List(intValue(1), intValue(2), stringValue("a")),
            beanConfig.getOfConfigValue.asScala)
        assertEquals(List(Duration.ofMillis(1), Duration.ofHours(2), Duration.ofDays(3)),
            beanConfig.getOfDuration.asScala)
        assertEquals(List(ConfigMemorySize.ofBytes(1024),
            ConfigMemorySize.ofBytes(1048576),
            ConfigMemorySize.ofBytes(1073741824)),
            beanConfig.getOfMemorySize.asScala)

        val stringsConfigOne = new StringsConfig()
        stringsConfigOne.setAbcd("testAbcdOne")
        stringsConfigOne.setYes("testYesOne")
        val stringsConfigTwo = new StringsConfig()
        stringsConfigTwo.setAbcd("testAbcdTwo")
        stringsConfigTwo.setYes("testYesTwo")

        assertEquals(List(stringsConfigOne, stringsConfigTwo).asJava, beanConfig.getOfStringBean)
    }
    @Test
    def testCanAccessListByElement() {
        val listConfig: Config = loadConfig().getConfig("arrays")
        Assert.assertTrue(listConfig.getList("empty").isEmpty)
        Assert.assertTrue(listConfig.hasPath("ofObject.0"))
        Assert.assertEquals(2, listConfig.getInt("ofInt.1"))
        Assert.assertEquals("c", listConfig.getString("ofString.2"))
        Assert.assertEquals("c", listConfig.getString("ofArray.2.2"))
        Assert.assertEquals(listConfig.getList("ofArray.2").unwrapped(), List("a", "b", "c").asJava);
        Assert.assertTrue(listConfig.hasPath("ofArray.2"))
        Assert.assertTrue(listConfig.hasPath("ofArray.2.2"))
        Assert.assertTrue(listConfig.hasPath("ofObject.0.byteObj.byteVal"))
        Assert.assertEquals("1", listConfig.getString("ofObject.0.byteObj.byteVal"))
        Assert.assertFalse(listConfig.hasPath("ofObject.0.byteObj.byteVal.non"))
        Assert.assertTrue(listConfig.getIsNull("ofNull.0"))
        Assert.assertFalse(listConfig.hasPath("ofNull.0"))

        try {
            listConfig.getString("empty.1")
            Assert.fail();
        } catch {
            case e: BadPath => {}
            case e: Exception => Assert.fail()
        }

    }

    @Test
    def testCreateSet() {
        val beanConfig: SetsConfig = ConfigBeanFactory.create(loadConfig().getConfig("sets"), classOf[SetsConfig])
        assertNotNull(beanConfig)
        assertEquals(Set().asJava, beanConfig.getEmpty)
        assertEquals(Set(1, 2, 3).asJava, beanConfig.getOfInt)
        assertEquals(Set(32L, 42L, 52L).asJava, beanConfig.getOfLong)
        assertEquals(Set("a", "b", "c").asJava, beanConfig.getOfString)
        assertEquals(3, beanConfig.getOfObject.size)
        assertEquals(3, beanConfig.getOfDouble.size)
        assertEquals(3, beanConfig.getOfConfig.size)
        assertTrue(beanConfig.getOfConfig.iterator().next().isInstanceOf[Config])
        assertEquals(3, beanConfig.getOfConfigObject.size)
        assertTrue(beanConfig.getOfConfigObject.iterator().next().isInstanceOf[ConfigObject])
        assertEquals(Set(intValue(1), intValue(2), stringValue("a")),
            beanConfig.getOfConfigValue.asScala)
        assertEquals(Set(Duration.ofMillis(1), Duration.ofHours(2), Duration.ofDays(3)),
            beanConfig.getOfDuration.asScala)
        assertEquals(Set(ConfigMemorySize.ofBytes(1024),
            ConfigMemorySize.ofBytes(1048576),
            ConfigMemorySize.ofBytes(1073741824)),
            beanConfig.getOfMemorySize.asScala)

        val stringsConfigOne = new StringsConfig()
        stringsConfigOne.setAbcd("testAbcdOne")
        stringsConfigOne.setYes("testYesOne")
        val stringsConfigTwo = new StringsConfig()
        stringsConfigTwo.setAbcd("testAbcdTwo")
        stringsConfigTwo.setYes("testYesTwo")

        assertEquals(Set(stringsConfigOne, stringsConfigTwo).asJava, beanConfig.getOfStringBean)
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

    @Test
    def testPreferCamelNames() {
        val beanConfig = ConfigBeanFactory.create(loadConfig().getConfig("preferCamelNames"), classOf[PreferCamelNamesConfig])
        assertNotNull(beanConfig)

        assertEquals("yes", beanConfig.getFooBar)
        assertEquals("yes", beanConfig.getBazBar)
    }

    @Test
    def testValues() {
        val beanConfig = ConfigBeanFactory.create(loadConfig().getConfig("values"), classOf[ValuesConfig])
        assertNotNull(beanConfig)
        assertEquals(42, beanConfig.getObj())
        assertEquals("abcd", beanConfig.getConfig.getString("abcd"))
        assertEquals(3, beanConfig.getConfigObj.toConfig.getInt("intVal"))
        assertEquals(stringValue("hello world"), beanConfig.getConfigValue)
        assertEquals(List(1, 2, 3).map(intValue), beanConfig.getList.asScala)
        assertEquals(true, beanConfig.getUnwrappedMap.get("shouldBeInt"))
        assertEquals(42, beanConfig.getUnwrappedMap.get("should-be-boolean"))
    }

    @Test
    def testOptionalProperties() {
        val beanConfig: ObjectsConfig = ConfigBeanFactory.create(loadConfig().getConfig("objects"), classOf[ObjectsConfig])
        assertNotNull(beanConfig)
        assertNotNull(beanConfig.getValueObject)
        assertNull(beanConfig.getValueObject.getOptionalValue)
        assertNull(beanConfig.getValueObject.getDefault)
        assertEquals("notNull", beanConfig.getValueObject.getMandatoryValue)
    }

    @Test
    def testNotAnOptionalProperty(): Unit = {
        val e = intercept[ConfigException.ValidationFailed] {
            ConfigBeanFactory.create(parseConfig("{valueObject: {}}"), classOf[ObjectsConfig])
        }
        assertTrue("missing value error", e.getMessage.contains("No setting"))
        assertTrue("error about the right property", e.getMessage.contains("mandatoryValue"))

    }

    @Test
    def testNotABeanField() {
        val e = intercept[ConfigException.BadBean] {
            ConfigBeanFactory.create(parseConfig("notBean=42"), classOf[NotABeanFieldConfig])
        }
        assertTrue("unsupported type error", e.getMessage.contains("unsupported type"))
        assertTrue("error about the right property", e.getMessage.contains("notBean"))
    }

    @Test
    def testNotAnEnumField() {
        val e = intercept[ConfigException.BadValue] {
            ConfigBeanFactory.create(parseConfig("{problem=P1,solutions=[S4]}"), classOf[EnumsConfig])
        }
        assertTrue("invalid value error", e.getMessage.contains("Invalid value"))
        assertTrue("error about the right property", e.getMessage.contains("solutions"))
        assertTrue("error enumerates the enum constants", e.getMessage.contains("should be one of [S1, S2, S3]"))
    }

    @Test
    def testUnsupportedListElement() {
        val e = intercept[ConfigException.BadBean] {
            ConfigBeanFactory.create(parseConfig("uri=[42]"), classOf[UnsupportedListElementConfig])
        }
        assertTrue("unsupported element type error", e.getMessage.contains("unsupported list element type"))
        assertTrue("error about the right property", e.getMessage.contains("uri"))
    }

    @Test
    def testUnsupportedMapKey() {
        val e = intercept[ConfigException.BadBean] {
            ConfigBeanFactory.create(parseConfig("map={}"), classOf[UnsupportedMapKeyConfig])
        }
        assertTrue("unsupported map type error", e.getMessage.contains("unsupported Map"))
        assertTrue("error about the right property", e.getMessage.contains("'map'"))
    }

    @Test
    def testUnsupportedMapValue() {
        val e = intercept[ConfigException.BadBean] {
            ConfigBeanFactory.create(parseConfig("map={}"), classOf[UnsupportedMapValueConfig])
        }
        assertTrue("unsupported map type error", e.getMessage.contains("unsupported Map"))
        assertTrue("error about the right property", e.getMessage.contains("'map'"))
    }

    @Test
    def testDifferentFieldNameFromAccessors(): Unit = {
        val e = intercept[ConfigException.ValidationFailed] {
            ConfigBeanFactory.create(ConfigFactory.empty(), classOf[DifferentFieldNameFromAccessorsConfig])
        }
        assertTrue("only one missing value error", e.getMessage.contains("No setting"))
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
