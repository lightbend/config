package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import java.util.Properties
import com.typesafe.config.Config
import com.typesafe.config.ConfigParseOptions

class PropertiesTest extends TestUtils {
    @Test
    def pathSplitting() {
        def last(s: String) = PropertiesParser.lastElement(s)
        def exceptLast(s: String) = PropertiesParser.exceptLastElement(s)

        assertEquals("a", last("a"))
        assertNull(exceptLast("a"))

        assertEquals("b", last("a.b"))
        assertEquals("a", exceptLast("a.b"))

        assertEquals("c", last("a.b.c"))
        assertEquals("a.b", exceptLast("a.b.c"))

        assertEquals("", last(""))
        assertNull(null, exceptLast(""))

        assertEquals("", last("."))
        assertEquals("", exceptLast("."))

        assertEquals("", last(".."))
        assertEquals(".", exceptLast(".."))

        assertEquals("", last("..."))
        assertEquals("..", exceptLast("..."))
    }

    @Test
    def pathObjectCreating() {
        def p(key: String) = PropertiesParser.pathFromPropertyKey(key)

        assertEquals(path("a"), p("a"))
        assertEquals(path("a", "b"), p("a.b"))
        assertEquals(path(""), p(""))
    }

    @Test
    def funkyPathsInProperties() {
        def testPath(propsPath: String, confPath: String) {
            val props = new Properties()

            props.setProperty(propsPath, propsPath)

            val conf = Config.parse(props, ConfigParseOptions.defaults())

            assertEquals(propsPath, conf.getString(confPath))
        }

        // the easy ones
        testPath("x", "x")
        testPath("y.z", "y.z")
        testPath("q.r.s", "q.r.s")

        // weird empty path element stuff
        testPath("", "\"\"")
        testPath(".", "\"\".\"\"")
        testPath("..", "\"\".\"\".\"\"")
        testPath("a.", "a.\"\"")
        testPath(".b", "\"\".b")

        // quotes in .properties
        testPath("\"", "\"\\\"\"")
    }

    @Test
    def objectsWinOverStrings() {
        val props = new Properties()

        props.setProperty("a.b", "foo")
        props.setProperty("a", "bar")

        props.setProperty("x", "baz")
        props.setProperty("x.y", "bar")
        props.setProperty("x.y.z", "foo")

        val conf = Config.parse(props, ConfigParseOptions.defaults())

        assertEquals(2, conf.size())
        assertEquals("foo", conf.getString("a.b"))
        assertEquals("foo", conf.getString("x.y.z"))
    }
}
