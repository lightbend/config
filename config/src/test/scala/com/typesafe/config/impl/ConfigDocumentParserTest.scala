package com.typesafe.config.impl

import com.typesafe.config.ConfigSyntax.JSON
import com.typesafe.config.{ ConfigException, ConfigParseOptions, ConfigSyntax }
import org.junit.Assert._
import org.junit.Test

class ConfigDocumentParserTest extends TestUtils {

    private def parseTest(origText: String) {
        val node = ConfigDocumentParser.parse(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults())
        assertEquals(origText, node.render())
    }

    private def parseJSONFailuresTest(origText: String, containsMessage: String) {
        var exceptionThrown = false
        val e = intercept[ConfigException] {
            ConfigDocumentParser.parse(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON))
        }
        assertTrue(e.getMessage.contains(containsMessage))
    }

    private def parseSimpleValueTest(origText: String, finalText: String = null) {
        val expectedRenderedText = if (finalText == null) origText else finalText
        val node = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults())
        assertEquals(expectedRenderedText, node.render())
        assertTrue(node.isInstanceOf[ConfigNodeSimpleValue])

        val nodeJSON = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON))
        assertEquals(expectedRenderedText, nodeJSON.render())
        assertTrue(nodeJSON.isInstanceOf[ConfigNodeSimpleValue])
    }

    private def parseComplexValueTest(origText: String) {
        val node = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults())
        assertEquals(origText, node.render())
        assertTrue(node.isInstanceOf[ConfigNodeComplexValue])

        val nodeJSON = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON))
        assertEquals(origText, nodeJSON.render())
        assertTrue(nodeJSON.isInstanceOf[ConfigNodeComplexValue])
    }

    private def parseSingleValueInvalidJSONTest(origText: String, containsMessage: String) {
        val node = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults())
        assertEquals(origText, node.render())

        val e = intercept[ConfigException] {
            ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON))
        }
        assertTrue(e.getMessage.contains(containsMessage))
    }

    private def parseLeadingTrailingFailure(toReplace: String) {
        val e = intercept[ConfigException] {
            ConfigDocumentParser.parseValue(tokenize(toReplace), fakeOrigin(), ConfigParseOptions.defaults())
        }
        assertTrue("expected message parsing leading trailing", e.getMessage.contains("The value from withValueText cannot have leading or trailing newlines, whitespace, or comments"))
    }

    @Test
    def parseSuccess() {
        parseTest("foo:bar")
        parseTest(" foo : bar ")
        parseTest("""include "foo.conf" """)
        parseTest("   \nfoo:bar\n    ")

        // Can parse a map with all simple types
        parseTest(
            """
        aUnquoted : bar
        aString = "qux"
        aNum:123
        aDouble=123.456
        aTrue=true
        aFalse=false
        aNull=null
        aSub =  ${a.b}
        include "foo.conf"
        """)
        parseTest("{}")
        parseTest("{foo:bar}")
        parseTest("{  foo  :  bar  }")
        parseTest("{foo:bar}     ")
        parseTest("""{include "foo.conf"}""")
        parseTest("   \n{foo:bar}\n    ")

        //Can parse a map with all simple types
        parseTest(
            """{
          aUnquoted : bar
          aString = "qux"
          aNum:123
          aDouble=123.456
          aTrue=true
          aFalse=false
          aNull=null
          aSub =  ${a.b}
          include "foo.conf"
          }""")

        // Test that maps can be nested within other maps
        parseTest(
            """
          foo.bar.baz : {
            qux : "abcdefg"
            "abc".def."ghi" : 123
            abc = { foo:bar }
          }
          qux = 123.456
          """)

        // Test that comments can be parsed in maps
        parseTest(
            """{
          foo: bar
          // This is a comment
          baz:qux // This is another comment
         }""")

        // Basic array tests
        parseTest("[]")
        parseTest("[foo]")

        // Test trailing comment and whitespace
        parseTest("[foo,]")
        parseTest("[foo,]     ")

        // Test leading and trailing whitespace
        parseTest("   \n[]\n   ")

        // Can parse arrays with all simple types
        parseTest("""[foo, bar,"qux", 123,123.456, true,false, null, ${a.b}]""")
        parseTest("""[foo,   bar,"qux"    , 123 ,  123.456, true,false, null,   ${a.b}   ]""")

        // Basic concatenation tests
        parseTest("[foo bar baz qux]")
        parseTest("{foo: foo bar baz qux}")
        parseTest("[abc 123 123.456 null true false [1, 2, 3] {a:b}, 2]")

        // Complex node with all types test
        parseTest(
            """{
          foo: bar baz    qux    ernie
          // The above was a concatenation

          baz   =   [ abc 123, {a:12
                                b: {
                                  c: 13
                                  d: {
                                    a: 22
                                    b: "abcdefg" # this is a comment
                                    c: [1, 2, 3]
                                  }
                                }
                                }, # this was an object in an array
                                //The above value is a map containing a map containing a map, all in an array
                                22,
                                // The below value is an array contained in another array
                                [1,2,3]]
          // This is a map with some nested maps and arrays within it, as well as some concatenations
          qux {
            baz: abc 123
            bar: {
              baz: abcdefg
              bar: {
                a: null
                b: true
                c: [true false 123, null, [1, 2, 3]]
              }
            }
          }
        // Did I cover everything?
        }""")

        // Can correctly parse a JSON string
        val origText =
            """{
              "foo": "bar",
              "baz": 123,
              "qux": true,
              "array": [
                {"a": true,
                 "c": false},
                12
              ]
           }
      """
        val node = ConfigDocumentParser.parse(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON))
        assertEquals(origText, node.render())
    }

    @Test
    def parseJSONFailures() {
        // JSON does not support concatenations
        parseJSONFailuresTest("""{ "foo": 123 456 789 } """, "Expecting close brace } or a comma")

        // JSON must begin with { or [
        parseJSONFailuresTest(""""a": 123, "b": 456""", "Document must have an object or array at root")

        // JSON does not support unquoted text
        parseJSONFailuresTest("""{"foo": unquotedtext}""", "Token not allowed in valid JSON")

        // JSON does not support substitutions
        parseJSONFailuresTest("""{"foo": ${"a.b"}}""", "Substitutions (${} syntax) not allowed in JSON")

        // JSON does not support multi-element paths
        parseJSONFailuresTest("""{"foo"."bar": 123}""", "Token not allowed in valid JSON")

        // JSON does not support =
        parseJSONFailuresTest("""{"foo"=123}""", """Key '"foo"' may not be followed by token: '='""")

        // JSON does not support +=
        parseJSONFailuresTest("""{"foo" += "bar"}""", """Key '"foo"' may not be followed by token: '+='""")

        // JSON does not support duplicate keys
        parseJSONFailuresTest("""{"foo" : 123, "foo": 456}""", "JSON does not allow duplicate fields")

        // JSON does not support trailing commas
        parseJSONFailuresTest("""{"foo" : 123,}""", "expecting a field name after a comma, got a close brace } instead")

        // JSON does not support empty documents
        parseJSONFailuresTest("", "Empty document")

    }

    @Test
    def parseSingleValues() {
        // Parse simple values
        parseSimpleValueTest("123")
        parseSimpleValueTest("123.456")
        parseSimpleValueTest(""""a string"""")
        parseSimpleValueTest("true")
        parseSimpleValueTest("false")
        parseSimpleValueTest("null")

        // Can parse complex values
        parseComplexValueTest("""{"a": "b"}""")
        parseComplexValueTest("""["a","b","c"]""")

        // Check that concatenations are handled by CONF parsing
        var origText = "123 456 \"abc\""
        var node = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults())
        assertEquals(origText, node.render())

        // Check that keys with no separators and object values are handled by CONF parsing
        origText = """{"foo" { "bar" : 12 } }"""
        node = ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults())
        assertEquals(origText, node.render())
    }

    @Test
    def parseSingleValuesFailures() {
        // Parse Simple Value throws on leading and trailing whitespace, comments, or newlines
        parseLeadingTrailingFailure("   123")
        parseLeadingTrailingFailure("123   ")
        parseLeadingTrailingFailure(" 123 ")
        parseLeadingTrailingFailure("\n123")
        parseLeadingTrailingFailure("123\n")
        parseLeadingTrailingFailure("\n123\n")
        parseLeadingTrailingFailure("#thisisacomment\n123#comment")

        // Parse Simple Value correctly throws on whitespace after a concatenation
        parseLeadingTrailingFailure("123 456 789   ")

        parseSingleValueInvalidJSONTest("unquotedtext", "Token not allowed in valid JSON")
        parseSingleValueInvalidJSONTest("${a.b}", "Substitutions (${} syntax) not allowed in JSON")

        // Check that concatenations in JSON will throw an error
        var origText = "123 456 \"abc\""
        var e = intercept[ConfigException] { ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON)) }
        assertTrue("expected message for parsing concat as json", e.getMessage.contains("Parsing JSON and the value set in withValueText was either a concatenation or had trailing whitespace, newlines, or comments"))

        // Check that keys with no separators and object values in JSON will throw an error
        origText = """{"foo" { "bar" : 12 } }"""
        e = intercept[ConfigException] { ConfigDocumentParser.parseValue(tokenize(origText), fakeOrigin(), ConfigParseOptions.defaults().setSyntax(JSON)) }
        assertTrue("expected failure for key foo followed by token", e.getMessage.contains("""Key '"foo"' may not be followed by token: '{'"""))
    }

    @Test
    def parseEmptyDocument() {
        val node = ConfigDocumentParser.parse(tokenize(""), fakeOrigin(), ConfigParseOptions.defaults())
        assertTrue(node.value().isInstanceOf[ConfigNodeObject])
        assertTrue(node.value().children().isEmpty())

        val node2 = ConfigDocumentParser.parse(tokenize("#comment\n#comment\n\n"), fakeOrigin(), ConfigParseOptions.defaults())
        assertTrue(node2.value().isInstanceOf[ConfigNodeObject])
    }
}
