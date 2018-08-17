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

        checkValidationException(e, expecteds)
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

        checkValidationException(e, expecteds)
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

        checkValidationException(e, expecteds)
    }

    @Test
    def validationCatchesListOverriddenWithDifferentList() {
        val reference = parseConfig("""{ a : [true,false,false] }""")
        val conf = parseConfig("""{ a : [42,43] }""")
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference)
        }

        val expecteds = Seq(WrongElementType("a", 1, "boolean", "number"))

        checkValidationException(e, expecteds)
    }

    @Test
    def validationFailedSerializable(): Unit = {
        // Reusing a previous test case to generate an error
        val reference = parseConfig("""{ a : [{},{},{}] }""")
        val conf = parseConfig("""{ a : 42 }""")
        val e = intercept[ConfigException.ValidationFailed] {
            conf.checkValid(reference)
        }

        val expecteds = Seq(WrongType("a", 1, "list", "number"))

        val actual = checkSerializableNoMeaningfulEquals(e)
        checkValidationException(actual, expecteds)
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

        checkValidationException(e, expecteds)
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
