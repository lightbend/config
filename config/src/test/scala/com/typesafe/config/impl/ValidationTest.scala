/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigException
import scala.collection.JavaConverters._
import scala.io.Source

class ValidationTest extends TestUtils {

    sealed abstract class Problem(path: String, line: Int) {
        def check(p: ConfigException.ValidationProblem) {
            assertEquals("matching path", path, p.path())
            assertEquals("matching line", line, p.origin().lineNumber())
        }

        protected def assertMessage(p: ConfigException.ValidationProblem, re: String) {
            assertTrue("didn't get expected message for " + path + ": got '" + p.problem() + "'",
                p.problem().matches(re))
        }
    }

    case class Missing(path: String, line: Int, expected: String) extends Problem(path, line) {
        override def check(p: ConfigException.ValidationProblem) {
            super.check(p)
            val re = "No setting.*" + path + ".*expecting.*" + expected + ".*"
            assertMessage(p, re)
        }
    }

    case class WrongType(path: String, line: Int, expected: String, got: String) extends Problem(path, line) {
        override def check(p: ConfigException.ValidationProblem) {
            super.check(p)
            val re = "Wrong value type.*" + path + ".*expecting.*" + expected + ".*got.*" + got + ".*"
            assertMessage(p, re)
        }
    }

    case class WrongElementType(path: String, line: Int, expected: String, got: String) extends Problem(path, line) {
        override def check(p: ConfigException.ValidationProblem) {
            super.check(p)
            val re = "List at.*" + path + ".*wrong value type.*expecting.*" + expected + ".*got.*element of.*" + got + ".*"
            assertMessage(p, re)
        }
    }

    private def checkException(e: ConfigException.ValidationFailed, expecteds: Seq[Problem]) {
        val problems = e.problems().asScala.toIndexedSeq.sortBy(_.path).sortBy(_.origin.lineNumber)

        //for (problem <- problems)
        //    System.err.println(problem.origin().description() + ": " + problem.path() + ": " + problem.problem())

        for ((problem, expected) <- problems zip expecteds) {
            expected.check(problem)
        }
        assertEquals("found expected validation problems, got '" + problems + "' and expected '" + expecteds + "'",
            expecteds.size, problems.size)
    }

    @Test
    def validation() {
        val reference = ConfigFactory.parseFile(resourceFile("validate-reference.conf"), ConfigParseOptions.defaults())
        val conf = ConfigFactory.parseFile(resourceFile("validate-invalid.conf"), ConfigParseOptions.defaults())
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference)
        }

        val expecteds = Seq(
            Missing("willBeMissing", 1, "number"),
            WrongType("int3", 7, "number", "object"),
            WrongType("float2", 9, "number", "boolean"),
            WrongType("float3", 10, "number", "list"),
            WrongType("bool1", 11, "boolean", "number"),
            WrongType("bool3", 13, "boolean", "object"),
            Missing("object1.a", 17, "string"),
            WrongType("object2", 18, "object", "list"),
            WrongType("object3", 19, "object", "number"),
            WrongElementType("array3", 22, "boolean", "object"),
            WrongElementType("array4", 23, "object", "number"),
            WrongType("array5", 24, "list", "number"),
            WrongType("a.b.c.d.e.f.g", 28, "boolean", "number"),
            Missing("a.b.c.d.e.f.j", 28, "boolean"),
            WrongType("a.b.c.d.e.f.i", 30, "boolean", "list"))

        checkException(e, expecteds)
    }

    @Test
    def validationWithRoot() {
        val objectWithB = parseObject("""{ b : c }""")
        val reference = ConfigFactory.parseFile(resourceFile("validate-reference.conf"),
            ConfigParseOptions.defaults()).withFallback(objectWithB)
        val conf = ConfigFactory.parseFile(resourceFile("validate-invalid.conf"), ConfigParseOptions.defaults())
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference, "a", "b")
        }

        val expecteds = Seq(
            Missing("b", 1, "string"),
            WrongType("a.b.c.d.e.f.g", 28, "boolean", "number"),
            Missing("a.b.c.d.e.f.j", 28, "boolean"),
            WrongType("a.b.c.d.e.f.i", 30, "boolean", "list"))

        checkException(e, expecteds)
    }

    @Test
    def validationCatchesUnresolved() {
        val reference = parseConfig("""{ a : 2 }""")
        val conf = parseConfig("""{ b : ${c}, c : 42 }""")
        val e = intercept[ConfigException.NotResolved] {
            conf.checkValid(reference)
        }
        assertTrue("expected different message, got: " + e.getMessage,
            e.getMessage.contains("resolve"))
    }

    @Test
    def validationCatchesListOverriddenWithNumber() {
        val reference = parseConfig("""{ a : [{},{},{}] }""")
        val conf = parseConfig("""{ a : 42 }""")
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference)
        }

        val expecteds = Seq(WrongType("a", 1, "list", "number"))

        checkException(e, expecteds)
    }

    @Test
    def validationCatchesListOverriddenWithDifferentList() {
        val reference = parseConfig("""{ a : [true,false,false] }""")
        val conf = parseConfig("""{ a : [42,43] }""")
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference)
        }

        val expecteds = Seq(WrongElementType("a", 1, "boolean", "number"))

        checkException(e, expecteds)
    }

    @Test
    def validationAllowsListOverriddenWithSameTypeList() {
        val reference = parseConfig("""{ a : [1,2,3] }""")
        val conf = parseConfig("""{ a : [4,5] }""")
        conf.checkValid(reference)
    }

    @Test
    def validationCatchesListOverriddenWithNoIndexesObject() {
        val reference = parseConfig("""{ a : [1,2,3] }""")
        val conf = parseConfig("""{ a : { notANumber: foo } }""")
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference)
        }

        val expecteds = Seq(WrongType("a", 1, "list", "object"))

        checkException(e, expecteds)
    }

    @Test
    def validationAllowsListOverriddenWithIndexedObject() {
        val reference = parseConfig("""{ a : [a,b,c] }""")
        val conf = parseConfig("""{ a : { "0" : x, "1" : y } }""")
        conf.checkValid(reference)
        assertEquals("got the sequence from overriding list with indexed object",
            Seq("x", "y"), conf.getStringList("a").asScala)
    }
}
