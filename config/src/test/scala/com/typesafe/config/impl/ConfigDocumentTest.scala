package com.typesafe.config.impl

import java.io.{ BufferedReader, FileReader }
import java.nio.charset.StandardCharsets
import java.nio.file.{ Paths, Files }

import com.typesafe.config._
import com.typesafe.config.parser._
import org.junit.Assert._
import org.junit.Test

class ConfigDocumentTest extends TestUtils {
    private def configDocumentReplaceJsonTest(origText: String, finalText: String, newValue: String, replacePath: String) {
        val configDocument = ConfigDocumentFactory.parseString(origText, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON))
        assertEquals(origText, configDocument.render())
        val newDocument = configDocument.setValue(replacePath, newValue)
        assertTrue(newDocument.isInstanceOf[SimpleConfigDocument])
        assertEquals(finalText, newDocument.render())
    }

    private def configDocumentReplaceConfTest(origText: String, finalText: String, newValue: String, replacePath: String) {
        val configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals(origText, configDocument.render())
        val newDocument = configDocument.setValue(replacePath, newValue)
        assertTrue(newDocument.isInstanceOf[SimpleConfigDocument])
        assertEquals(finalText, newDocument.render())
    }

    @Test
    def configDocumentReplace {
        // Can handle parsing/replacement with a very simple map
        configDocumentReplaceConfTest("""{"a":1}""", """{"a":2}""", "2", "a")
        configDocumentReplaceJsonTest("""{"a":1}""", """{"a":2}""", "2", "a")

        // Can handle parsing/replacement with a map without surrounding braces
        configDocumentReplaceConfTest("a: b\nc = d", "a: b\nc = 12", "12", "c")

        // Can handle parsing/replacement with a complicated map
        var origText =
            """{
              "a":123,
              "b": 123.456,
              "c": true,
              "d": false,
              "e": null,
              "f": "a string",
              "g": [1,2,3,4,5],
              "h": {
                "a": 123,
                "b": {
                  "a": 12
                },
                "c": [1, 2, 3, {"a": "b"}, [1,2,3]]
              }
             }"""
        var finalText =
            """{
              "a":123,
              "b": 123.456,
              "c": true,
              "d": false,
              "e": null,
              "f": "a string",
              "g": [1,2,3,4,5],
              "h": {
                "a": 123,
                "b": {
                  "a": "i am now a string"
                },
                "c": [1, 2, 3, {"a": "b"}, [1,2,3]]
              }
             }"""
        configDocumentReplaceConfTest(origText, finalText, """"i am now a string"""", "h.b.a")
        configDocumentReplaceJsonTest(origText, finalText, """"i am now a string"""", "h.b.a")

        // Can handle replacing values with maps
        finalText =
            """{
              "a":123,
              "b": 123.456,
              "c": true,
              "d": false,
              "e": null,
              "f": "a string",
              "g": [1,2,3,4,5],
              "h": {
                "a": 123,
                "b": {
                  "a": {"a":"b", "c":"d"}
                },
                "c": [1, 2, 3, {"a": "b"}, [1,2,3]]
              }
             }"""
        configDocumentReplaceConfTest(origText, finalText, """{"a":"b", "c":"d"}""", "h.b.a")
        configDocumentReplaceJsonTest(origText, finalText, """{"a":"b", "c":"d"}""", "h.b.a")

        // Can handle replacing values with arrays
        finalText =
            """{
              "a":123,
              "b": 123.456,
              "c": true,
              "d": false,
              "e": null,
              "f": "a string",
              "g": [1,2,3,4,5],
              "h": {
                "a": 123,
                "b": {
                  "a": [1,2,3,4,5]
                },
                "c": [1, 2, 3, {"a": "b"}, [1,2,3]]
              }
             }"""
        configDocumentReplaceConfTest(origText, finalText, "[1,2,3,4,5]", "h.b.a")
        configDocumentReplaceJsonTest(origText, finalText, "[1,2,3,4,5]", "h.b.a")

        finalText =
            """{
              "a":123,
              "b": 123.456,
              "c": true,
              "d": false,
              "e": null,
              "f": "a string",
              "g": [1,2,3,4,5],
              "h": {
                "a": 123,
                "b": {
                  "a": this is a concatenation 123 456 {a:b} [1,2,3] {a: this is another 123 concatenation null true}
                },
                "c": [1, 2, 3, {"a": "b"}, [1,2,3]]
              }
             }"""
        configDocumentReplaceConfTest(origText, finalText,
            "this is a concatenation 123 456 {a:b} [1,2,3] {a: this is another 123 concatenation null true}", "h.b.a")
    }

    @Test
    def configDocumentMultiElementDuplicatesRemoved {
        var origText = "{a: b, a.b.c: d, a: e}"
        var configDoc = ConfigDocumentFactory.parseString(origText)
        assertEquals("{a: 2}", configDoc.setValue("a", "2").render())

        origText = "{a: b, a: e, a.b.c: d}"
        configDoc = ConfigDocumentFactory.parseString(origText)
        assertEquals("{a: 2, }", configDoc.setValue("a", "2").render())

        origText = "{a.b.c: d}"
        configDoc = ConfigDocumentFactory.parseString(origText)
        assertEquals("{ a : 2}", configDoc.setValue("a", "2").render())
    }

    @Test
    def configDocumentSetNewValueBraceRoot {
        val origText = "{\n\t\"a\":\"b\",\n\t\"c\":\"d\"\n}"
        val finalTextConf = "{\n\t\"a\":\"b\",\n\t\"c\":\"d\"\n\t\"e\" : \"f\"\n}"
        val finalTextJson = "{\n\t\"a\":\"b\",\n\t\"c\":\"d\",\n\t\"e\" : \"f\"\n}"
        configDocumentReplaceConfTest(origText, finalTextConf, "\"f\"", "\"e\"")
        configDocumentReplaceJsonTest(origText, finalTextJson, "\"f\"", "\"e\"")
    }

    @Test
    def configDocumentSetNewValueNoBraces {
        val origText = "\"a\":\"b\",\n\"c\":\"d\"\n"
        val finalText = "\"a\":\"b\",\n\"c\":\"d\"\n\"e\" : \"f\"\n"
        configDocumentReplaceConfTest(origText, finalText, "\"f\"", "\"e\"")
    }

    @Test
    def configDocumentSetNewValueMultiLevelConf {
        val origText = "a:b\nc:d"
        val finalText = "a:b\nc:d\ne : { f : { g : 12 } }"
        configDocumentReplaceConfTest(origText, finalText, "12", "e.f.g")
    }

    @Test
    def configDocumentSetNewValueMultiLevelJson {
        val origText = "{\"a\":\"b\",\n\"c\":\"d\"}"
        val finalText = "{\"a\":\"b\",\n\"c\":\"d\",\n\"e\" : { \"f\" : { \"g\" : 12 } }}"
        configDocumentReplaceJsonTest(origText, finalText, "12", "e.f.g")
    }

    @Test
    def configDocumentSetNewConfigValue {
        val origText = "{\"a\": \"b\"}"
        val finalText = "{\"a\": 12}"
        val configDocHOCON = ConfigDocumentFactory.parseString(origText)
        val configDocJSON = ConfigDocumentFactory.parseString(origText, ConfigParseOptions.defaults.setSyntax(ConfigSyntax.JSON))
        val newValue = ConfigValueFactory.fromAnyRef(12)
        assertEquals(origText, configDocHOCON.render())
        assertEquals(origText, configDocJSON.render())
        assertEquals(finalText, configDocHOCON.setValue("a", newValue).render())
        assertEquals(finalText, configDocJSON.setValue("a", newValue).render())
    }

    @Test
    def configDocumentHasValue {
        val origText = "{a: b, a.b.c.d: e, c: {a: {b: c}}}"
        val configDoc = ConfigDocumentFactory.parseString(origText)

        assertTrue(configDoc.hasValue("a"))
        assertTrue(configDoc.hasValue("a.b.c"))
        assertTrue(configDoc.hasValue("c.a.b"))
        assertFalse(configDoc.hasValue("c.a.b.c"))
        assertFalse(configDoc.hasValue("a.b.c.d.e"))
        assertFalse(configDoc.hasValue("this.does.not.exist"))
    }

    @Test
    def configDocumentRemoveValue {
        val origText = "{a: b, a.b.c.d: e, c: {a: {b: c}}}"
        val configDoc = ConfigDocumentFactory.parseString(origText)

        assertEquals("{c: {a: {b: c}}}", configDoc.removeValue("a").render())
        assertEquals("{a: b, a.b.c.d: e, }", configDoc.removeValue("c").render())
        assertEquals(configDoc, configDoc.removeValue("this.does.not.exist"))
    }

    @Test
    def configDocumentRemoveValueJSON {
        val origText = """{"a": "b", "c": "d"}"""
        val configDoc = ConfigDocumentFactory.parseString(origText, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON))

        // Ensure that removing a value in JSON does not leave us with a trailing comma
        assertEquals("""{"a": "b" }""", configDoc.removeValue("c").render())
    }

    @Test
    def configDocumentRemoveMultiple {
        val origText = "a { b: 42 }, a.b = 43, a { b: { c: 44 } }"
        val configDoc = ConfigDocumentFactory.parseString(origText)
        val removed = configDoc.removeValue("a.b")
        assertEquals("a { }, a { }", removed.render())
    }

    @Test
    def configDocumentRemoveOverridden {
        val origText = "a { b: 42 }, a.b = 43, a { b: { c: 44 } }, a : 57 "
        val configDoc = ConfigDocumentFactory.parseString(origText)
        val removed = configDoc.removeValue("a.b")
        assertEquals("a { }, a { }, a : 57 ", removed.render())
    }

    @Test
    def configDocumentRemoveNested {
        val origText = "a { b: 42 }, a.b = 43, a { b: { c: 44 } }"
        val configDoc = ConfigDocumentFactory.parseString(origText)
        val removed = configDoc.removeValue("a.b.c")
        assertEquals("a { b: 42 }, a.b = 43, a { b: { } }", removed.render())
    }

    @Test
    def configDocumentArrayFailures {
        // Attempting a replace on a ConfigDocument parsed from an array throws an error
        val origText = "[1, 2, 3, 4, 5]"
        val document = ConfigDocumentFactory.parseString(origText)

        val e1 = intercept[ConfigException] { document.setValue("a", "1") }
        assertTrue(e1.getMessage.contains("ConfigDocument had an array at the root level"))

        val e2 = intercept[ConfigException] { document.hasValue("a") }
        assertTrue(e2.getMessage.contains("ConfigDocument had an array at the root level"))

        val e3 = intercept[ConfigException] { document.removeValue("a") }
        assertTrue(e3.getMessage.contains("ConfigDocument had an array at the root level"))
    }

    @Test
    def configDocumentJSONReplaceFailure {
        // Attempting a replace on a ConfigDocument parsed from JSON with a value using HOCON syntax
        // will fail
        val origText = "{\"foo\": \"bar\", \"baz\": \"qux\"}"
        val document = ConfigDocumentFactory.parseString(origText, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON))
        var exceptionThrown = false
        try {
            document.setValue("foo", "unquoted")
        } catch {
            case e: Exception =>
                exceptionThrown = true
                assertTrue(e.isInstanceOf[ConfigException])
                assertTrue(e.getMessage.contains("Token not allowed in valid JSON"))
        }
        assertTrue(exceptionThrown)
    }

    @Test
    def configDocumentJSONReplaceWithConcatenationFailure {
        // Attempting a replace on a ConfigDocument parsed from JSON with a concatenation will
        // fail
        val origText = "{\"foo\": \"bar\", \"baz\": \"qux\"}"
        val document = ConfigDocumentFactory.parseString(origText, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON))
        var exceptionThrown = false
        try {
            document.setValue("foo", "1 2 3 concatenation")
        } catch {
            case e: Exception =>
                exceptionThrown = true
                assertTrue(e.isInstanceOf[ConfigException])
                assertTrue(e.getMessage.contains("Parsing JSON and the value set in setValue was either a concatenation or had trailing whitespace, newlines, or comments"))
        }
        assertTrue(exceptionThrown)
    }

    @Test
    def configDocumentFileParse {
        val configDocument = ConfigDocumentFactory.parseFile(resourceFile("/test03.conf"))
        val fileReader = new BufferedReader(new FileReader("config/src/test/resources/test03.conf"))
        var line = fileReader.readLine()
        var sb = new StringBuilder()
        while (line != null) {
            sb.append(line)
            sb.append("\n")
            line = fileReader.readLine()
        }
        fileReader.close()
        val fileText = sb.toString()
        assertEquals(fileText, configDocument.render())
    }

    @Test
    def configDocumentReaderParse {
        val configDocument = ConfigDocumentFactory.parseReader(new FileReader(resourceFile("/test03.conf")))
        val configDocumentFile = ConfigDocumentFactory.parseFile(resourceFile("/test03.conf"))
        assertEquals(configDocumentFile.render(), configDocument.render())
    }

    @Test
    def configDocumentIndentationSingleLineObject {
        // Proper insertion for single-line objects
        var origText = "a { b: c }"
        var configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a { b: c, d : e }", configDocument.setValue("a.d", "e").render())

        origText = "a { b: c }, d: e"
        configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a { b: c }, d: e, f : g", configDocument.setValue("f", "g").render())

        origText = "a { b: c }, d: e,"
        configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a { b: c }, d: e, f : g", configDocument.setValue("f", "g").render())

        assertEquals("a { b: c }, d: e, f : { g : { h : i } }", configDocument.setValue("f.g.h", "i").render())

        origText = "{a { b: c }, d: e}"
        configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("{a { b: c }, d: e, f : g}", configDocument.setValue("f", "g").render())

        assertEquals("{a { b: c }, d: e, f : { g : { h : i } }}", configDocument.setValue("f.g.h", "i").render())
    }

    @Test
    def configDocumentIndentationMultiLineObject {
        var origText = "a {\n  b: c\n}"
        var configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a {\n  b: c\n  e : f\n}", configDocument.setValue("a.e", "f").render())

        assertEquals("a {\n  b: c\n  d : { e : { f : g } }\n}", configDocument.setValue("a.d.e.f", "g").render())

        origText = "a {\n b: c\n}\n"
        configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a {\n b: c\n}\nd : e\n", configDocument.setValue("d", "e").render())

        assertEquals("a {\n b: c\n}\nd : { e : { f : g } }\n", configDocument.setValue("d.e.f", "g").render())
    }

    @Test
    def configDocumentIndentationNested {
        var origText = "a { b { c { d: e } } }"
        var configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a { b { c { d: e, f : g } } }", configDocument.setValue("a.b.c.f", "g").render())

        origText = "a {\n  b {\n    c {\n      d: e\n    }\n  }\n}"
        configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a {\n  b {\n    c {\n      d: e\n      f : g\n    }\n  }\n}", configDocument.setValue("a.b.c.f", "g").render())
    }

    @Test
    def configDocumentIndentationEmptyObject {
        var origText = "a { }"
        var configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a { b : c }", configDocument.setValue("a.b", "c").render())

        origText = "a {\n  b {\n  }\n}"
        configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a {\n  b {\n    c : d\n  }\n}", configDocument.setValue("a.b.c", "d").render())
    }

    @Test
    def configDocumentIndentationMultiLineValue {
        val origText = "a {\n  b {\n    c {\n      d: e\n    }\n  }\n}"
        val configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a {\n  b {\n    c {\n      d: e\n      f : {\n        g: h\n        i: j\n        k: {\n          l: m\n        }\n      }\n    }\n  }\n}",
            configDocument.setValue("a.b.c.f", "{\n  g: h\n  i: j\n  k: {\n    l: m\n  }\n}").render())

        assertEquals("a {\n  b {\n    c {\n      d: e\n      f : 12 13 [1,\n      2,\n      3,\n      {\n        a:b\n      }]\n    }\n  }\n}",
            configDocument.setValue("a.b.c.f", "12 13 [1,\n2,\n3,\n{\n  a:b\n}]").render())
    }

    @Test
    def configDocumentIndentationMultiLineValueSingleLineObject {
        // Weird indentation occurs when adding a multi-line value to a single-line object
        val origText = "a { b { } }"
        val configDocument = ConfigDocumentFactory.parseString(origText)
        assertEquals("a { b { c : {\n   c:d\n } } }", configDocument.setValue("a.b.c", "{\n  c:d\n}").render())
    }

    @Test
    def configDocumentIndentationSingleLineObjectContainingMultiLineValue {
        val origText = "a { b {\n  c: d\n} }"
        val configDocument = ConfigDocumentFactory.parseString(origText)

        assertEquals("a { b {\n  c: d\n}, e : f }", configDocument.setValue("a.e", "f").render())
    }

}
