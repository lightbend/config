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
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigFactory

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
    def configOriginNotSerializable() {
        val a = SimpleConfigOrigin.newSimple("foo")
        checkNotSerializable(a)
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
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_902000000_-050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000009020000002A0002_4_20103" +
            "000000010001_x"
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
    def configLongSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_E02000000_9050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000015030000000080000029000A" +
            "_2_1_4_7_4_8_3_6_8_90103000000010001_x"

        val a = longValue(Integer.MAX_VALUE + 42L)
        val b = checkSerializable(expectedSerialization, a)
        assertEquals(Integer.MAX_VALUE + 42L, b.unwrapped)
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

    @Test
    def configDoubleEquality() {
        val a = doubleValue(3.14)
        val sameAsA = doubleValue(3.14)
        val b = doubleValue(4.14)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configDoubleSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w3F02000000_3050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n09000000010001040000000F0440091EB8_QEB851F0004" +
            "_3_._1_40103000000010001_x"

        val a = doubleValue(3.14)
        val b = checkSerializable(expectedSerialization, a)
        assertEquals(3.14, b.unwrapped)
    }

    @Test
    def configIntAndDoubleEquality() {
        val doubleVal = doubleValue(3.0)
        val intValue = longValue(3)
        val doubleValueB = doubleValue(4.0)
        val intValueB = doubleValue(4)

        checkEqualObjects(intValue, doubleVal)
        checkEqualObjects(intValueB, doubleValueB)
        checkNotEqualObjects(intValue, doubleValueB)
        checkNotEqualObjects(intValueB, doubleVal)
    }

    @Test
    def configNullSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_10200000025050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000001000103000000010001_x"

        val a = nullValue
        val b = checkSerializable(expectedSerialization, a)
        assertNull("b is null", b.unwrapped)
    }

    @Test
    def configBooleanSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_20200000026050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n09000000010001040000000201010103000000010001_x"

        val a = boolValue(true)
        val b = checkSerializable(expectedSerialization, a)
        assertEquals(true, b.unwrapped)
    }

    @Test
    def configStringSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_F02000000_:050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000016050013_T_h_e_ _q_u_i_c" +
            "_k_ _b_r_o_w_n_ _f_o_x0103000000010001_x"

        val a = stringValue("The quick brown fox")
        val b = checkSerializable(expectedSerialization, a)
        assertEquals("The quick brown fox", b.unwrapped)
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
    def java6ConfigObjectSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
            "000101040000000802000000010001_1010001_c050000000101040000000802000000030001_301" +
            "0001_b050000000101040000000802000000020001_2010103000000010001_x"

        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val b = checkSerializableOldFormat(expectedSerialization, a)
        assertEquals(1, b.toConfig.getInt("a"))
        // check that deserialized Config and ConfigObject refer to each other
        assertTrue(b.toConfig.root eq b)
    }

    @Test
    def java6ConfigConfigSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
            "000101040000000802000000010001_1010001_c050000000101040000000802000000030001_301" +
            "0001_b050000000101040000000802000000020001_2010103000000010101_x"

        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val b = checkSerializableOldFormat(expectedSerialization, a.toConfig())
        assertEquals(1, b.getInt("a"))
        // check that deserialized Config and ConfigObject refer to each other
        assertTrue(b.root.toConfig eq b)
    }

    @Test
    def configObjectSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
            "000101040000000802000000010001_1010001_b050000000101040000000802000000020001_201" +
            "0001_c050000000101040000000802000000030001_3010103000000010001_x"

        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val b = checkSerializable(expectedSerialization, a)
        assertEquals(1, b.toConfig.getInt("a"))
        // check that deserialized Config and ConfigObject refer to each other
        assertTrue(b.toConfig.root eq b)
    }

    @Test
    def configConfigSerializable() {
        val expectedSerialization = "" +
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
            "000101040000000802000000010001_1010001_b050000000101040000000802000000020001_201" +
            "0001_c050000000101040000000802000000030001_3010103000000010101_x"

        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val b = checkSerializable(expectedSerialization, a.toConfig())
        assertEquals(1, b.getInt("a"))
        // check that deserialized Config and ConfigObject refer to each other
        assertTrue(b.root.toConfig eq b)
    }

    /**
     * Reproduces the issue <a href=https://github.com/lightbend/config/issues/461>#461</a>.
     * <p>
     * We use a custom de-/serializer that encodes String objects in a JDK-incompatible way. Encoding used here
     * is rather simplistic: a long indicating the length in bytes (JDK uses a variable length integer) followed
     * by the string's bytes. Running this test with the original SerializedConfigValue.readExternal()
     * implementation results in an EOFException thrown during deserialization.
     */
    @Test
    def configConfigCustomSerializable() {
        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val expected = new SimpleConfigObject(fakeOrigin(), aMap).toConfig
        val actual = checkSerializableWithCustomSerializer(expected)

        assertEquals(expected, actual)
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
            "ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
            "_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_q02000000_e050000001906" +
            "0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_A0600000003050000000101" +
            "040000000802000000010001_101050000000101040000000802000000020001_201050000000101" +
            "040000000802000000030001_3010103000000010001_x"
        val aScalaSeq = Seq(1, 2, 3) map { intValue(_): AbstractConfigValue }
        val aList = new SimpleConfigList(fakeOrigin(), aScalaSeq.asJava)
        val bList = checkSerializable(expectedSerialization, aList)
        assertEquals(1, bList.get(0).unwrapped())
    }

    @Test
    def configReferenceEquality() {
        val a = subst("foo")
        val sameAsA = subst("foo")
        val b = subst("bar")
        val c = subst("foo", optional = true)

        assertTrue("wrong type " + a, a.isInstanceOf[ConfigReference])
        assertTrue("wrong type " + b, b.isInstanceOf[ConfigReference])
        assertTrue("wrong type " + c, c.isInstanceOf[ConfigReference])

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
        checkNotEqualObjects(a, c)

    }

    @Test
    def configReferenceNotSerializable() {
        val a = subst("foo")
        assertTrue("wrong type " + a, a.isInstanceOf[ConfigReference])
        checkNotSerializable(a)
    }

    @Test
    def configConcatenationEquality() {
        val a = substInString("foo")
        val sameAsA = substInString("foo")
        val b = substInString("bar")
        val c = substInString("foo", optional = true)

        assertTrue("wrong type " + a, a.isInstanceOf[ConfigConcatenation])
        assertTrue("wrong type " + b, b.isInstanceOf[ConfigConcatenation])
        assertTrue("wrong type " + c, c.isInstanceOf[ConfigConcatenation])

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
        checkNotEqualObjects(a, c)
    }

    @Test
    def configConcatenationNotSerializable() {
        val a = substInString("foo")
        assertTrue("wrong type " + a, a.isInstanceOf[ConfigConcatenation])
        checkNotSerializable(a)
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
    def configDelayedMergeNotSerializable() {
        val s1 = subst("foo")
        val s2 = subst("bar")
        val a = new ConfigDelayedMerge(fakeOrigin(), List[AbstractConfigValue](s1, s2).asJava)
        checkNotSerializable(a)
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
    def configDelayedMergeObjectNotSerializable() {
        val empty = SimpleConfigObject.empty()
        val s1 = subst("foo")
        val s2 = subst("bar")
        val a = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](empty, s1, s2).asJava)
        checkNotSerializable(a)
    }

    @Test
    def valuesToString() {
        // just check that these don't throw, the exact output
        // isn't super important since it's just for debugging
        intValue(10).toString()
        longValue(11).toString()
        doubleValue(3.14).toString()
        stringValue("hi").toString()
        nullValue.toString()
        boolValue(true).toString()
        val emptyObj = SimpleConfigObject.empty()
        emptyObj.toString()
        new SimpleConfigList(fakeOrigin(), Collections.emptyList[AbstractConfigValue]()).toString()
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
        assertEquals(values, (m.entrySet().asScala map { _.getValue() }).toSet)

        val keys = Set("a", "b", "c")
        assertEquals(keys, m.keySet().asScala.toSet)
        assertEquals(keys, (m.entrySet().asScala map { _.getKey() }).toSet)

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

        assertEquals(scalaSeq.head, l.get(0))
        assertEquals(scalaSeq(1), l.get(1))
        assertEquals(scalaSeq(2), l.get(2))

        assertTrue(l.contains(stringValue("a")))

        assertTrue(l.containsAll(List[AbstractConfigValue](stringValue("b")).asJava))
        assertFalse(l.containsAll(List[AbstractConfigValue](stringValue("d")).asJava))

        assertEquals(1, l.indexOf(scalaSeq(1)))

        assertFalse(l.isEmpty())

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
        unresolved { dmo.get("foo") }
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
            Seq("a", "b", "c", "d") map { obj.getString })

        // make sure it still works if we're doing concatenation
        val obj2 = parseConfig("{ a : xx " + a + " yy, b : xx " + b + " yy, c : xx " + c + " yy, d : xx " + d + " yy}")
        assertEquals(Seq(a, b, c, d) map { "xx " + _ + " yy" },
            Seq("a", "b", "c", "d") map { obj2.getString })
    }

    @Test
    def mergeOriginsWorks() {
        def o(desc: String, empty: Boolean) = {
            val values = new java.util.HashMap[String, AbstractConfigValue]()
            if (!empty)
                values.put("hello", intValue(37))
            new SimpleConfigObject(SimpleConfigOrigin.newSimple(desc), values)
        }
        def m(values: AbstractConfigObject*) = {
            AbstractConfigObject.mergeOrigins(values: _*).description()
        }

        // simplest case
        assertEquals("merge of a,b", m(o("a", empty = false), o("b", empty = false)))
        // combine duplicate "merge of"
        assertEquals("merge of a,x,y", m(o("a", empty = false), o("merge of x,y", empty = false)))
        assertEquals("merge of a,b,x,y", m(o("merge of a,b", empty = false), o("merge of x,y", empty = false)))
        // ignore empty objects
        assertEquals("a", m(o("foo", empty = true), o("a", empty = false)))
        // unless they are all empty, pick the first one
        assertEquals("foo", m(o("foo", empty = true), o("a", empty = true)))
        // merge just one
        assertEquals("foo", m(o("foo", empty = false)))
        // merge three
        assertEquals("merge of a,b,c", m(o("a", empty = false), o("b", empty = false), o("c", empty = false)))
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
        assertEquals(nullValue, obj.root.get("a"))
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
        val filenameWithLine = hasFilename.withLineNumber(3)
        val noFilenameWithLine = noFilename.withLineNumber(4)

        assertEquals("foo", hasFilename.filename())
        assertEquals("foo", filenameWithLine.filename())
        assertNull(noFilename.filename())
        assertNull(noFilenameWithLine.filename())

        assertEquals("foo", hasFilename.description())
        assertEquals("bar", noFilename.description())

        assertEquals(-1, hasFilename.lineNumber())
        assertEquals(-1, noFilename.lineNumber())

        assertEquals("foo: 3", filenameWithLine.description())
        assertEquals("bar: 4", noFilenameWithLine.description())

        assertEquals(3, filenameWithLine.lineNumber())
        assertEquals(4, noFilenameWithLine.lineNumber())

        // the filename is made absolute when converting to url
        assertTrue(hasFilename.url.toExternalForm.contains("foo"))
        assertNull(noFilename.url)
        val rootFile = SimpleConfigOrigin.newFile("/baz")
        val rootFileURL = if (isWindows) s"file:/$userDrive/baz" else "file:/baz"
        assertEquals(rootFileURL, rootFile.url.toExternalForm)

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
    def withOnlyInvolvingUnresolved() {
        val obj = parseObject("{ a = {}, a=${x}, b=${y}, b=${z}, x={asf:1}, y=2, z=3 }")
        assertEquals("keep only a.asf", parseObject("{ a={asf:1} }"), obj.toConfig.resolve.withOnlyPath("a.asf").root)

        intercept[ConfigException.UnresolvedSubstitution] {
            obj.withOnlyKey("a").toConfig.resolve
        }

        intercept[ConfigException.UnresolvedSubstitution] {
            obj.withOnlyKey("b").toConfig.resolve
        }

        assertEquals(ResolveStatus.UNRESOLVED, obj.resolveStatus())
        assertEquals(ResolveStatus.RESOLVED, obj.withOnlyKey("z").resolveStatus())
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

    @Test
    def withoutInvolvingUnresolved() {
        val obj = parseObject("{ a = {}, a=${x}, b=${y}, b=${z}, x={asf:1}, y=2, z=3 }")
        assertEquals("without a.asf", parseObject("{ a={}, b=3, x={asf:1}, y=2, z=3 }"), obj.toConfig.resolve.withoutPath("a.asf").root)

        intercept[ConfigException.UnresolvedSubstitution] {
            obj.withoutKey("x").toConfig.resolve
        }

        intercept[ConfigException.UnresolvedSubstitution] {
            obj.withoutKey("z").toConfig.resolve
        }

        assertEquals(ResolveStatus.UNRESOLVED, obj.resolveStatus())
        assertEquals(ResolveStatus.UNRESOLVED, obj.withoutKey("a").resolveStatus())
        assertEquals(ResolveStatus.RESOLVED, obj.withoutKey("a").withoutKey("b").resolveStatus())
    }

    @Test
    def atPathWorksOneElement() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = v.atPath("a")
        assertEquals(parseConfig("a=42"), config)
        assertTrue(config.getValue("a") eq v)
        assertTrue(config.origin.description.contains("atPath"))
    }

    @Test
    def atPathWorksTwoElements() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = v.atPath("a.b")
        assertEquals(parseConfig("a.b=42"), config)
        assertTrue(config.getValue("a.b") eq v)
        assertTrue(config.origin.description.contains("atPath"))
    }

    @Test
    def atPathWorksFourElements() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = v.atPath("a.b.c.d")
        assertEquals(parseConfig("a.b.c.d=42"), config)
        assertTrue(config.getValue("a.b.c.d") eq v)
        assertTrue(config.origin.description.contains("atPath"))
    }

    @Test
    def atKeyWorks() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = v.atKey("a")
        assertEquals(parseConfig("a=42"), config)
        assertTrue(config.getValue("a") eq v)
        assertTrue(config.origin.description.contains("atKey"))
    }

    @Test
    def withValueDepth1FromEmpty() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = ConfigFactory.empty.withValue("a", v)
        assertEquals(parseConfig("a=42"), config)
        assertTrue(config.getValue("a") eq v)
    }

    @Test
    def withValueDepth2FromEmpty() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = ConfigFactory.empty.withValue("a.b", v)
        assertEquals(parseConfig("a.b=42"), config)
        assertTrue(config.getValue("a.b") eq v)
    }

    @Test
    def withValueDepth3FromEmpty() {
        val v = ConfigValueFactory.fromAnyRef(42)
        val config = ConfigFactory.empty.withValue("a.b.c", v)
        assertEquals(parseConfig("a.b.c=42"), config)
        assertTrue(config.getValue("a.b.c") eq v)
    }

    @Test
    def withValueDepth1OverwritesExisting() {
        val v = ConfigValueFactory.fromAnyRef(47)
        val old = v.atPath("a")
        val config = old.withValue("a", ConfigValueFactory.fromAnyRef(42))
        assertEquals(parseConfig("a=42"), config)
        assertEquals(42, config.getInt("a"))
    }

    @Test
    def withValueDepth2OverwritesExisting() {
        val v = ConfigValueFactory.fromAnyRef(47)
        val old = v.atPath("a.b")
        val config = old.withValue("a.b", ConfigValueFactory.fromAnyRef(42))
        assertEquals(parseConfig("a.b=42"), config)
        assertEquals(42, config.getInt("a.b"))
    }

    @Test
    def withValueInsideExistingObject() {
        val v = ConfigValueFactory.fromAnyRef(47)
        val old = v.atPath("a.c")
        val config = old.withValue("a.b", ConfigValueFactory.fromAnyRef(42))
        assertEquals(parseConfig("a.b=42,a.c=47"), config)
        assertEquals(42, config.getInt("a.b"))
        assertEquals(47, config.getInt("a.c"))
    }

    @Test
    def withValueBuildComplexConfig() {
        val v1 = ConfigValueFactory.fromAnyRef(1)
        val v2 = ConfigValueFactory.fromAnyRef(2)
        val v3 = ConfigValueFactory.fromAnyRef(3)
        val v4 = ConfigValueFactory.fromAnyRef(4)
        val config = ConfigFactory.empty
            .withValue("a", v1)
            .withValue("b.c", v2)
            .withValue("b.d", v3)
            .withValue("x.y.z", v4)
        assertEquals(parseConfig("a=1,b.c=2,b.d=3,x.y.z=4"), config)
    }

    @Test
    def configOriginsInSerialization() {
        import scala.collection.JavaConverters._
        val bases = Seq(
            SimpleConfigOrigin.newSimple("foo"),
            SimpleConfigOrigin.newFile("/tmp/blahblah"),
            SimpleConfigOrigin.newURL(new URL("http://example.com")),
            SimpleConfigOrigin.newResource("myresource"),
            SimpleConfigOrigin.newResource("myresource", new URL("file://foo/bar")))
        val combos = bases.flatMap({
            base =>
                Seq(
                    (base, base.withComments(Seq("this is a comment", "another one").asJava)),
                    (base, base.withComments(null)),
                    (base, base.withLineNumber(41)),
                    (base, SimpleConfigOrigin.mergeOrigins(base.withLineNumber(10), base.withLineNumber(20))))
        }) ++
            bases.sliding(2).map({ seq => (seq.head, seq.tail.head) }) ++
            bases.sliding(3).map({ seq => (seq.head, seq.tail.tail.head) }) ++
            bases.sliding(4).map({ seq => (seq.head, seq.tail.tail.tail.head) })
        val withFlipped = combos ++ combos.map(_.swap)
        val withDuplicate = withFlipped ++ withFlipped.map(p => (p._1, p._1))
        val values = withDuplicate.flatMap({
            combo =>
                Seq(
                    // second inside first
                    new SimpleConfigList(combo._1,
                        Seq[AbstractConfigValue](new ConfigInt(combo._2, 42, "42")).asJava),
                    // triple-nested means we have to null then un-null then null, which is a tricky case
                    // in the origin-serialization code.
                    new SimpleConfigList(combo._1,
                        Seq[AbstractConfigValue](new SimpleConfigList(combo._2,
                            Seq[AbstractConfigValue](new ConfigInt(combo._1, 42, "42")).asJava)).asJava))
        })
        def top(v: SimpleConfigList) = v.origin
        def middle(v: SimpleConfigList) = v.get(0).origin
        def bottom(v: SimpleConfigList) = v.get(0) match {
            case list: ConfigList => Some(list.get(0).origin)
            case _ => None
        }

        //System.err.println("values=\n  " + values.map(v => top(v).description + ", " + middle(v).description + ", " + bottom(v).map(_.description)).mkString("\n  "))
        for (v <- values) {
            val deserialized = checkSerializable(v)
            // double-check that checkSerializable verified the origins
            assertEquals(top(v), top(deserialized))
            assertEquals(middle(v), middle(deserialized))
            assertEquals(bottom(v), bottom(deserialized))
        }
    }

    @Test
    def renderWithNewlinesInDescription(): Unit = {
        val v = ConfigValueFactory.fromAnyRef(89, "this is a description\nwith some\nnewlines")
        val list = new SimpleConfigList(SimpleConfigOrigin.newSimple("\n5\n6\n7\n"),
            java.util.Collections.singletonList(v.asInstanceOf[AbstractConfigValue]))
        val conf = ConfigFactory.empty().withValue("bar", list)
        val rendered = conf.root.render()
        def assertHas(s: String): Unit =
            assertTrue(s"has ${s.replace("\n", "\\n")} in it", rendered.contains(s))
        assertHas("is a description\n")
        assertHas("with some\n")
        assertHas("newlines\n")
        assertHas("#\n")
        assertHas("5\n")
        assertHas("6\n")
        assertHas("7\n")
        val parsed = ConfigFactory.parseString(rendered)

        assertEquals(conf, parsed)
    }

    @Test
    def renderSorting(): Unit = {
        val config = parseConfig("""0=a,1=b,2=c,999999999999999999999999999999999999999999999=0,3=d,10=e,20a=f,20=g,30=h""")
        val rendered = config.root.render(ConfigRenderOptions.concise())
        assertEquals("""{"0":"a","1":"b","2":"c","3":"d","10":"e","20":"g","30":"h","999999999999999999999999999999999999999999999":0,"20a":"f"}""", rendered)
    }
}
