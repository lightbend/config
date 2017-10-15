package com.typesafe.config.impl

import java.io.{InputStream, InputStreamReader}

import beanconfig.polymorphic.PolymorphicWithDefaultImpl._
import beanconfig.polymorphic.PolymorphicWithDefaultImplConfigs._
import beanconfig.polymorphic.Subtypes._
import beanconfig.polymorphic.SubtypesConfigs._
import beanconfig.polymorphic.TypeNames._
import beanconfig.polymorphic.TypeNamesConfigs._
import beanconfig.polymorphic.VisibleTypeId._
import beanconfig.polymorphic.VisibleTypeIdConfigs._
import beanconfig.polymorphic.WithGenericsConfigs._
import beanconfig.polymorphic.WithServiceLoader._
import beanconfig.polymorphic.WithServiceLoaderConfigs._
import beanconfig.polymorphic.{TypeNames, WithGenerics}
import com.typesafe.config._
import org.junit.Assert._
import org.junit._

import scala.collection.JavaConverters._

class ConfigPolymorphicBeanTest extends TestUtils {

    @Test
    def testDeserializationWithObject() {
        val beanConfig: InnerConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("polymorphicWithDefaultImpl"), classOf[InnerConfig])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getObject.isInstanceOf[MyInter])
        assertFalse(beanConfig.getObject.isInstanceOf[LegacyInter])
        assertEquals(List("a", "b", "c").asJava, beanConfig.getObject.asInstanceOf[MyInter].getBlah)
    }

    @Test
    def testDeserializationWithArray() {
        val beanConfig: InnerConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("polymorphicWithDefaultImpl"), classOf[InnerConfig])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getArray.isInstanceOf[LegacyInter])
        assertEquals(List("a", "b", "c", "d").asJava, beanConfig.getArray.asInstanceOf[LegacyInter].getBlah)
    }

    @Test
    def testDefaultAsVoid() {
        val beanConfig: DefaultWithVoidAsDefaultConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("polymorphicWithDefaultImpl"),
                classOf[DefaultWithVoidAsDefaultConfig])
        assertNotNull(beanConfig)

        assertNull(beanConfig.getDefaultAsVoid1)
        assertNull(beanConfig.getDefaultAsVoid2)
    }

    @Test
    def testBadTypeAsNull() {
        val e = intercept[ConfigException.BadBean] {
            ConfigBeanFactory.create(loadConfig().getConfig("polymorphicWithDefaultImpl"), classOf[MysteryPolymorphicConfig])
        }
        assertTrue("no default implementation", e.getMessage.contains("has no default implementation"))
    }

    @Test
    def testDeserialization() {
        val beanConfig: SuperTypeConfig = ConfigBeanFactory.create(loadConfig().getConfig("subtypes"), classOf[SuperTypeConfig])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getBean1.isInstanceOf[SubB])
        assertEquals(13, beanConfig.getBean1.asInstanceOf[SubB].getB)

        assertTrue(beanConfig.getBean2.isInstanceOf[SubD])
        assertEquals(-4, beanConfig.getBean2.asInstanceOf[SubD].getD)
    }

    @Test
    def testDefaultImpl() {
        val beanConfig: DefaultImplConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("subtypes"), classOf[DefaultImplConfig])
        assertNotNull(beanConfig)

        // first, test with no type information
        assertTrue(beanConfig.getDefaultImpl1.isInstanceOf[SuperTypeWithDefault])
        assertEquals(13, beanConfig.getDefaultImpl1.asInstanceOf[DefaultImpl].getA)

        // and then with unmapped info
        assertTrue(beanConfig.getDefaultImpl2.isInstanceOf[SuperTypeWithDefault])
        assertEquals(14, beanConfig.getDefaultImpl2.asInstanceOf[DefaultImpl].getA)

        assertTrue(beanConfig.getDefaultImpl3.isInstanceOf[SuperTypeWithDefault])
        assertEquals(0, beanConfig.getDefaultImpl3.asInstanceOf[DefaultImpl].getA)
    }

    @Test
    def testViaAtomic() {
        val beanConfig: AtomicWrapperConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("subtypes"), classOf[AtomicWrapperConfig])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getAtomicWrapper.getDirectValue.isInstanceOf[ImplX])
        assertEquals(3, beanConfig.getAtomicWrapper.getValue)
    }

    @Test
    def testCreateSpecificType() {
        val beanConfig: SingleTypeConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("typeNames"), classOf[SingleTypeConfig])
        assertNotNull(beanConfig)
        assertNotNull(beanConfig.getDog)

        assertTrue(beanConfig.getDog.isInstanceOf[TypeNames.Dog])
        assertEquals("Smiley", beanConfig.getDog.getName)
        assertEquals(1, beanConfig.getDog.asInstanceOf[TypeNames.Dog].getAgeInYears)
    }

    @Test
    def testCreateList() {
        val beanConfig: TypeNamesListConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("typeNames"), classOf[TypeNamesListConfig])
        assertNotNull(beanConfig)

        assertEquals(2, beanConfig.getDogs.size)
        assertEquals(3, beanConfig.getCats.size)
        assertEquals(2, beanConfig.getAnimals.size)

        val dogsConfigOne = new Dog()
        dogsConfigOne.setName("Spot")
        dogsConfigOne.setAgeInYears(3)
        val dogsConfigTwo = new Dog()
        dogsConfigTwo.setName("Odie")
        dogsConfigTwo.setAgeInYears(7)

        assertEquals(List(dogsConfigOne, dogsConfigTwo).asJava, beanConfig.getDogs)

        val catsConfigOne = new MaineCoon()
        catsConfigOne.setName("Belzebub")
        catsConfigOne.setPurrs(true)
        val catsConfigTwo = new MaineCoon()
        catsConfigTwo.setName("Piru")
        catsConfigTwo.setPurrs(false)
        val catsConfigThree = new Persian()
        catsConfigThree.setName("Khomeini")
        catsConfigThree.setPurrs(true)

        assertEquals(List(catsConfigOne, catsConfigTwo, catsConfigThree).asJava, beanConfig.getCats)

        val animalsConfigOne = new MaineCoon()
        animalsConfigOne.setName("Venla")
        animalsConfigOne.setPurrs(true)
        val animalsConfigTwo = new Dog()
        animalsConfigTwo.setName("Amadeus")
        animalsConfigTwo.setAgeInYears(13)

        assertEquals(List(animalsConfigOne, animalsConfigTwo).asJava, beanConfig.getAnimals)
    }

    @Test
    def testVisible() {
        val beanConfig: VisibleTypeConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("visibleTypeId"), classOf[VisibleTypeConfig])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getBean.isInstanceOf[PropertyBean])
        assertEquals("BaseType", beanConfig.getBean.getType)
        assertEquals(3, beanConfig.getBean.getA)
    }

    @Test
    def testWrapperWithGenerics() {
        val beanConfig: WrapperWithGenericsConfig =
            ConfigBeanFactory.create(loadConfig().getConfig("withGenerics"), classOf[WrapperWithGenericsConfig])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getWrapperWithDog.getAnimal.isInstanceOf[WithGenerics.Dog])
        assertEquals("Fluffy", beanConfig.getWrapperWithDog.getAnimal.getName)
        assertEquals(3, beanConfig.getWrapperWithDog.getAnimal.asInstanceOf[WithGenerics.Dog].getBoneCount)
    }

    @Test
    def testSimpleServiceLoader() {
        val beanConfig: SimpleServiceLoader =
            ConfigBeanFactory.create(loadConfig().getConfig("withServiceLoader"), classOf[SimpleServiceLoader])
        assertNotNull(beanConfig)

        assertTrue(beanConfig.getSingleTag.isInstanceOf[ImplA])
        assertEquals("bar", beanConfig.getSingleTag.asInstanceOf[ImplA].getMessage)

        assertEquals(2, beanConfig.getOtherTags.size)

        assertTrue(beanConfig.getOtherTags.get(0).isInstanceOf[ImplA])
        assertEquals("baz", beanConfig.getOtherTags.get(0).asInstanceOf[ImplA].getMessage)
        assertTrue(beanConfig.getOtherTags.get(1).isInstanceOf[ImplB])
        assertEquals(42, beanConfig.getOtherTags.get(1).asInstanceOf[ImplB].getValue)
    }

    private def loadConfig(): Config = {
        val configIs: InputStream = this.getClass().getClassLoader().getResourceAsStream("beanconfig/polymorphic01.conf")
        try {
            val config: Config = ConfigFactory.parseReader(new InputStreamReader(configIs),
                ConfigParseOptions.defaults.setSyntax(ConfigSyntax.CONF)).resolve
            config
        } finally {
            configIs.close()
        }
    }

}
