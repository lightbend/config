package com.typesafe.config.impl

import com.typesafe.config._
import org.junit.Assert._
import org.junit.Test

class MergeRenderOptionsTest {
    private def check(input: String, expected: String, json: Boolean, formatted: Boolean): Unit = {
        val options = ConfigRenderOptions.concise().setJson(json).setFormatted(formatted)
        val config = ConfigFactory.parseString(input)
        val resolved = config.resolve().root().unwrapped()
        var text = config.root().render(options)
        assertEquals(expected, text)
        // Unresolved JSON rendering contains substitutions; reparse it as HOCON.
        for (cycle <- 1 to 5) {
            val reparsed = ConfigFactory.parseString(text)
            assertEquals(resolved, reparsed.resolve().root().unwrapped())
            text = reparsed.root().render(options)
            assertEquals("cycle " + cycle, expected, text)
        }
    }

    @Test def hoconFormattedEntriesUseOrdinaryFieldSyntax(): Unit = {
        check("a=1\na=${a}\nsib=0", "a=1,\na=${a}\n\nsib=0\n", false, true)
    }

    @Test def hoconCompactEntriesUseOrdinaryFieldSyntax(): Unit = {
        check("a=1\na=${a}\nsib=0", "a=1,a=${a},sib=0", false, false)
    }

    @Test def keysRequiringQuotesRemainQuoted(): Unit = {
        for (key <- List("a b", "a.b", "a= b")) {
            val quoted = "\"" + key + "\""
            val input = quoted + "=1\n" + quoted + "=${x}\nx=2"
            check(input, quoted + "=1," + quoted + "=${x},x=2", false, false)
            check(input, quoted + "=1,\n" + quoted + "=${x}\n\nx=2\n", false, true)
        }
    }

    @Test def objectEntriesUseHoconObjectShorthand(): Unit = {
        // Final object produces ConfigDelayedMergeObject; final substitution produces ConfigDelayedMerge.
        check("a=${x}\na={b=2}\nx={c=3}", "a=${x},a{b=2},x{c=3}", false, false)
        check("a={b=2}\na=${x}\nx={c=3}", "a{b=2},a=${x},x{c=3}", false, false)
        check("a=${x}\na={b=2}\nx={c=3}",
            "a=${x},\na {\n    b=2\n}\n\nx {\n    c=3\n}\n", false, true)
    }

    @Test def jsonEntriesKeepQuotedKeysAndColons(): Unit = {
        check("a=1\na=${a}\nsib=0", "{\"a\":1,\"a\":${a},\"sib\":0}", true, false)
        check("a=1\na=${a}\nsib=0",
            "{\n        \"a\" : 1,\n    \"a\" : ${a}\n,\n    \"sib\" : 0\n}\n", true, true)
        check("a=${x}\na={b=2}\nx={c=3}",
            "{\"a\":${x},\"a\":{\"b\":2},\"x\":{\"c\":3}}", true, false)
    }

    @Test def renderingWithoutKeyKeepsValueSyntax(): Unit = {
        val value = ConfigFactory.parseString("a=1\na=${a}").root().get("a")
        assertEquals("1,${a}", value.render(ConfigRenderOptions.concise().setJson(false)))
        assertEquals("1,${a}", value.render(ConfigRenderOptions.concise()))
    }
}
