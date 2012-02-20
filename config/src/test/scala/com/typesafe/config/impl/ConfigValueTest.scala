/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import java.util.Collections
import java.net.URL
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigValueType
import com.typesafe.config.ConfigOrigin

class ConfigValueTest extends TestUtils {

    @Test
    def configOriginEquality() {
        val a = SimpleConfigOrigin.newSimple("foo")
        val sameAsA = SimpleConfigOrigin.newSimple("foo")
        val b = SimpleConfigOrigin.newSimple("bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configOriginSerializable() {
        val expectedSerialization = "" +
            "aced00057372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c6543" +
            "6f6e6669674f726967696e000000000000000102000649000d656e644c696e654e756d6265724900" +
            "0a6c696e654e756d6265724c000e636f6d6d656e74734f724e756c6c7400104c6a6176612f757469" +
            "6c2f4c6973743b4c000b6465736372697074696f6e7400124c6a6176612f6c616e672f537472696e" +
            "673b4c000a6f726967696e547970657400254c636f6d2f74797065736166652f636f6e6669672f69" +
            "6d706c2f4f726967696e547970653b4c000975726c4f724e756c6c71007e00027870ffffffffffff" +
            "ffff70740003666f6f7e720023636f6d2e74797065736166652e636f6e6669672e696d706c2e4f72" +
            "6967696e5479706500000000000000001200007872000e6a6176612e6c616e672e456e756d000000" +
            "0000000000120000787074000747454e4552494370"

        val a = SimpleConfigOrigin.newSimple("foo")
        checkSerializable(expectedSerialization, a)
    }

    @Test
    def configIntEquality() {
        val a = intValue(42)
        val sameAsA = intValue(42)
        val b = intValue(43)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configIntSerializable() {
        val expectedSerialization = "" +
            "aced000573720022636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e66696749" +
            "6e74000000000000000102000149000576616c756578720025636f6d2e74797065736166652e636f" +
            "6e6669672e696d706c2e436f6e6669674e756d62657200000000000000010200014c000c6f726967" +
            "696e616c546578747400124c6a6176612f6c616e672f537472696e673b7872002c636f6d2e747970" +
            "65736166652e636f6e6669672e696d706c2e4162737472616374436f6e66696756616c7565000000" +
            "00000000010200014c00066f726967696e74002d4c636f6d2f74797065736166652f636f6e666967" +
            "2f696d706c2f53696d706c65436f6e6669674f726967696e3b78707372002b636f6d2e7479706573" +
            "6166652e636f6e6669672e696d706c2e53696d706c65436f6e6669674f726967696e000000000000" +
            "000102000649000d656e644c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d" +
            "6d656e74734f724e756c6c7400104c6a6176612f7574696c2f4c6973743b4c000b64657363726970" +
            "74696f6e71007e00024c000a6f726967696e547970657400254c636f6d2f74797065736166652f63" +
            "6f6e6669672f696d706c2f4f726967696e547970653b4c000975726c4f724e756c6c71007e000278" +
            "70ffffffffffffffff7074000b66616b65206f726967696e7e720023636f6d2e7479706573616665" +
            "2e636f6e6669672e696d706c2e4f726967696e5479706500000000000000001200007872000e6a61" +
            "76612e6c616e672e456e756d0000000000000000120000787074000747454e455249437070000000" +
            "2a"

        val a = intValue(42)
        val b = checkSerializable(expectedSerialization, a)
        assertEquals(42, b.unwrapped)
    }

    @Test
    def configLongEquality() {
        val a = longValue(Integer.MAX_VALUE + 42L)
        val sameAsA = longValue(Integer.MAX_VALUE + 42L)
        val b = longValue(Integer.MAX_VALUE + 43L)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configIntAndLongEquality() {
        val longVal = longValue(42L)
        val intValue = longValue(42)
        val longValueB = longValue(43L)
        val intValueB = longValue(43)

        checkEqualObjects(intValue, longVal)
        checkEqualObjects(intValueB, longValueB)
        checkNotEqualObjects(intValue, longValueB)
        checkNotEqualObjects(intValueB, longVal)
    }

    private def configMap(pairs: (String, Int)*): java.util.Map[String, AbstractConfigValue] = {
        val m = new java.util.HashMap[String, AbstractConfigValue]()
        for (p <- pairs) {
            m.put(p._1, intValue(p._2))
        }
        m
    }

    @Test
    def configObjectEquality() {
        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val sameAsAMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val bMap = configMap("a" -> 3, "b" -> 4, "c" -> 5)
        // different keys is a different case in the equals implementation
        val cMap = configMap("x" -> 3, "y" -> 4, "z" -> 5)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val sameAsA = new SimpleConfigObject(fakeOrigin(), sameAsAMap)
        val b = new SimpleConfigObject(fakeOrigin(), bMap)
        val c = new SimpleConfigObject(fakeOrigin(), cMap)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkEqualObjects(b, b)
        checkEqualObjects(c, c)
        checkNotEqualObjects(a, b)
        checkNotEqualObjects(a, c)
        checkNotEqualObjects(b, c)

        // the config for an equal object is also equal
        val config = a.toConfig()
        checkEqualObjects(config, config)
        checkEqualObjects(config, sameAsA.toConfig())
        checkEqualObjects(a.toConfig(), config)
        checkNotEqualObjects(config, b.toConfig())
        checkNotEqualObjects(config, c.toConfig())

        // configs are not equal to objects
        checkNotEqualObjects(a, a.toConfig())
        checkNotEqualObjects(b, b.toConfig())
    }

    @Test
    def configObjectSerializable() {
        val expectedSerialization = "" +
            "aced00057372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c6543" +
            "6f6e6669674f626a65637400000000000000010200035a001069676e6f72657346616c6c6261636b" +
            "735a00087265736f6c7665644c000576616c756574000f4c6a6176612f7574696c2f4d61703b7872" +
            "002d636f6d2e74797065736166652e636f6e6669672e696d706c2e4162737472616374436f6e6669" +
            "674f626a65637400000000000000010200014c0006636f6e6669677400274c636f6d2f7479706573" +
            "6166652f636f6e6669672f696d706c2f53696d706c65436f6e6669673b7872002c636f6d2e747970" +
            "65736166652e636f6e6669672e696d706c2e4162737472616374436f6e66696756616c7565000000" +
            "00000000010200014c00066f726967696e74002d4c636f6d2f74797065736166652f636f6e666967" +
            "2f696d706c2f53696d706c65436f6e6669674f726967696e3b78707372002b636f6d2e7479706573" +
            "6166652e636f6e6669672e696d706c2e53696d706c65436f6e6669674f726967696e000000000000" +
            "000102000649000d656e644c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d" +
            "6d656e74734f724e756c6c7400104c6a6176612f7574696c2f4c6973743b4c000b64657363726970" +
            "74696f6e7400124c6a6176612f6c616e672f537472696e673b4c000a6f726967696e547970657400" +
            "254c636f6d2f74797065736166652f636f6e6669672f696d706c2f4f726967696e547970653b4c00" +
            "0975726c4f724e756c6c71007e00097870ffffffffffffffff7074000b66616b65206f726967696e" +
            "7e720023636f6d2e74797065736166652e636f6e6669672e696d706c2e4f726967696e5479706500" +
            "000000000000001200007872000e6a6176612e6c616e672e456e756d000000000000000012000078" +
            "7074000747454e455249437073720025636f6d2e74797065736166652e636f6e6669672e696d706c" +
            "2e53696d706c65436f6e66696700000000000000010200014c00066f626a65637474002f4c636f6d" +
            "2f74797065736166652f636f6e6669672f696d706c2f4162737472616374436f6e6669674f626a65" +
            "63743b787071007e00060001737200116a6176612e7574696c2e486173684d61700507dac1c31660" +
            "d103000246000a6c6f6164466163746f724900097468726573686f6c6478703f4000000000000c77" +
            "0800000010000000037400016273720022636f6d2e74797065736166652e636f6e6669672e696d70" +
            "6c2e436f6e666967496e74000000000000000102000149000576616c756578720025636f6d2e7479" +
            "7065736166652e636f6e6669672e696d706c2e436f6e6669674e756d626572000000000000000102" +
            "00014c000c6f726967696e616c5465787471007e00097871007e00047371007e0007ffffffffffff" +
            "ffff7071007e000c71007e000f707000000002740001637371007e00177371007e0007ffffffffff" +
            "ffffff7071007e000c71007e000f707000000003740001617371007e00177371007e0007ffffffff" +
            "ffffffff7071007e000c71007e000f70700000000178"

        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val b = checkSerializable(expectedSerialization, a)
        assertEquals(1, b.toConfig.getInt("a"))
        // check that deserialized Config and ConfigObject refer to each other
        assertTrue(b.toConfig.root eq b)
    }

    @Test
    def configListEquality() {
        val aScalaSeq = Seq(1, 2, 3) map { intValue(_): AbstractConfigValue }
        val aList = new SimpleConfigList(fakeOrigin(), aScalaSeq.asJava)
        val sameAsAList = new SimpleConfigList(fakeOrigin(), aScalaSeq.asJava)
        val bScalaSeq = Seq(4, 5, 6) map { intValue(_): AbstractConfigValue }
        val bList = new SimpleConfigList(fakeOrigin(), bScalaSeq.asJava)

        checkEqualObjects(aList, aList)
        checkEqualObjects(aList, sameAsAList)
        checkNotEqualObjects(aList, bList)
    }

    @Test
    def configListSerializable() {
        val expectedSerialization = "" +
            "aced000573720029636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c6543" +
            "6f6e6669674c69737400000000000000010200025a00087265736f6c7665644c000576616c756574" +
            "00104c6a6176612f7574696c2f4c6973743b7872002c636f6d2e74797065736166652e636f6e6669" +
            "672e696d706c2e4162737472616374436f6e66696756616c756500000000000000010200014c0006" +
            "6f726967696e74002d4c636f6d2f74797065736166652f636f6e6669672f696d706c2f53696d706c" +
            "65436f6e6669674f726967696e3b78707372002b636f6d2e74797065736166652e636f6e6669672e" +
            "696d706c2e53696d706c65436f6e6669674f726967696e000000000000000102000649000d656e64" +
            "4c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d6d656e74734f724e756c6c" +
            "71007e00014c000b6465736372697074696f6e7400124c6a6176612f6c616e672f537472696e673b" +
            "4c000a6f726967696e547970657400254c636f6d2f74797065736166652f636f6e6669672f696d70" +
            "6c2f4f726967696e547970653b4c000975726c4f724e756c6c71007e00067870ffffffffffffffff" +
            "7074000b66616b65206f726967696e7e720023636f6d2e74797065736166652e636f6e6669672e69" +
            "6d706c2e4f726967696e5479706500000000000000001200007872000e6a6176612e6c616e672e45" +
            "6e756d0000000000000000120000787074000747454e455249437001737200146a6176612e757469" +
            "6c2e4c696e6b65644c6973740c29535d4a608822030000787077040000000373720022636f6d2e74" +
            "797065736166652e636f6e6669672e696d706c2e436f6e666967496e740000000000000001020001" +
            "49000576616c756578720025636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e" +
            "6669674e756d62657200000000000000010200014c000c6f726967696e616c5465787471007e0006" +
            "7871007e00027371007e0005ffffffffffffffff7071007e000971007e000c707000000001737100" +
            "7e00107371007e0005ffffffffffffffff7071007e000971007e000c7070000000027371007e0010" +
            "7371007e0005ffffffffffffffff7071007e000971007e000c70700000000378"

        val aScalaSeq = Seq(1, 2, 3) map { intValue(_): AbstractConfigValue }
        val aList = new SimpleConfigList(fakeOrigin(), aScalaSeq.asJava)
        val bList = checkSerializable(expectedSerialization, aList)
        assertEquals(1, bList.get(0).unwrapped())
    }

    @Test
    def configSubstitutionEquality() {
        val a = subst("foo")
        val sameAsA = subst("foo")
        val b = subst("bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configSubstitutionSerializable() {
        val expectedSerialization = "" +
            "aced00057372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e66696753" +
            "7562737469747574696f6e61f02fc05eca6d7d0200035a001069676e6f72657346616c6c6261636b" +
            "7349000c7072656669784c656e6774684c00067069656365737400104c6a6176612f7574696c2f4c" +
            "6973743b7872002c636f6d2e74797065736166652e636f6e6669672e696d706c2e41627374726163" +
            "74436f6e66696756616c756500000000000000010200014c00066f726967696e74002d4c636f6d2f" +
            "74797065736166652f636f6e6669672f696d706c2f53696d706c65436f6e6669674f726967696e3b" +
            "78707372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c65436f6e" +
            "6669674f726967696e000000000000000102000649000d656e644c696e654e756d62657249000a6c" +
            "696e654e756d6265724c000e636f6d6d656e74734f724e756c6c71007e00014c000b646573637269" +
            "7074696f6e7400124c6a6176612f6c616e672f537472696e673b4c000a6f726967696e5479706574" +
            "00254c636f6d2f74797065736166652f636f6e6669672f696d706c2f4f726967696e547970653b4c" +
            "000975726c4f724e756c6c71007e00067870ffffffffffffffff7074000b66616b65206f72696769" +
            "6e7e720023636f6d2e74797065736166652e636f6e6669672e696d706c2e4f726967696e54797065" +
            "00000000000000001200007872000e6a6176612e6c616e672e456e756d0000000000000000120000" +
            "787074000747454e45524943700000000000737200146a6176612e7574696c2e4c696e6b65644c69" +
            "73740c29535d4a60882203000078707704000000017372002f636f6d2e74797065736166652e636f" +
            "6e6669672e696d706c2e537562737469747574696f6e45787072657373696f6e0000000000000001" +
            "0200025a00086f7074696f6e616c4c00047061746874001f4c636f6d2f74797065736166652f636f" +
            "6e6669672f696d706c2f506174683b7870007372001d636f6d2e74797065736166652e636f6e6669" +
            "672e696d706c2e5061746800000000000000010200024c0005666972737471007e00064c00097265" +
            "6d61696e64657271007e00117870740003666f6f7078"

        val a = subst("foo")
        val b = checkSerializable(expectedSerialization, a)
    }

    @Test
    def configDelayedMergeEquality() {
        val s1 = subst("foo")
        val s2 = subst("bar")
        val a = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](s1, s2).asJava)
        val sameAsA = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](s1, s2).asJava)
        val b = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](s2, s1).asJava)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configDelayedMergeSerializable() {
        val expectedSerialization = "" +
            "aced00057372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e66696744" +
            "656c617965644d6572676500000000000000010200025a001069676e6f72657346616c6c6261636b" +
            "734c0005737461636b7400104c6a6176612f7574696c2f4c6973743b7872002c636f6d2e74797065" +
            "736166652e636f6e6669672e696d706c2e4162737472616374436f6e66696756616c756500000000" +
            "000000010200014c00066f726967696e74002d4c636f6d2f74797065736166652f636f6e6669672f" +
            "696d706c2f53696d706c65436f6e6669674f726967696e3b78707372002b636f6d2e747970657361" +
            "66652e636f6e6669672e696d706c2e53696d706c65436f6e6669674f726967696e00000000000000" +
            "0102000649000d656e644c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d6d" +
            "656e74734f724e756c6c71007e00014c000b6465736372697074696f6e7400124c6a6176612f6c61" +
            "6e672f537472696e673b4c000a6f726967696e547970657400254c636f6d2f74797065736166652f" +
            "636f6e6669672f696d706c2f4f726967696e547970653b4c000975726c4f724e756c6c71007e0006" +
            "7870ffffffffffffffff7074000b66616b65206f726967696e7e720023636f6d2e74797065736166" +
            "652e636f6e6669672e696d706c2e4f726967696e5479706500000000000000001200007872000e6a" +
            "6176612e6c616e672e456e756d0000000000000000120000787074000747454e4552494370007372" +
            "00146a6176612e7574696c2e4c696e6b65644c6973740c29535d4a60882203000078707704000000" +
            "027372002b636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e66696753756273" +
            "7469747574696f6e61f02fc05eca6d7d0200035a001069676e6f72657346616c6c6261636b734900" +
            "0c7072656669784c656e6774684c000670696563657371007e00017871007e00027371007e0005ff" +
            "ffffffffffffff7071007e000971007e000c7000000000007371007e000e7704000000017372002f" +
            "636f6d2e74797065736166652e636f6e6669672e696d706c2e537562737469747574696f6e457870" +
            "72657373696f6e00000000000000010200025a00086f7074696f6e616c4c00047061746874001f4c" +
            "636f6d2f74797065736166652f636f6e6669672f696d706c2f506174683b7870007372001d636f6d" +
            "2e74797065736166652e636f6e6669672e696d706c2e5061746800000000000000010200024c0005" +
            "666972737471007e00064c000972656d61696e64657271007e00157870740003666f6f7078737100" +
            "7e00107371007e0005ffffffffffffffff7071007e000971007e000c7000000000007371007e000e" +
            "7704000000017371007e0014007371007e0017740003626172707878"

        val s1 = subst("foo")
        val s2 = subst("bar")
        val a = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](s1, s2).asJava)
        val b = checkSerializable(expectedSerialization, a)
    }

    @Test
    def configDelayedMergeObjectEquality() {
        val empty = SimpleConfigObject.empty()
        val s1 = subst("foo")
        val s2 = subst("bar")
        val a = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](empty, s1, s2).asJava)
        val sameAsA = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](empty, s1, s2).asJava)
        val b = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](empty, s2, s1).asJava)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configDelayedMergeObjectSerializable() {
        val expectedSerialization = "" +
            "aced000573720031636f6d2e74797065736166652e636f6e6669672e696d706c2e436f6e66696744" +
            "656c617965644d657267654f626a65637400000000000000010200025a001069676e6f7265734661" +
            "6c6c6261636b734c0005737461636b7400104c6a6176612f7574696c2f4c6973743b7872002d636f" +
            "6d2e74797065736166652e636f6e6669672e696d706c2e4162737472616374436f6e6669674f626a" +
            "65637400000000000000010200014c0006636f6e6669677400274c636f6d2f74797065736166652f" +
            "636f6e6669672f696d706c2f53696d706c65436f6e6669673b7872002c636f6d2e74797065736166" +
            "652e636f6e6669672e696d706c2e4162737472616374436f6e66696756616c756500000000000000" +
            "010200014c00066f726967696e74002d4c636f6d2f74797065736166652f636f6e6669672f696d70" +
            "6c2f53696d706c65436f6e6669674f726967696e3b78707372002b636f6d2e74797065736166652e" +
            "636f6e6669672e696d706c2e53696d706c65436f6e6669674f726967696e00000000000000010200" +
            "0649000d656e644c696e654e756d62657249000a6c696e654e756d6265724c000e636f6d6d656e74" +
            "734f724e756c6c71007e00014c000b6465736372697074696f6e7400124c6a6176612f6c616e672f" +
            "537472696e673b4c000a6f726967696e547970657400254c636f6d2f74797065736166652f636f6e" +
            "6669672f696d706c2f4f726967696e547970653b4c000975726c4f724e756c6c71007e00087870ff" +
            "ffffffffffffff7074000b66616b65206f726967696e7e720023636f6d2e74797065736166652e63" +
            "6f6e6669672e696d706c2e4f726967696e5479706500000000000000001200007872000e6a617661" +
            "2e6c616e672e456e756d0000000000000000120000787074000747454e455249437073720025636f" +
            "6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c65436f6e6669670000000000" +
            "0000010200014c00066f626a65637474002f4c636f6d2f74797065736166652f636f6e6669672f69" +
            "6d706c2f4162737472616374436f6e6669674f626a6563743b787071007e000600737200146a6176" +
            "612e7574696c2e4c696e6b65644c6973740c29535d4a60882203000078707704000000037372002b" +
            "636f6d2e74797065736166652e636f6e6669672e696d706c2e53696d706c65436f6e6669674f626a" +
            "65637400000000000000010200035a001069676e6f72657346616c6c6261636b735a00087265736f" +
            "6c7665644c000576616c756574000f4c6a6176612f7574696c2f4d61703b7871007e00027371007e" +
            "0007ffffffffffffffff7074000c656d70747920636f6e66696771007e000e707371007e00107100" +
            "7e001700017372001e6a6176612e7574696c2e436f6c6c656374696f6e7324456d7074794d617059" +
            "3614855adce7d002000078707372002b636f6d2e74797065736166652e636f6e6669672e696d706c" +
            "2e436f6e666967537562737469747574696f6e61f02fc05eca6d7d0200035a001069676e6f726573" +
            "46616c6c6261636b7349000c7072656669784c656e6774684c000670696563657371007e00017871" +
            "007e00047371007e0007ffffffffffffffff7071007e000b71007e000e7000000000007371007e00" +
            "137704000000017372002f636f6d2e74797065736166652e636f6e6669672e696d706c2e53756273" +
            "7469747574696f6e45787072657373696f6e00000000000000010200025a00086f7074696f6e616c" +
            "4c00047061746874001f4c636f6d2f74797065736166652f636f6e6669672f696d706c2f50617468" +
            "3b7870007372001d636f6d2e74797065736166652e636f6e6669672e696d706c2e50617468000000" +
            "00000000010200024c0005666972737471007e00084c000972656d61696e64657271007e00227870" +
            "740003666f6f70787371007e001d7371007e0007ffffffffffffffff7071007e000b71007e000e70" +
            "00000000007371007e00137704000000017371007e0021007371007e0024740003626172707878"

        val empty = SimpleConfigObject.empty()
        val s1 = subst("foo")
        val s2 = subst("bar")
        val a = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](empty, s1, s2).asJava)
        val b = checkSerializable(expectedSerialization, a)
    }

    @Test
    def valuesToString() {
        // just check that these don't throw, the exact output
        // isn't super important since it's just for debugging
        intValue(10).toString()
        longValue(11).toString()
        doubleValue(3.14).toString()
        stringValue("hi").toString()
        nullValue().toString()
        boolValue(true).toString()
        val emptyObj = SimpleConfigObject.empty()
        emptyObj.toString()
        (new SimpleConfigList(fakeOrigin(), Collections.emptyList[AbstractConfigValue]())).toString()
        subst("a").toString()
        substInString("b").toString()
        val dm = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](subst("a"), subst("b")).asJava)
        dm.toString()
        val dmo = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](emptyObj, subst("a"), subst("b")).asJava)
        dmo.toString()

        fakeOrigin().toString()
    }

    private def unsupported(body: => Unit) {
        intercept[UnsupportedOperationException] {
            body
        }
    }

    @Test
    def configObjectUnwraps() {
        val m = new SimpleConfigObject(fakeOrigin(),
            configMap("a" -> 1, "b" -> 2, "c" -> 3))
        assertEquals(Map("a" -> 1, "b" -> 2, "c" -> 3), m.unwrapped().asScala)
    }

    @Test
    def configObjectImplementsMap() {
        val m: ConfigObject = new SimpleConfigObject(fakeOrigin(),
            configMap("a" -> 1, "b" -> 2, "c" -> 3))

        assertEquals(intValue(1), m.get("a"))
        assertEquals(intValue(2), m.get("b"))
        assertEquals(intValue(3), m.get("c"))
        assertNull(m.get("d"))
        // get can take a non-string
        assertNull(m.get(new Object()))

        assertTrue(m.containsKey("a"))
        assertFalse(m.containsKey("z"))
        // containsKey can take a non-string
        assertFalse(m.containsKey(new Object()))

        assertTrue(m.containsValue(intValue(1)))
        assertFalse(m.containsValue(intValue(10)))

        // can take a non-ConfigValue
        assertFalse(m.containsValue(new Object()))

        assertFalse(m.isEmpty())

        assertEquals(3, m.size())

        val values = Set(intValue(1), intValue(2), intValue(3))
        assertEquals(values, m.values().asScala.toSet)
        assertEquals(values, m.entrySet().asScala map { _.getValue() } toSet)

        val keys = Set("a", "b", "c")
        assertEquals(keys, m.keySet().asScala.toSet)
        assertEquals(keys, m.entrySet().asScala map { _.getKey() } toSet)

        unsupported { m.clear() }
        unsupported { m.put("hello", intValue(42)) }
        unsupported { m.putAll(Collections.emptyMap[String, AbstractConfigValue]()) }
        unsupported { m.remove("a") }
    }

    @Test
    def configListImplementsList() {
        val scalaSeq = Seq[AbstractConfigValue](stringValue("a"), stringValue("b"), stringValue("c"))
        val l: ConfigList = new SimpleConfigList(fakeOrigin(),
            scalaSeq.asJava)

        assertEquals(scalaSeq(0), l.get(0))
        assertEquals(scalaSeq(1), l.get(1))
        assertEquals(scalaSeq(2), l.get(2))

        assertTrue(l.contains(stringValue("a")))

        assertTrue(l.containsAll(List[AbstractConfigValue](stringValue("b")).asJava))
        assertFalse(l.containsAll(List[AbstractConfigValue](stringValue("d")).asJava))

        assertEquals(1, l.indexOf(scalaSeq(1)))

        assertFalse(l.isEmpty());

        assertEquals(scalaSeq, l.iterator().asScala.toSeq)

        unsupported { l.iterator().remove() }

        assertEquals(1, l.lastIndexOf(scalaSeq(1)))

        val li = l.listIterator()
        var i = 0
        while (li.hasNext()) {
            assertEquals(i > 0, li.hasPrevious())
            assertEquals(i, li.nextIndex())
            assertEquals(i - 1, li.previousIndex())

            unsupported { li.remove() }
            unsupported { li.add(intValue(3)) }
            unsupported { li.set(stringValue("foo")) }

            val v = li.next()
            assertEquals(l.get(i), v)

            if (li.hasPrevious()) {
                // go backward
                assertEquals(scalaSeq(i), li.previous())
                // go back forward
                li.next()
            }

            i += 1
        }

        l.listIterator(1) // doesn't throw!

        assertEquals(3, l.size())

        assertEquals(scalaSeq.tail, l.subList(1, l.size()).asScala)

        assertEquals(scalaSeq, l.toArray.toList)

        assertEquals(scalaSeq, l.toArray(new Array[ConfigValue](l.size())).toList)

        unsupported { l.add(intValue(3)) }
        unsupported { l.add(1, intValue(4)) }
        unsupported { l.addAll(List[ConfigValue]().asJava) }
        unsupported { l.addAll(1, List[ConfigValue]().asJava) }
        unsupported { l.clear() }
        unsupported { l.remove(intValue(2)) }
        unsupported { l.remove(1) }
        unsupported { l.removeAll(List[ConfigValue](intValue(1)).asJava) }
        unsupported { l.retainAll(List[ConfigValue](intValue(1)).asJava) }
        unsupported { l.set(0, intValue(42)) }
    }

    private def unresolved(body: => Unit) {
        intercept[ConfigException.NotResolved] {
            body
        }
    }

    @Test
    def notResolvedThrown() {
        // ConfigSubstitution
        unresolved { subst("foo").valueType() }
        unresolved { subst("foo").unwrapped() }

        // ConfigDelayedMerge
        val dm = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](subst("a"), subst("b")).asJava)
        unresolved { dm.valueType() }
        unresolved { dm.unwrapped() }

        // ConfigDelayedMergeObject
        val emptyObj = SimpleConfigObject.empty()
        val dmo = new ConfigDelayedMergeObject(fakeOrigin(),
            List[AbstractConfigValue](emptyObj, subst("a"), subst("b")).asJava)
        assertEquals(ConfigValueType.OBJECT, dmo.valueType())
        unresolved { dmo.unwrapped() }
        unresolved { dmo.containsKey(null) }
        unresolved { dmo.containsValue(null) }
        unresolved { dmo.entrySet() }
        unresolved { dmo.isEmpty() }
        unresolved { dmo.keySet() }
        unresolved { dmo.size() }
        unresolved { dmo.values() }
        unresolved { dmo.toConfig.getInt("foo") }
    }

    @Test
    def roundTripNumbersThroughString() {
        // formats rounded off with E notation
        val a = "132454454354353245.3254652656454808909932874873298473298472"
        // formats as 100000.0
        val b = "1e6"
        // formats as 5.0E-5
        val c = "0.00005"
        // formats as 1E100 (capital E)
        val d = "1e100"

        val obj = parseConfig("{ a : " + a + ", b : " + b + ", c : " + c + ", d : " + d + "}")
        assertEquals(Seq(a, b, c, d),
            Seq("a", "b", "c", "d") map { obj.getString(_) })

        // make sure it still works if we're doing concatenation
        val obj2 = parseConfig("{ a : xx " + a + " yy, b : xx " + b + " yy, c : xx " + c + " yy, d : xx " + d + " yy}")
        assertEquals(Seq(a, b, c, d) map { "xx " + _ + " yy" },
            Seq("a", "b", "c", "d") map { obj2.getString(_) })
    }

    @Test
    def mergeOriginsWorks() {
        def o(desc: String, empty: Boolean) = {
            val values = new java.util.HashMap[String, AbstractConfigValue]()
            if (!empty)
                values.put("hello", intValue(37))
            new SimpleConfigObject(SimpleConfigOrigin.newSimple(desc), values);
        }
        def m(values: AbstractConfigObject*) = {
            AbstractConfigObject.mergeOrigins(values: _*).description()
        }

        // simplest case
        assertEquals("merge of a,b", m(o("a", false), o("b", false)))
        // combine duplicate "merge of"
        assertEquals("merge of a,x,y", m(o("a", false), o("merge of x,y", false)))
        assertEquals("merge of a,b,x,y", m(o("merge of a,b", false), o("merge of x,y", false)))
        // ignore empty objects
        assertEquals("a", m(o("foo", true), o("a", false)))
        // unless they are all empty, pick the first one
        assertEquals("foo", m(o("foo", true), o("a", true)))
        // merge just one
        assertEquals("foo", m(o("foo", false)))
        // merge three
        assertEquals("merge of a,b,c", m(o("a", false), o("b", false), o("c", false)))
    }

    @Test
    def hasPathWorks() {
        val empty = parseConfig("{}")

        assertFalse(empty.hasPath("foo"))

        val obj = parseConfig("a=null, b.c.d=11, foo=bar")

        // returns true for the non-null values
        assertTrue(obj.hasPath("foo"))
        assertTrue(obj.hasPath("b.c.d"))
        assertTrue(obj.hasPath("b.c"))
        assertTrue(obj.hasPath("b"))

        // hasPath() is false for null values but containsKey is true
        assertEquals(nullValue(), obj.root.get("a"))
        assertTrue(obj.root.containsKey("a"))
        assertFalse(obj.hasPath("a"))

        // false for totally absent values
        assertFalse(obj.root.containsKey("notinhere"))
        assertFalse(obj.hasPath("notinhere"))

        // throws proper exceptions
        intercept[ConfigException.BadPath] {
            empty.hasPath("a.")
        }

        intercept[ConfigException.BadPath] {
            empty.hasPath("..")
        }

    }

    @Test
    def newNumberWorks() {
        def nL(v: Long) = ConfigNumber.newNumber(fakeOrigin(), v, null)
        def nD(v: Double) = ConfigNumber.newNumber(fakeOrigin(), v, null)

        // the general idea is that the destination type should depend
        // only on the actual numeric value, not on the type of the source
        // value.
        assertEquals(3.14, nD(3.14).unwrapped())
        assertEquals(1, nL(1).unwrapped())
        assertEquals(1, nD(1.0).unwrapped())
        assertEquals(Int.MaxValue + 1L, nL(Int.MaxValue + 1L).unwrapped())
        assertEquals(Int.MinValue - 1L, nL(Int.MinValue - 1L).unwrapped())
        assertEquals(Int.MaxValue + 1L, nD(Int.MaxValue + 1.0).unwrapped())
        assertEquals(Int.MinValue - 1L, nD(Int.MinValue - 1.0).unwrapped())
    }

    @Test
    def automaticBooleanConversions() {
        val trues = parseObject("{ a=true, b=yes, c=on }").toConfig
        assertEquals(true, trues.getBoolean("a"))
        assertEquals(true, trues.getBoolean("b"))
        assertEquals(true, trues.getBoolean("c"))

        val falses = parseObject("{ a=false, b=no, c=off }").toConfig
        assertEquals(false, falses.getBoolean("a"))
        assertEquals(false, falses.getBoolean("b"))
        assertEquals(false, falses.getBoolean("c"))
    }

    @Test
    def configOriginFileAndLine() {
        val hasFilename = SimpleConfigOrigin.newFile("foo")
        val noFilename = SimpleConfigOrigin.newSimple("bar")
        val filenameWithLine = hasFilename.setLineNumber(3)
        val noFilenameWithLine = noFilename.setLineNumber(4)

        assertEquals("foo", hasFilename.filename())
        assertEquals("foo", filenameWithLine.filename())
        assertNull(noFilename.filename())
        assertNull(noFilenameWithLine.filename())

        assertEquals("foo", hasFilename.description())
        assertEquals("bar", noFilename.description())

        assertEquals(-1, hasFilename.lineNumber())
        assertEquals(-1, noFilename.lineNumber())

        assertEquals("foo: 3", filenameWithLine.description())
        assertEquals("bar: 4", noFilenameWithLine.description());

        assertEquals(3, filenameWithLine.lineNumber())
        assertEquals(4, noFilenameWithLine.lineNumber())

        // the filename is made absolute when converting to url
        assertTrue(hasFilename.url.toExternalForm.contains("foo"))
        assertNull(noFilename.url)
        assertEquals("file:/baz", SimpleConfigOrigin.newFile("/baz").url.toExternalForm)

        val urlOrigin = SimpleConfigOrigin.newURL(new URL("file:/foo"))
        assertEquals("/foo", urlOrigin.filename)
        assertEquals("file:/foo", urlOrigin.url.toExternalForm)
    }

    @Test
    def withOnly() {
        val obj = parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }")
        assertEquals("keep only a", parseObject("{ a=1 }"), obj.withOnlyKey("a"))
        assertEquals("keep only e", parseObject("{ e.f.g=4 }"), obj.withOnlyKey("e"))
        assertEquals("keep only c.d", parseObject("{ c.d.y=3, c.d.z=5 }"), obj.toConfig.withOnlyPath("c.d").root)
        assertEquals("keep only c.d.z", parseObject("{ c.d.z=5 }"), obj.toConfig.withOnlyPath("c.d.z").root)
        assertEquals("keep nonexistent key", parseObject("{ }"), obj.withOnlyKey("nope"))
        assertEquals("keep nonexistent path", parseObject("{ }"), obj.toConfig.withOnlyPath("q.w.e.r.t.y").root)
        assertEquals("keep only nonexistent underneath non-object", parseObject("{ }"), obj.toConfig.withOnlyPath("a.nonexistent").root)
        assertEquals("keep only nonexistent underneath nested non-object", parseObject("{ }"), obj.toConfig.withOnlyPath("c.d.z.nonexistent").root)
    }

    @Test
    def without() {
        val obj = parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }")
        assertEquals("without a", parseObject("{ b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"), obj.withoutKey("a"))
        assertEquals("without c", parseObject("{ a=1, b=2, e.f.g=4 }"), obj.withoutKey("c"))
        assertEquals("without c.d", parseObject("{ a=1, b=2, e.f.g=4, c={} }"), obj.toConfig.withoutPath("c.d").root)
        assertEquals("without c.d.z", parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4 }"), obj.toConfig.withoutPath("c.d.z").root)
        assertEquals("without nonexistent key", parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"), obj.withoutKey("nonexistent"))
        assertEquals("without nonexistent path", parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"), obj.toConfig.withoutPath("q.w.e.r.t.y").root)
        assertEquals("without nonexistent path with existing prefix", parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"), obj.toConfig.withoutPath("a.foo").root)
    }
}
