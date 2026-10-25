package com.typesafe.config.impl

import com.typesafe.config._
import org.junit.Assert._
import org.junit.Test

class DelayedMergeRenderingTest {
    private def options(formatted: Boolean) = ConfigRenderOptions.defaults()
        .setJson(false).setComments(true).setOriginComments(false).setFormatted(formatted)

    private def stable(config: Config, expected: String, formatted: Boolean): Unit = {
        val opts = options(formatted)
        val resolved = config.resolve().root().unwrapped()
        var text = config.root().render(opts)
        assertEquals(expected, text)
        for (cycle <- 1 to 5) {
            val reparsed = ConfigFactory.parseString(text)
            assertEquals("resolved values in cycle " + cycle, resolved, reparsed.resolve().root().unwrapped())
            val next = reparsed.root().render(opts)
            assertEquals("render in cycle " + cycle, expected, next)
            text = next
        }
    }

    @Test def repeatedEntriesAreStable(): Unit = {
        stable(ConfigFactory.parseString("a = [1]\na += 2"),
            "\"a\" : [\n    1\n],\n\"a\" : ${?a}[\n    2\n]\n\n", true)
        stable(ConfigFactory.parseString("a = [1]\na += 2"),
            "\"a\":[1],\"a\":${?a}[2]", false)
    }

    @Test def nestedCommentsAreStable(): Unit = {
        val input = "a {\n # merge comment\n b = [1]\n b += 2\n}"
        stable(ConfigFactory.parseString(input),
            "a {\n    # merge comment\n    \"b\" : [\n        1\n    ],\n    \"b\" : ${?a.b}[\n        2\n    ]\n\n}\n", true)
        stable(ConfigFactory.parseString(input),
            "a{# merge comment\n\"b\":[1],\"b\":${?a.b}[2]}", false)
    }

    @Test def entryCommentsArePrintedOnce(): Unit = {
        val config = ConfigFactory.parseString("# one\na=1\n#  two\na=${a}")
        assertTrue(config.root().get("a").isInstanceOf[ConfigDelayedMerge])
        stable(config, "# one\n\"a\" : 1,\n#  two\n\"a\" : ${a}\n\n", true)
        stable(config, "# one\n\"a\":1,\n#  two\n\"a\":${a}", false)
    }

    @Test def objectMergeCommentsAreStable(): Unit = {
        val config = ConfigFactory.parseString("# first\na=${x}\n# second\na={b=2}\nx={c=3}")
        assertTrue(config.root().get("a").isInstanceOf[ConfigDelayedMergeObject])
        stable(config, "# first\n\"a\" : ${x},\n# second\n\"a\" : {\n    b=2\n}\n\nx {\n    c=3\n}\n", true)
        stable(config, "# first\n\"a\":${x},\n# second\n\"a\":{b=2},x{c=3}", false)
    }

    @Test def wrapperCommentsSurviveForBothMergeTypes(): Unit = {
        val cases = List(
            ("# entry\na=1\na=${a}",
                "# wrapper\n# entry\n\"a\" : 1,\n\"a\" : ${a}\n\n",
                "# wrapper\n# entry\n\"a\":1,\"a\":${a}"),
            ("# entry\na=${x}\na={b=2}\nx={c=3}",
                "# wrapper\n# entry\n\"a\" : ${x},\n\"a\" : {\n    b=2\n}\n\nx {\n    c=3\n}\n",
                "# wrapper\n# entry\n\"a\":${x},\"a\":{b=2},x{c=3}")
        )
        for ((input, formatted, compact) <- cases) {
            val root = ConfigFactory.parseString(input).root()
            val merge = root.get("a")
            // Include an entry comment in the wrapper to exercise deduplication.
            val tagged = merge.withOrigin(merge.origin().withComments(
                java.util.Arrays.asList("wrapper", " entry")))
            val config = root.withValue("a", tagged).toConfig()
            stable(config, formatted, true)
            stable(config, compact, false)
        }
    }

    @Test def mergeInArrayObjectIsStable(): Unit = {
        val config = ConfigFactory.parseString("l=[{a=1\na=${x}}]\nx=2")
        stable(config, "l=[\n    {\n        \"a\" : 1,\n        \"a\" : ${x}\n\n    }\n]\nx=2\n", true)
        stable(config, "l=[{\"a\":1,\"a\":${x}}],x=2", false)
    }

    @Test def mergeWithoutKeyRetainsDiagnosticBanner(): Unit = {
        val merge = ConfigFactory.parseString("a=1\na=${a}").root().get("a")
        val header = "# unresolved merge of 2 values follows (\n" +
            "# this unresolved merge will not be parseable because it's at the root of the object\n" +
            "# the HOCON format has no way to list multiple root objects in a single file\n"
        val first = "#     unmerged value 0 from String: 1\n1,"
        val second = "#     unmerged value 1 from String: 2\n${a}"
        val end = "# ) end of unresolved merge\n"
        assertEquals(header + first + "\n" + second + "\n" + end, merge.render(options(true)))
        assertEquals(header + first + second + end, merge.render(options(false)))
        assertEquals("1,\n${a}\n", merge.render(options(true).setComments(false)))
        assertEquals("1,${a}", merge.render(options(false).setComments(false)))
    }

    @Test def nestedEntryCommentsAlignWithSiblings(): Unit = {
        val config = ConfigFactory.parseString("outer {\n sib=0\n # c1\n a=1\n # c2\n a=${outer.a}\n}")
        stable(config, "outer {\n    # c1\n    \"a\" : 1,\n    # c2\n    \"a\" : ${outer.a}\n\n    sib=0\n}\n", true)
        stable(config, "outer{# c1\n\"a\":1,\n# c2\n\"a\":${outer.a},sib=0}", false)
    }

    @Test def objectMergeInArrayIsStable(): Unit = {
        val config = ConfigFactory.parseString("l=[{a=${x}\na={b=2}}]\nx={c=3}")
        assertTrue(config.getObjectList("l").get(0).get("a").isInstanceOf[ConfigDelayedMergeObject])
        stable(config, "l=[\n    {\n        \"a\" : ${x},\n        \"a\" : {\n            b=2\n        }\n\n    }\n]\nx {\n    c=3\n}\n", true)
        stable(config, "l=[{\"a\":${x},\"a\":{b=2}}],x{c=3}", false)
    }
}
