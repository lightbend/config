/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigException

class PathTest extends TestUtils {

    @Test
    def pathEquality() {
        // note: foo.bar is a single key here
        val a = Path.newKey("foo.bar")
        // check that newKey worked
        assertEquals(path("foo.bar"), a);
        val sameAsA = Path.newKey("foo.bar")
        val differentKey = Path.newKey("hello")
        // here foo.bar is two elements
        val twoElements = Path.newPath("foo.bar")
        // check that newPath worked
        assertEquals(path("foo", "bar"), twoElements);
        val sameAsTwoElements = Path.newPath("foo.bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, differentKey)
        checkNotEqualObjects(a, twoElements)
        checkEqualObjects(twoElements, sameAsTwoElements)
    }

    @Test
    def pathToString() {
        assertEquals("Path(foo)", path("foo").toString())
        assertEquals("Path(foo.bar)", path("foo", "bar").toString())
        assertEquals("Path(foo.\"bar*\")", path("foo", "bar*").toString())
        assertEquals("Path(\"foo.bar\")", path("foo.bar").toString())
    }

    @Test
    def pathRender() {
        assertEquals("foo", path("foo").render())
        assertEquals("foo.bar", path("foo", "bar").render())
        assertEquals("foo.\"bar*\"", path("foo", "bar*").render())
        assertEquals("\"foo.bar\"", path("foo.bar").render())
        assertEquals("foo bar", path("foo bar").render())
        assertEquals("\"\".\"\"", path("", "").render())
    }

    @Test
    def pathFromPathList() {
        assertEquals(path("foo"), new Path(List(path("foo")).asJava))
        assertEquals(path("foo", "bar", "baz", "boo"), new Path(List(path("foo", "bar"),
            path("baz", "boo")).asJava))
    }

    @Test
    def pathPrepend() {
        assertEquals(path("foo", "bar"), path("bar").prepend(path("foo")))
        assertEquals(path("a", "b", "c", "d"), path("c", "d").prepend(path("a", "b")))
    }

    @Test
    def pathLength() {
        assertEquals(1, path("foo").length())
        assertEquals(2, path("foo", "bar").length())
    }

    @Test
    def pathParent() {
        assertNull(path("a").parent())
        assertEquals(path("a"), path("a", "b").parent())
        assertEquals(path("a", "b"), path("a", "b", "c").parent())
    }

    @Test
    def pathLast() {
        assertEquals("a", path("a").last())
        assertEquals("b", path("a", "b").last())
    }

    @Test
    def pathsAreInvalid() {
        // this test is just of the Path.newPath() wrapper, the extensive
        // test of different paths is over in ConfParserTest
        intercept[ConfigException.BadPath] {
            Path.newPath("")
        }

        intercept[ConfigException.BadPath] {
            Path.newPath("..")
        }
    }
}
