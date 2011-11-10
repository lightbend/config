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
        Parser.parse(SyntaxFlavor.CONF, new SimpleConfigOrigin("test conf string"), s, includer())
    }

    def parse(s: String) = {
        val tree = parseWithoutResolving(s)

        // resolve substitutions so we can test problems with that, like cycles or
        // interpolating arrays into strings
        tree match {
            case obj: AbstractConfigObject =>
                SubstitutionResolver.resolveWithoutFallbacks(tree, obj)
            case _ =>
                tree
        }
    }

    @Test
    def invalidConfThrows(): Unit = {
        // be sure we throw
        for (invalid <- whitespaceVariations(invalidConf)) {
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
        for (valid <- whitespaceVariations(validConf)) {
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
                list.asJavaList().get(0) match {
                    case subst: ConfigSubstitution =>
                        subst.pieces().get(0) match {
                            case p: Path => p
                        }
                }
        }

        // also parse with the standalone path parser and be sure the
        // outcome is the same
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

        for (invalid <- Seq("a.", ".b", "a..b", "a${b}c", "\"\".", ".\"\"")) {
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
    }
}
