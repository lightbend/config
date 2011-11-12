package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import java.util.Collections
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigValueType

class ConfigValueTest extends TestUtils {

    @Test
    def configIntEquality() {
        val a = new ConfigInt(fakeOrigin(), 42)
        val sameAsA = new ConfigInt(fakeOrigin(), 42)
        val b = new ConfigInt(fakeOrigin(), 43)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configLongEquality() {
        val a = new ConfigLong(fakeOrigin(), Integer.MAX_VALUE + 42L)
        val sameAsA = new ConfigLong(fakeOrigin(), Integer.MAX_VALUE + 42L)
        val b = new ConfigLong(fakeOrigin(), Integer.MAX_VALUE + 43L)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
    }

    @Test
    def configIntAndLongEquality() {
        val longValue = new ConfigLong(fakeOrigin(), 42L)
        val intValue = new ConfigLong(fakeOrigin(), 42)
        val longValueB = new ConfigLong(fakeOrigin(), 43L)
        val intValueB = new ConfigLong(fakeOrigin(), 43)

        checkEqualObjects(intValue, longValue)
        checkEqualObjects(intValueB, longValueB)
        checkNotEqualObjects(intValue, longValueB)
        checkNotEqualObjects(intValueB, longValue)
    }

    private def configMap(pairs: (String, Int)*): java.util.Map[String, AbstractConfigValue] = {
        val m = new java.util.HashMap[String, AbstractConfigValue]()
        for (p <- pairs) {
            m.put(p._1, new ConfigInt(fakeOrigin(), p._2))
        }
        m
    }

    @Test
    def configObjectEquality() {
        val aMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val sameAsAMap = configMap("a" -> 1, "b" -> 2, "c" -> 3)
        val bMap = configMap("a" -> 3, "b" -> 4, "c" -> 5)
        val a = new SimpleConfigObject(fakeOrigin(), aMap)
        val sameAsA = new SimpleConfigObject(fakeOrigin(), sameAsAMap)
        val b = new SimpleConfigObject(fakeOrigin(), bMap)

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
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
    def configSubstitutionEquality() {
        val a = subst("foo")
        val sameAsA = subst("foo")
        val b = subst("bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, b)
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

        assertTrue(m.containsKey("a"))
        assertFalse(m.containsKey("z"))

        assertTrue(m.containsValue(intValue(1)))
        assertFalse(m.containsValue(intValue(10)))

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
        val dmo = new ConfigDelayedMergeObject(fakeOrigin(), List[AbstractConfigValue](emptyObj, subst("a"), subst("b")).asJava)
        assertEquals(ConfigValueType.OBJECT, dmo.valueType())
        unresolved { dmo.unwrapped() }
        unresolved { dmo.containsKey(null) }
        unresolved { dmo.containsValue(null) }
        unresolved { dmo.entrySet() }
        unresolved { dmo.isEmpty() }
        unresolved { dmo.keySet() }
        unresolved { dmo.size() }
        unresolved { dmo.values() }
        unresolved { dmo.getInt("foo") }
    }
}
