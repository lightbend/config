/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import java.io.Reader
import java.io.StringReader
import com.typesafe.config._
import java.util.HashMap
import scala.collection.JavaConverters._
import java.io.File
import java.net.URL
import java.util.Properties
import java.io.ByteArrayInputStream

class ConfParserTest extends TestUtils {

    def parseWithoutResolving(s: String) = {
        val options = ConfigParseOptions.defaults().
            setOriginDescription("test conf string").
            setSyntax(ConfigSyntax.CONF);
        Parseable.newString(s, options).parseValue().asInstanceOf[AbstractConfigValue]
    }

    def parse(s: String) = {
        val tree = parseWithoutResolving(s)

        // resolve substitutions so we can test problems with that, like cycles or
        // interpolating arrays into strings
        tree match {
            case obj: AbstractConfigObject =>
                SubstitutionResolver.resolve(tree, obj, ConfigResolveOptions.noSystem())
            case _ =>
                tree
        }
    }

    @Test
    def invalidConfThrows(): Unit = {
        // be sure we throw
        for (invalid <- whitespaceVariations(invalidConf, false)) {
            addOffendingJsonToException("config", invalid.test) {
                intercept[ConfigException] {
                    parse(invalid.test)
                }
            }
        }
    }

    @Test
    def validConfWorks(): Unit = {
        // all we're checking here unfortunately is that it doesn't throw.
        // for a more thorough check, use the EquivalentsTest stuff.
        for (valid <- whitespaceVariations(validConf, true)) {
            val ourAST = addOffendingJsonToException("config-conf", valid.test) {
                parse(valid.test)
            }
            // let's also check round-trip rendering
            val rendered = ourAST.render()
            val reparsed = addOffendingJsonToException("config-conf-reparsed", rendered) {
                parse(rendered)
            }
            assertEquals(ourAST, reparsed)
        }
    }

    private def parsePath(s: String): Path = {
        var firstException: ConfigException = null
        var secondException: ConfigException = null

        // parse first by wrapping into a whole document and using
        // the regular parser.
        val result = try {
            val tree = parseWithoutResolving("[${" + s + "}]")
            tree match {
                case list: ConfigList =>
                    list.get(0) match {
                        case subst: ConfigSubstitution =>
                            subst.pieces().get(0) match {
                                case p: Path => p
                            }
                    }
            }
        } catch {
            case e: ConfigException =>
                firstException = e
                null
        }

        // also parse with the standalone path parser and be sure the
        // outcome is the same.
        try {
            val shouldBeSame = Parser.parsePath(s)
            assertEquals(result, shouldBeSame)
        } catch {
            case e: ConfigException =>
                secondException = e
        }

        if (firstException == null && secondException != null)
            throw new AssertionError("only the standalone path parser threw", secondException)
        if (firstException != null && secondException == null)
            throw new AssertionError("only the whole-document parser threw", firstException)

        if (firstException != null)
            throw firstException
        if (secondException != null)
            throw new RuntimeException("wtf, should have thrown because not equal")

        result
    }

    @Test
    def pathParsing() {
        assertEquals(path("a"), parsePath("a"))
        assertEquals(path("a", "b"), parsePath("a.b"))
        assertEquals(path("a.b"), parsePath("\"a.b\""))
        assertEquals(path("a."), parsePath("\"a.\""))
        assertEquals(path(".b"), parsePath("\".b\""))
        assertEquals(path("true"), parsePath("true"))
        assertEquals(path("a"), parsePath(" a "))
        assertEquals(path("a ", "b"), parsePath(" a .b"))
        assertEquals(path("a ", " b"), parsePath(" a . b"))
        assertEquals(path("a  b"), parsePath(" a  b"))
        assertEquals(path("a", "b.c", "d"), parsePath("a.\"b.c\".d"))
        assertEquals(path("3", "14"), parsePath("3.14"))
        assertEquals(path("a3", "14"), parsePath("a3.14"))
        assertEquals(path(""), parsePath("\"\""))
        assertEquals(path("a", "", "b"), parsePath("a.\"\".b"))
        assertEquals(path("a", ""), parsePath("a.\"\""))
        assertEquals(path("", "b"), parsePath("\"\".b"))
        assertEquals(path(""), parsePath("\"\"\"\""))
        assertEquals(path("a", ""), parsePath("a.\"\"\"\""))
        assertEquals(path("", "b"), parsePath("\"\"\"\".b"))
        assertEquals(path("", "", ""), parsePath(""" "".""."" """))
        assertEquals(path("a-c"), parsePath("a-c"))
        assertEquals(path("a_c"), parsePath("a_c"))
        assertEquals(path("-"), parsePath("\"-\""))

        // here 10.0 is part of an unquoted string
        assertEquals(path("foo10", "0"), parsePath("foo10.0"))
        // here 10.0 is a number that gets value-concatenated
        assertEquals(path("10", "0foo"), parsePath("10.0foo"))
        // just a number
        assertEquals(path("10", "0"), parsePath("10.0"))

        for (invalid <- Seq("", " ", "  \n   \n  ", "a.", ".b", "a..b", "a${b}c", "\"\".", ".\"\"")) {
            try {
                intercept[ConfigException.BadPath] {
                    parsePath(invalid)
                }
            } catch {
                case e =>
                    System.err.println("failed on: '" + invalid + "'");
                    throw e;
            }
        }

        intercept[ConfigException.Parse] {
            // this gets parsed as a number since it starts with '-'
            parsePath("-")
        }
    }

    @Test
    def duplicateKeyLastWins() {
        val obj = parseConfig("""{ "a" : 10, "a" : 11 } """)

        assertEquals(1, obj.root.size())
        assertEquals(11, obj.getInt("a"))
    }

    @Test
    def duplicateKeyObjectsMerged() {
        val obj = parseConfig("""{ "a" : { "x" : 1, "y" : 2 }, "a" : { "x" : 42, "z" : 100 } }""")

        assertEquals(1, obj.root.size())
        assertEquals(3, obj.getObject("a").size())
        assertEquals(42, obj.getInt("a.x"))
        assertEquals(2, obj.getInt("a.y"))
        assertEquals(100, obj.getInt("a.z"))
    }

    @Test
    def duplicateKeyObjectsMergedRecursively() {
        val obj = parseConfig("""{ "a" : { "b" : { "x" : 1, "y" : 2 } }, "a" : { "b" : { "x" : 42, "z" : 100 } } }""")

        assertEquals(1, obj.root.size())
        assertEquals(1, obj.getObject("a").size())
        assertEquals(3, obj.getObject("a.b").size())
        assertEquals(42, obj.getInt("a.b.x"))
        assertEquals(2, obj.getInt("a.b.y"))
        assertEquals(100, obj.getInt("a.b.z"))
    }

    @Test
    def duplicateKeyObjectsMergedRecursivelyDeeper() {
        val obj = parseConfig("""{ "a" : { "b" : { "c" : { "x" : 1, "y" : 2 } } }, "a" : { "b" : { "c" : { "x" : 42, "z" : 100 } } } }""")

        assertEquals(1, obj.root.size())
        assertEquals(1, obj.getObject("a").size())
        assertEquals(1, obj.getObject("a.b").size())
        assertEquals(3, obj.getObject("a.b.c").size())
        assertEquals(42, obj.getInt("a.b.c.x"))
        assertEquals(2, obj.getInt("a.b.c.y"))
        assertEquals(100, obj.getInt("a.b.c.z"))
    }

    @Test
    def duplicateKeyObjectNullObject() {
        // null is supposed to "reset" the object at key "a"
        val obj = parseConfig("""{ a : { b : 1 }, a : null, a : { c : 2 } }""")
        assertEquals(1, obj.root.size())
        assertEquals(1, obj.getObject("a").size())
        assertEquals(2, obj.getInt("a.c"))
    }

    @Test
    def duplicateKeyObjectNumberObject() {
        val obj = parseConfig("""{ a : { b : 1 }, a : 42, a : { c : 2 } }""")
        assertEquals(1, obj.root.size())
        assertEquals(1, obj.getObject("a").size())
        assertEquals(2, obj.getInt("a.c"))
    }

    @Test
    def impliedCommaHandling() {
        val valids = Seq(
            """
// one line
{
  a : y, b : z, c : [ 1, 2, 3 ]
}""", """
// multiline but with all commas
{
  a : y,
  b : z,
  c : [
    1,
    2,
    3,
  ],
}
""", """
// multiline with no commas
{
  a : y
  b : z
  c : [
    1
    2
    3
  ]
}
""")

        def dropCurlies(s: String) = {
            // drop the outside curly braces
            val first = s.indexOf('{')
            val last = s.lastIndexOf('}')
            s.substring(0, first) + s.substring(first + 1, last) + s.substring(last + 1, s.length())
        }

        val changes = Seq(
            { s: String => s },
            { s: String => s.replace("\n", "\n\n") },
            { s: String => s.replace("\n", "\n\n\n") },
            { s: String => s.replace(",\n", "\n,\n") },
            { s: String => s.replace(",\n", "\n\n,\n\n") },
            { s: String => s.replace("\n", " \n ") },
            { s: String => s.replace(",\n", "  \n  \n  ,  \n  \n  ") },
            { s: String => dropCurlies(s) })

        var tested = 0;
        for (v <- valids; change <- changes) {
            tested += 1;
            val obj = parseConfig(change(v))
            assertEquals(3, obj.root.size())
            assertEquals("y", obj.getString("a"))
            assertEquals("z", obj.getString("b"))
            assertEquals(Seq(1, 2, 3), obj.getIntList("c").asScala)
        }

        assertEquals(valids.length * changes.length, tested)

        // with no newline or comma, we do value concatenation
        val noNewlineInArray = parseConfig(" { c : [ 1 2 3 ] } ")
        assertEquals(Seq("1 2 3"), noNewlineInArray.getStringList("c").asScala)

        val noNewlineInArrayWithQuoted = parseConfig(""" { c : [ "4" "5" "6" ] } """)
        assertEquals(Seq("4 5 6"), noNewlineInArrayWithQuoted.getStringList("c").asScala)

        val noNewlineInObject = parseConfig(" { a : b c } ")
        assertEquals("b c", noNewlineInObject.getString("a"))

        val noNewlineAtEnd = parseConfig("a : b")
        assertEquals("b", noNewlineAtEnd.getString("a"))

        intercept[ConfigException] {
            parseConfig("{ a : y b : z }")
        }

        intercept[ConfigException] {
            parseConfig("""{ "a" : "y" "b" : "z" }""")
        }
    }

    @Test
    def keysWithSlash() {
        val obj = parseConfig("""/a/b/c=42, x/y/z : 32""")
        assertEquals(42, obj.getInt("/a/b/c"))
        assertEquals(32, obj.getInt("x/y/z"))
    }

    private def lineNumberTest(num: Int, text: String) {
        val e = intercept[ConfigException] {
            parseConfig(text)
        }
        if (!e.getMessage.contains(num + ":"))
            throw new Exception("error message did not contain line '" + num + "' '" + text.replace("\n", "\\n") + "'", e)
    }

    @Test
    def lineNumbersInErrors() {
        // error is at the last char
        lineNumberTest(1, "}")
        lineNumberTest(2, "\n}")
        lineNumberTest(3, "\n\n}")

        // error is before a final newline
        lineNumberTest(1, "}\n")
        lineNumberTest(2, "\n}\n")
        lineNumberTest(3, "\n\n}\n")

        // with unquoted string
        lineNumberTest(1, "foo")
        lineNumberTest(2, "\nfoo")
        lineNumberTest(3, "\n\nfoo")

        // with quoted string
        lineNumberTest(1, "\"foo\"")
        lineNumberTest(2, "\n\"foo\"")
        lineNumberTest(3, "\n\n\"foo\"")

        // newline in middle of number uses the line the number was on
        lineNumberTest(1, "1e\n")
        lineNumberTest(2, "\n1e\n")
        lineNumberTest(3, "\n\n1e\n")
    }

    @Test
    def toStringForParseables() {
        // just be sure the toString don't throw, to get test coverage
        val options = ConfigParseOptions.defaults()
        Parseable.newFile(new File("foo"), options).toString
        Parseable.newResources(classOf[ConfParserTest], "foo", options).toString
        Parseable.newURL(new URL("file:///foo"), options).toString
        Parseable.newProperties(new Properties(), options).toString
        Parseable.newReader(new StringReader("{}"), options).toString
    }
}
