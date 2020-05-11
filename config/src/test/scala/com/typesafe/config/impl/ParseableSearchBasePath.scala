package com.typesafe.config.impl

import java.nio.file.Paths;

import org.junit.Assert.{ assertEquals, assertNull }
import org.junit.Test

class ParseableSearchBasePath extends TestUtils {

    @Test
    def testGlobSearchBasePath(): Unit = {
        assertEquals(Paths.get("/a/b/c"), Parseable.globSearchBasePath("/a/b/c/d.{conf,json,properties}"))
        assertEquals(Paths.get("/a/b"), Parseable.globSearchBasePath("/a/b/c?/d.{conf,json,properties}"))
        assertEquals(Paths.get("/a/b"), Parseable.globSearchBasePath("/a/b/c[fx]/d.{conf,json,properties}"))
        assertEquals(Paths.get("/a/b/c"), Parseable.globSearchBasePath("/a/b/c/*.conf"))
        assertEquals(Paths.get("/"), Parseable.globSearchBasePath("/*.conf"))
        assertEquals(Paths.get("/a/b/c[$]{^}"), Parseable.globSearchBasePath("/a/b/c\\[\\$]\\{\\^}/*.conf"))

        assertNull(Parseable.globSearchBasePath("*.conf"))
        assertNull(Parseable.globSearchBasePath("abc"))

        assertEquals(Paths.get("abc"), Parseable.globSearchBasePath("abc/*.conf"))
        assertEquals(Paths.get("a/b"), Parseable.globSearchBasePath("a/b/c?/d.conf"))
        assertEquals(Paths.get("a"), Parseable.globSearchBasePath("a/b[cd]/*.conf"))
        assertEquals(Paths.get("a"), Parseable.globSearchBasePath("a/b.conf"))
    }
}
