package com.typesafe.config.impl

import java.io.{ BufferedReader, FileReader }
import java.nio.charset.StandardCharsets
import java.nio.file.{ Paths, Files }

import com.typesafe.config._
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
    def configDocumentReplace() {
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
    def configDocumentSetNewValueBraceRoot {
        val origText = "{\n\t\"a\":\"b\",\n\t\"c\":\"d\"\n}"
        val finalTextConf = "{\n\t\"a\":\"b\",\n\t\"c\":\"d\"\n\n\"e\" : \"f\"\n}"
        val finalTextJson = "{\n\t\"a\":\"b\",\n\t\"c\":\"d\",\n\n\"e\" : \"f\"\n}"
        configDocumentReplaceConfTest(origText, finalTextConf, "\"f\"", "\"e\"")
        configDocumentReplaceJsonTest(origText, finalTextJson, "\"f\"", "\"e\"")
    }

    @Test
    def configDocumentSetNewValueNoBraces {
        val origText = "\"a\":\"b\",\n\"c\":\"d\"\n"
        val finalText = "\"a\":\"b\",\n\"c\":\"d\"\n\n\"e\" : \"f\"\n"
        configDocumentReplaceConfTest(origText, finalText, "\"f\"", "\"e\"")
    }

    @Test
    def configDocumentSetNewValueMultiLevelConf {
        val origText = "a:b\nc:d"
        val finalText = "a:b\nc:d\ne : {\nf : {\ng : 12\n}\n}\n"
        configDocumentReplaceConfTest(origText, finalText, "12", "e.f.g")
    }

    @Test
    def configDocumentSetNewValueMultiLevelJson {
        val origText = "{\"a\":\"b\",\n\"c\":\"d\"}"
        val finalText = "{\"a\":\"b\",\n\"c\":\"d\",\n\"e\" : {\n\"f\" : {\n\"g\" : 12\n}\n}\n}"
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
    def configDocumentArrayReplaceFailure {
        // Attempting a replace on a ConfigDocument parsed from an array throws an error
        val origText = "[1, 2, 3, 4, 5]"
        val document = ConfigDocumentFactory.parseString(origText)
        var exceptionThrown = false
        try {
            document.setValue("a", "1")
        } catch {
            case e: Exception =>
                exceptionThrown = true
                assertTrue(e.isInstanceOf[ConfigException])
                assertTrue(e.getMessage.contains("ConfigDocument had an array at the root level"))
        }
        assertTrue(exceptionThrown)
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

}
