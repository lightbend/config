package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import java.io.Reader
import java.io.StringReader
import com.typesafe.config._
import java.util.HashMap
import scala.collection.JavaConverters._

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
        }
    }

    private def parsePath(s: String): Path = {
        // parse first by wrapping into a whole document and using
        // the regular parser.
        val tree = parseWithoutResolving("[${" + s + "}]")
        val result = tree match {
            case list: ConfigList =>
                list.get(0) match {
                    case subst: ConfigSubstitution =>
                        subst.pieces().get(0) match {
                            case p: Path => p
                        }
                }
        }

        // also parse with the standalone path parser and be sure the
        // outcome is the same.
        val shouldBeSame = Parser.parsePath(s)
        assertEquals(result, shouldBeSame)

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

        for (invalid <- Seq("", "a.", ".b", "a..b", "a${b}c", "\"\".", ".\"\"")) {
            try {
                intercept[ConfigException.BadPath] {
                    parsePath(invalid)
                }
            } catch {
                case e =>
                    System.err.println("failed on: " + invalid);
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
        val obj = parseObject("""{ "a" : 10, "a" : 11 } """)

        assertEquals(1, obj.size())
        assertEquals(11, obj.getInt("a"))
    }

    @Test
    def duplicateKeyObjectsMerged() {
        val obj = parseObject("""{ "a" : { "x" : 1, "y" : 2 }, "a" : { "x" : 42, "z" : 100 } }""")

        assertEquals(1, obj.size())
        assertEquals(3, obj.getObject("a").size())
        assertEquals(42, obj.getInt("a.x"))
        assertEquals(2, obj.getInt("a.y"))
        assertEquals(100, obj.getInt("a.z"))
    }

    @Test
    def duplicateKeyObjectsMergedRecursively() {
        val obj = parseObject("""{ "a" : { "b" : { "x" : 1, "y" : 2 } }, "a" : { "b" : { "x" : 42, "z" : 100 } } }""")

        assertEquals(1, obj.size())
        assertEquals(1, obj.getObject("a").size())
        assertEquals(3, obj.getObject("a.b").size())
        assertEquals(42, obj.getInt("a.b.x"))
        assertEquals(2, obj.getInt("a.b.y"))
        assertEquals(100, obj.getInt("a.b.z"))
    }

    @Test
    def duplicateKeyObjectsMergedRecursivelyDeeper() {
        val obj = parseObject("""{ "a" : { "b" : { "c" : { "x" : 1, "y" : 2 } } }, "a" : { "b" : { "c" : { "x" : 42, "z" : 100 } } } }""")

        assertEquals(1, obj.size())
        assertEquals(1, obj.getObject("a").size())
        assertEquals(1, obj.getObject("a.b").size())
        assertEquals(3, obj.getObject("a.b.c").size())
        assertEquals(42, obj.getInt("a.b.c.x"))
        assertEquals(2, obj.getInt("a.b.c.y"))
        assertEquals(100, obj.getInt("a.b.c.z"))
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
            val obj = parseObject(change(v))
            assertEquals(3, obj.size())
            assertEquals("y", obj.getString("a"))
            assertEquals("z", obj.getString("b"))
            assertEquals(Seq(1, 2, 3), obj.getIntList("c").asScala)
        }

        assertEquals(valids.length * changes.length, tested)

        // with no newline or comma, we do value concatenation
        val noNewlineInArray = parseObject(" { c : [ 1 2 3 ] } ")
        assertEquals(Seq("1 2 3"), noNewlineInArray.getStringList("c").asScala)

        val noNewlineInArrayWithQuoted = parseObject(""" { c : [ "4" "5" "6" ] } """)
        assertEquals(Seq("4 5 6"), noNewlineInArrayWithQuoted.getStringList("c").asScala)

        val noNewlineInObject = parseObject(" { a : b c } ")
        assertEquals("b c", noNewlineInObject.getString("a"))

        val noNewlineAtEnd = parseObject("a : b")
        assertEquals("b", noNewlineAtEnd.getString("a"))

        intercept[ConfigException] {
            parseObject("{ a : y b : z }")
        }

        intercept[ConfigException] {
            parseObject("""{ "a" : "y" "b" : "z" }""")
        }
    }
}
