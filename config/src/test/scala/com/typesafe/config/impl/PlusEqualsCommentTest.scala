package com.typesafe.config.impl

import com.typesafe.config._
import org.junit.Assert._
import org.junit.Test

class PlusEqualsCommentTest {
    private val options = ConfigRenderOptions.defaults()
        .setJson(false).setComments(true).setOriginComments(false)

    private def checkStable(config: Config, expected: String): Unit = {
        assertEquals(expected, config.root().render(options))
        var text = expected
        for (cycle <- 1 to 5) {
            val reparsed = ConfigFactory.parseString(text)
            assertEquals(config.resolve().root().unwrapped(), reparsed.resolve().root().unwrapped())
            text = reparsed.root().render(options)
            assertEquals("render in cycle " + cycle, expected, text)
        }
    }

    @Test def loneAppendCommentIsRenderedOnce(): Unit = {
        checkStable(ConfigFactory.parseString("# two\na += 2"),
            "# two\na=${?a}[\n    2\n]\n")
    }

    @Test def objectAppendCommentIsRenderedOnce(): Unit = {
        checkStable(ConfigFactory.parseString("# two\na += { x = 1 }"),
            "# two\na=${?a}[\n    {\n        x=1\n    }\n]\n")
    }

    @Test def appendAfterDefinitionCommentIsRenderedOnceAfterResolve(): Unit = {
        for (initial <- List("[]", "[1]")) {
            val resolved = ConfigFactory.parseString("a=" + initial + "\n# two\na += 2").resolve()
            val first = if (initial == "[]") "" else "    1,\n"
            checkStable(resolved, "# two\na=[\n" + first + "    2\n]\n")
        }
    }

    @Test def fieldCommentSurvivesResolve(): Unit = {
        for (prefix <- List("", "a=[1]\n")) {
            val resolved = ConfigFactory.parseString(prefix + "# two\na += 2").resolve()
            assertEquals(java.util.Arrays.asList(" two"), resolved.getValue("a").origin().comments())
            assertTrue(resolved.getList("a").get(resolved.getList("a").size() - 1).origin().comments().isEmpty())
        }
    }

    @Test def appendedElementKeepsSourceLocation(): Unit = {
        val parseOptions = ConfigParseOptions.defaults().setOriginDescription("append source")
        val resolved = ConfigFactory.parseString("a=[]\n# two\na += 2", parseOptions).resolve()
        val element = resolved.getList("a").get(0)
        assertEquals("append source", element.origin().description().split(":")(0))
        assertEquals(3, element.origin().lineNumber())
        assertEquals(ConfigValueType.NUMBER, element.valueType())
        assertEquals(2, element.unwrapped())
    }

    @Test def objectChildCommentIsPreserved(): Unit = {
        val resolved = ConfigFactory.parseString("# two\na += {\n # child\n x=1\n}").resolve()
        val element = resolved.getObjectList("a").get(0)
        assertTrue(element.origin().comments().isEmpty())
        assertEquals(java.util.Arrays.asList(" child"), element.get("x").origin().comments())
        assertEquals(java.util.Arrays.asList(" two"), resolved.getValue("a").origin().comments())
    }
}
