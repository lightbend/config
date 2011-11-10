package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._

class PathTest extends TestUtils {

    @Test
    def pathEquality() {
        // note: foo.bar is a single key here
        val a = Path.newKey("foo.bar")
        val sameAsA = Path.newKey("foo.bar")
        val differentKey = Path.newKey("hello")
        // here foo.bar is two elements
        val twoElements = Path.newPath("foo.bar")
        val sameAsTwoElements = Path.newPath("foo.bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, differentKey)
        checkNotEqualObjects(a, twoElements)
        checkEqualObjects(twoElements, sameAsTwoElements)
    }

    @Test
    def pathToString() {
        assertEquals("Path(foo)", Path.newPath("foo").toString())
        assertEquals("Path(foo.bar)", Path.newPath("foo.bar").toString())
        assertEquals("Path(foo.\"bar*\")", Path.newPath("foo.bar*").toString())
        assertEquals("Path(\"foo.bar\")", Path.newKey("foo.bar").toString())
    }

    @Test
    def pathRender() {
        assertEquals("foo", Path.newPath("foo").render())
        assertEquals("foo.bar", Path.newPath("foo.bar").render())
        assertEquals("foo.\"bar*\"", Path.newPath("foo.bar*").render())
        assertEquals("\"foo.bar\"", Path.newKey("foo.bar").render())
        assertEquals("foo bar", Path.newKey("foo bar").render())
    }
}
