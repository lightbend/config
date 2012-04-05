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
        val e = intercept[ConfigException.Parse] {
            parseConfig(""" a : abc { x : y } """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Expecting") &&
                e.getMessage.contains("'{'"))
    }

    @Test
    def noArraysInStringConcat() {
        val e = intercept[ConfigException.Parse] {
            parseConfig(""" a : abc { x : y } """)
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("Expecting") &&
                e.getMessage.contains("'{'"))
    }

    @Test
    def noObjectsSubstitutedInStringConcat() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : abc ${x}, x : { y : z } """).resolve()
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("not a list or object") &&
                e.getMessage.contains("OBJECT"))
    }

    @Test
    def noArraysSubstitutedInStringConcat() {
        val e = intercept[ConfigException.WrongType] {
            parseConfig(""" a : abc ${x}, x : [1,2] """).resolve()
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("not a list or object") &&
                e.getMessage.contains("LIST"))
    }
}
