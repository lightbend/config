/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

class ConcatenationTest extends TestUtils {

    @Test
    def noSubstitutionsStringConcat() {
        val conf = parseConfig(""" a :  true "xyz" 123 foo  """).resolve()
        assertEquals("true xyz 123 foo", conf.getString("a"))
    }

    @Test
    def trivialStringConcat() {
        val conf = parseConfig(""" a : ${x}foo, x = 1 """).resolve()
        assertEquals("1foo", conf.getString("a"))
    }

    @Test
    def twoSubstitutionsStringConcat() {
        val conf = parseConfig(""" a : ${x}foo${x}, x = 1 """).resolve()
        assertEquals("1foo1", conf.getString("a"))
    }

    @Test
    def stringConcatCannotSpanLines() {
        val e = intercept[ConfigException.Parse] {
            parseConfig(""" a : ${x}
                foo, x = 1 """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("not be followed") &&
                e.getMessage.contains("','"))
    }

    @Test
    def noObjectsInStringConcat() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : abc { x : y } """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Cannot concatenate") &&
                e.getMessage.contains("abc") &&
                e.getMessage.contains("""{"x" : "y"}"""))
    }

    @Test
    def noObjectConcatWithNull() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : null { x : y } """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Cannot concatenate") &&
                e.getMessage.contains("null") &&
                e.getMessage.contains("""{"x" : "y"}"""))
    }

    @Test
    def noArraysInStringConcat() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : abc [1, 2] """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Cannot concatenate") &&
                e.getMessage.contains("abc") &&
                e.getMessage.contains("[1,2]"))
    }

    @Test
    def noObjectsSubstitutedInStringConcat() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : abc ${x}, x : { y : z } """).resolve()
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Cannot concatenate") &&
                e.getMessage.contains("abc"))
    }

    @Test
    def noArraysSubstitutedInStringConcat() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : abc ${x}, x : [1,2] """).resolve()
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Cannot concatenate") &&
                e.getMessage.contains("abc"))
    }

    @Test
    def noSubstitutionsListConcat() {
        val conf = parseConfig(""" a :  [1,2] [3,4]  """)
        assertEquals(Seq(1, 2, 3, 4), conf.getList("a").unwrapped().asScala)
    }

    @Test
    def listConcatWithSubstitutions() {
        val conf = parseConfig(""" a :  ${x} [3,4] ${y}, x : [1,2], y : [5,6]  """).resolve()
        assertEquals(Seq(1, 2, 3, 4, 5, 6), conf.getList("a").unwrapped().asScala)
    }

    @Test
    def listConcatSelfReferential() {
        val conf = parseConfig(""" a : [1, 2], a : ${a} [3,4], a : ${a} [5,6]  """).resolve()
        assertEquals(Seq(1, 2, 3, 4, 5, 6), conf.getList("a").unwrapped().asScala)
    }

    @Test
    def noSubstitutionsListConcatCannotSpanLines() {
        val e = intercept[ConfigException.Parse] {
            parseConfig(""" a :  [1,2]
                [3,4]  """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("expecting") &&
                e.getMessage.contains("'['"))
    }

    @Test
    def listConcatCanSpanLinesInsideBrackets() {
        val conf = parseConfig(""" a :  [1,2
               ] [3,4]  """)
        assertEquals(Seq(1, 2, 3, 4), conf.getList("a").unwrapped().asScala)
    }

    @Test
    def noSubstitutionsObjectConcat() {
        val conf = parseConfig(""" a : { b : c } { x : y }  """)
        assertEquals(Map("b" -> "c", "x" -> "y"), conf.getObject("a").unwrapped().asScala)
    }

    @Test
    def objectConcatMergeOrder() {
        val conf = parseConfig(""" a : { b : 1 } { b : 2 } { b : 3 } { b : 4 } """)
        assertEquals(4, conf.getInt("a.b"))
    }

    @Test
    def objectConcatWithSubstitutions() {
        val conf = parseConfig(""" a : ${x} { b : 1 } ${y}, x : { a : 0 }, y : { c : 2 } """).resolve()
        assertEquals(Map("a" -> 0, "b" -> 1, "c" -> 2), conf.getObject("a").unwrapped().asScala)
    }

    @Test
    def objectConcatSelfReferential() {
        val conf = parseConfig(""" a : { a : 0 }, a : ${a} { b : 1 }, a : ${a} { c : 2 } """).resolve()
        assertEquals(Map("a" -> 0, "b" -> 1, "c" -> 2), conf.getObject("a").unwrapped().asScala)
    }

    @Test
    def objectConcatSelfReferentialOverride() {
        val conf = parseConfig(""" a : { b : 3 }, a : { b : 2 } ${a} """).resolve()
        assertEquals(Map("b" -> 3), conf.getObject("a").unwrapped().asScala)
    }

    @Test
    def noSubstitutionsObjectConcatCannotSpanLines() {
        val e = intercept[ConfigException.Parse] {
            parseConfig(""" a :  { b : c }
                    { x : y }""")
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("expecting") &&
                e.getMessage.contains("'{'"))
    }

    @Test
    def objectConcatCanSpanLinesInsideBraces() {
        val conf = parseConfig(""" a :  { b : c
    } { x : y }  """)
        assertEquals(Map("b" -> "c", "x" -> "y"), conf.getObject("a").unwrapped().asScala)
    }

    @Test
    def stringConcatInsideArrayValue() {
        val conf = parseConfig(""" a : [ foo bar 10 ] """)
        assertEquals(Seq("foo bar 10"), conf.getStringList("a").asScala)
    }

    @Test
    def stringNonConcatInsideArrayValue() {
        val conf = parseConfig(""" a : [ foo
                bar
                10 ] """)
        assertEquals(Seq("foo", "bar", "10"), conf.getStringList("a").asScala)
    }

    @Test
    def objectConcatInsideArrayValue() {
        val conf = parseConfig(""" a : [ { b : c } { x : y } ] """)
        assertEquals(Seq(Map("b" -> "c", "x" -> "y")), conf.getObjectList("a").asScala.map(_.unwrapped().asScala))
    }

    @Test
    def objectNonConcatInsideArrayValue() {
        val conf = parseConfig(""" a : [ { b : c }
                { x : y } ] """)
        assertEquals(Seq(Map("b" -> "c"), Map("x" -> "y")), conf.getObjectList("a").asScala.map(_.unwrapped().asScala))
    }

    @Test
    def listConcatInsideArrayValue() {
        val conf = parseConfig(""" a : [ [1, 2] [3, 4] ] """)
        assertEquals(List(List(1, 2, 3, 4)),
            // well that's a little silly
            conf.getList("a").unwrapped().asScala.toList.map(_.asInstanceOf[java.util.List[_]].asScala.toList))
    }

    @Test
    def listNonConcatInsideArrayValue() {
        val conf = parseConfig(""" a : [ [1, 2]
                [3, 4] ] """)
        assertEquals(List(List(1, 2), List(3, 4)),
            // well that's a little silly
            conf.getList("a").unwrapped().asScala.toList.map(_.asInstanceOf[java.util.List[_]].asScala.toList))
    }

    @Test
    def stringConcatsAreKeys() {
        val conf = parseConfig(""" 123 foo : "value" """)
        assertEquals("value", conf.getString("123 foo"))
    }

    @Test
    def objectsAreNotKeys() {
        val e = intercept[ConfigException.Parse] {
            parseConfig("""{ { a : 1 } : "value" }""")
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("expecting a close") && e.getMessage.contains("'{'"))
    }

    @Test
    def arraysAreNotKeys() {
        val e = intercept[ConfigException.Parse] {
            parseConfig("""{ [ "a" ] : "value" }""")
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("expecting a close") && e.getMessage.contains("'['"))
    }
}
