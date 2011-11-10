package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._

class PathTest extends TestUtils {

    @Test
    def pathEquality() {
        // note: foo.bar is a single key here
        val a = PathBuilder.newKey("foo.bar")
        val sameAsA = PathBuilder.newKey("foo.bar")
        val differentKey = PathBuilder.newKey("hello")
        // here foo.bar is two elements
        val twoElements = PathBuilder.newPath("foo.bar")
        val sameAsTwoElements = PathBuilder.newPath("foo.bar")

        checkEqualObjects(a, a)
        checkEqualObjects(a, sameAsA)
        checkNotEqualObjects(a, differentKey)
        checkNotEqualObjects(a, twoElements)
        checkEqualObjects(twoElements, sameAsTwoElements)
    }

    @Test
    def pathToString() {
        assertEquals("Path(foo)", PathBuilder.newPath("foo").toString())
        assertEquals("Path(foo.bar)", PathBuilder.newPath("foo.bar").toString())
        assertEquals("Path(foo.\"bar*\")", PathBuilder.newPath("foo.bar*").toString())
        assertEquals("Path(\"foo.bar\")", PathBuilder.newKey("foo.bar").toString())
    }

    @Test
    def pathRender() {
        assertEquals("foo", PathBuilder.newPath("foo").render())
        assertEquals("foo.bar", PathBuilder.newPath("foo.bar").render())
        assertEquals("foo.\"bar*\"", PathBuilder.newPath("foo.bar*").render())
        assertEquals("\"foo.bar\"", PathBuilder.newKey("foo.bar").render())
        assertEquals("foo bar", PathBuilder.newKey("foo bar").render())
    }
}
