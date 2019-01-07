/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import java.io.{ File, StringReader }

import com.typesafe.config._

import scala.collection.JavaConverters._
import java.net.URL
import java.util.Properties

class ConfParserTest extends TestUtils {

    def parseWithoutResolving(s: String): AbstractConfigValue = {
        val options = ConfigParseOptions.defaults().
            setOriginDescription("test conf string").
            setSyntax(ConfigSyntax.CONF)
        Parseable.newString(s, options).parseValue()
    }

    def parse(s: String): AbstractConfigValue = {
        val tree = parseWithoutResolving(s)

        // resolve substitutions so we can test problems with that, like cycles or
        // interpolating arrays into strings
        tree match {
            case obj: AbstractConfigObject =>
                ResolveContext.resolve(tree, obj, ConfigResolveOptions.noSystem())
            case _ =>
                tree
        }
    }

    @Test
    def invalidConfThrows(): Unit = {
        // be sure we throw
        for (invalid <- whitespaceVariations(invalidConf, validInLift = false)) {
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
                        case ref: ConfigReference =>
                            ref.expression().path()
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
            val shouldBeSame = PathParser.parsePath(s)
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
        assertEquals(path("3", "14", "159"), parsePath("3.14.159"))
        assertEquals(path("a3", "14"), parsePath("a3.14"))
        assertEquals(path(""), parsePath("\"\""))
        assertEquals(path("a", "", "b"), parsePath("a.\"\".b"))
        assertEquals(path("a", ""), parsePath("a.\"\""))
        assertEquals(path("", "b"), parsePath("\"\".b"))
        assertEquals(path("", "", ""), parsePath(""" "".""."" """))
        assertEquals(path("a-c"), parsePath("a-c"))
        assertEquals(path("a_c"), parsePath("a_c"))
        assertEquals(path("-"), parsePath("\"-\""))
        assertEquals(path("-"), parsePath("-"))
        assertEquals(path("-foo"), parsePath("-foo"))
        assertEquals(path("-10"), parsePath("-10"))

        // here 10.0 is part of an unquoted string
        assertEquals(path("foo10", "0"), parsePath("foo10.0"))
        // here 10.0 is a number that gets value-concatenated
        assertEquals(path("10", "0foo"), parsePath("10.0foo"))
        // just a number
        assertEquals(path("10", "0"), parsePath("10.0"))
        // multiple-decimal number
        assertEquals(path("1", "2", "3", "4"), parsePath("1.2.3.4"))

        for (invalid <- Seq("", " ", "  \n   \n  ", "a.", ".b", "a..b", "a${b}c", "\"\".", ".\"\"")) {
            try {
                intercept[ConfigException.BadPath] {
                    parsePath(invalid)
                }
            } catch {
                case e: Throwable =>
                    System.err.println("failed on: '" + invalid + "'")
                    throw e;
            }
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

        var tested = 0
        for (v <- valids; change <- changes) {
            tested += 1
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

        // newlines in triple-quoted string should not hose up the numbering
        lineNumberTest(1, "a : \"\"\"foo\"\"\"}")
        lineNumberTest(2, "a : \"\"\"foo\n\"\"\"}")
        lineNumberTest(3, "a : \"\"\"foo\nbar\nbaz\"\"\"}")
        //   newlines after the triple quoted string
        lineNumberTest(5, "a : \"\"\"foo\nbar\nbaz\"\"\"\n\n}")
        //   triple quoted string ends in a newline
        lineNumberTest(6, "a : \"\"\"foo\nbar\nbaz\n\"\"\"\n\n}")
        //   end in the middle of triple-quoted string
        lineNumberTest(5, "a : \"\"\"foo\n\n\nbar\n")
    }

    @Test
    def toStringForParseablesWorks() {
        // just be sure the toString don't throw, to get test coverage
        val options = ConfigParseOptions.defaults()
        Parseable.newFile(new File("foo"), options).toString
        Parseable.newResources(classOf[ConfParserTest], "foo", options).toString
        Parseable.newURL(new URL("file:///foo"), options).toString
        Parseable.newProperties(new Properties(), options).toString
        Parseable.newReader(new StringReader("{}"), options).toString
    }

    private def assertComments(comments: Seq[String], conf: Config) {
        assertEquals(comments, conf.root().origin().comments().asScala.toSeq)
    }

    private def assertComments(comments: Seq[String], conf: Config, path: String) {
        assertEquals(comments, conf.getValue(path).origin().comments().asScala)
    }

    private def assertComments(comments: Seq[String], conf: Config, path: String, index: Int) {
        val v = conf.getList(path).get(index)
        assertEquals(comments, v.origin().comments().asScala.toSeq)
    }

    @Test
    def trackCommentsForSingleField() {
        // no comments
        val conf0 = parseConfig("""
                {
                foo=10 }
                """)
        assertComments(Seq(), conf0, "foo")

        // comment in front of a field is used
        val conf1 = parseConfig("""
                { # Before
                foo=10 }
                """)
        assertComments(Seq(" Before"), conf1, "foo")

        // comment with a blank line after is dropped
        val conf2 = parseConfig("""
                { # BlankAfter

                foo=10 }
                """)
        assertComments(Seq(), conf2, "foo")

        // comment in front of a field is used with no root {}
        val conf3 = parseConfig("""
                # BeforeNoBraces
                foo=10
                """)
        assertComments(Seq(" BeforeNoBraces"), conf3, "foo")

        // comment with a blank line after is dropped with no root {}
        val conf4 = parseConfig("""
                # BlankAfterNoBraces

                foo=10
                """)
        assertComments(Seq(), conf4, "foo")

        // comment same line after field is used
        val conf5 = parseConfig("""
                {
                foo=10 # SameLine
                }
                """)
        assertComments(Seq(" SameLine"), conf5, "foo")

        // comment before field separator is used
        val conf6 = parseConfig("""
                {
                foo # BeforeSep
                =10
                }
                """)
        assertComments(Seq(" BeforeSep"), conf6, "foo")

        // comment after field separator is used
        val conf7 = parseConfig("""
                {
                foo= # AfterSep
                10
                }
                """)
        assertComments(Seq(" AfterSep"), conf7, "foo")

        // comment on next line is NOT used
        val conf8 = parseConfig("""
                {
                foo=10
                # NextLine
                }
                """)
        assertComments(Seq(), conf8, "foo")

        // comment before field separator on new line
        val conf9 = parseConfig("""
                {
                foo
                # BeforeSepOwnLine
                =10
                }
                """)
        assertComments(Seq(" BeforeSepOwnLine"), conf9, "foo")

        // comment after field separator on its own line
        val conf10 = parseConfig("""
                {
                foo=
                # AfterSepOwnLine
                10
                }
                """)
        assertComments(Seq(" AfterSepOwnLine"), conf10, "foo")

        // comments comments everywhere
        val conf11 = parseConfig("""
                {# Before
                foo
                # BeforeSep
                = # AfterSepSameLine
                # AfterSepNextLine
                10 # AfterValue
                # AfterValueNewLine (should NOT be used)
                }
                """)
        assertComments(Seq(" Before", " BeforeSep", " AfterSepSameLine", " AfterSepNextLine", " AfterValue"), conf11, "foo")

        // empty object
        val conf12 = parseConfig("""# BeforeEmpty
                {} #AfterEmpty
                # NewLine
                """)
        assertComments(Seq(" BeforeEmpty", "AfterEmpty"), conf12)

        // empty array
        val conf13 = parseConfig("""
                foo=
                # BeforeEmptyArray
                  [] #AfterEmptyArray
                # NewLine
                """)
        assertComments(Seq(" BeforeEmptyArray", "AfterEmptyArray"), conf13, "foo")

        // array element
        val conf14 = parseConfig("""
                foo=[
                # BeforeElement
                10 # AfterElement
                ]
                """)
        assertComments(Seq(" BeforeElement", " AfterElement"), conf14, "foo", 0)

        // field with comma after it
        val conf15 = parseConfig("""
                foo=10, # AfterCommaField
                """)
        assertComments(Seq(" AfterCommaField"), conf15, "foo")

        // element with comma after it
        val conf16 = parseConfig("""
                foo=[10, # AfterCommaElement
                ]
                """)
        assertComments(Seq(" AfterCommaElement"), conf16, "foo", 0)

        // field with comma after it but comment isn't on the field's line, so not used
        val conf17 = parseConfig("""
                foo=10
                , # AfterCommaFieldNotUsed
                """)
        assertComments(Seq(), conf17, "foo")

        // element with comma after it but comment isn't on the field's line, so not used
        val conf18 = parseConfig("""
                foo=[10
                , # AfterCommaElementNotUsed
                ]
                """)
        assertComments(Seq(), conf18, "foo", 0)

        // comment on new line, before comma, should not be used
        val conf19 = parseConfig("""
                foo=10
                # BeforeCommaFieldNotUsed
                ,
                """)
        assertComments(Seq(), conf19, "foo")

        // comment on new line, before comma, should not be used
        val conf20 = parseConfig("""
                foo=[10
                # BeforeCommaElementNotUsed
                ,
                ]
                """)
        assertComments(Seq(), conf20, "foo", 0)

        // comment on same line before comma
        val conf21 = parseConfig("""
                foo=10 # BeforeCommaFieldSameLine
                ,
                """)
        assertComments(Seq(" BeforeCommaFieldSameLine"), conf21, "foo")

        // comment on same line before comma
        val conf22 = parseConfig("""
                foo=[10 # BeforeCommaElementSameLine
                ,
                ]
                """)
        assertComments(Seq(" BeforeCommaElementSameLine"), conf22, "foo", 0)

        // comment with a line containing only whitespace after is dropped
        val conf23 = parseConfig("""
                { # BlankAfter

                foo=10 }
                              """)
        assertComments(Seq(), conf23, "foo")
    }

    @Test
    def trackCommentsForMultipleFields() {
        // nested objects
        val conf5 = parseConfig("""
             # Outside
             bar {
                # Ignore me

                # Middle
                # two lines
                baz {
                    # Inner
                    foo=10 # AfterInner
                    # This should be ignored
                } # AfterMiddle
                # ignored
             } # AfterOutside
             # ignored!
             """)
        assertComments(Seq(" Inner", " AfterInner"), conf5, "bar.baz.foo")
        assertComments(Seq(" Middle", " two lines", " AfterMiddle"), conf5, "bar.baz")
        assertComments(Seq(" Outside", " AfterOutside"), conf5, "bar")

        // multiple fields
        val conf6 = parseConfig("""{
                # this is not with a field

                # this is field A
                a : 10,
                # this is field B
                b : 12 # goes with field B which has no comma
                # this is field C
                c : 14, # goes with field C after comma
                # not used
                # this is not used
                # nor is this
                # multi-line block

                # this is with field D
                # this is with field D also
                d : 16

                # this is after the fields
    }""")
        assertComments(Seq(" this is field A"), conf6, "a")
        assertComments(Seq(" this is field B", " goes with field B which has no comma"), conf6, "b")
        assertComments(Seq(" this is field C", " goes with field C after comma"), conf6, "c")
        assertComments(Seq(" this is with field D", " this is with field D also"), conf6, "d")

        // array
        val conf7 = parseConfig("""
                # before entire array
                array = [
                # goes with 0
                0,
                # goes with 1
                1, # with 1 after comma
                # goes with 2
                2 # no comma after 2
                # not with anything
                ] # after entire array
                """)
        assertComments(Seq(" goes with 0"), conf7, "array", 0)
        assertComments(Seq(" goes with 1", " with 1 after comma"), conf7, "array", 1)
        assertComments(Seq(" goes with 2", " no comma after 2"), conf7, "array", 2)
        assertComments(Seq(" before entire array", " after entire array"), conf7, "array")

        // properties-like syntax
        val conf8 = parseConfig("""
                # ignored comment

                # x.y comment
                x.y = 10
                # x.z comment
                x.z = 11
                # x.a comment
                x.a = 12
                # a.b comment
                a.b = 14
                a.c = 15
                a.d = 16 # a.d comment
                # ignored comment
                """)

        assertComments(Seq(" x.y comment"), conf8, "x.y")
        assertComments(Seq(" x.z comment"), conf8, "x.z")
        assertComments(Seq(" x.a comment"), conf8, "x.a")
        assertComments(Seq(" a.b comment"), conf8, "a.b")
        assertComments(Seq(), conf8, "a.c")
        assertComments(Seq(" a.d comment"), conf8, "a.d")
        // here we're concerned that comments apply only to leaf
        // nodes, not to parent objects.
        assertComments(Seq(), conf8, "x")
        assertComments(Seq(), conf8, "a")
    }

    @Test
    def includeFile() {
        val conf = ConfigFactory.parseString("include file(" + jsonQuotedResourceFile("test01") + ")")

        // should have loaded conf, json, properties
        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertEquals(1, conf.getInt("fromJson1"))
        assertEquals("abc", conf.getString("fromProps.abc"))
    }

    @Test
    def includeFileWithExtension() {
        val conf = ConfigFactory.parseString("include file(" + jsonQuotedResourceFile("test01.conf") + ")")

        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertFalse(conf.hasPath("fromJson1"))
        assertFalse(conf.hasPath("fromProps.abc"))
    }

    @Test
    def includeFileWhitespaceInsideParens() {
        val conf = ConfigFactory.parseString("include file(  \n  " + jsonQuotedResourceFile("test01") + "  \n  )")

        // should have loaded conf, json, properties
        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertEquals(1, conf.getInt("fromJson1"))
        assertEquals("abc", conf.getString("fromProps.abc"))
    }

    @Test
    def includeFileNoWhitespaceOutsideParens() {
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.parseString("include file (" + jsonQuotedResourceFile("test01") + ")")
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("expecting include parameter"))
    }

    @Test
    def includeFileNotQuoted() {
        val f = resourceFile("test01")
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.parseString("include file(" + f + ")")
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("expecting include file() parameter to be a quoted string"))
    }

    @Test
    def includeFileNotQuotedAndSpecialChar() {
        val f = resourceFile("test01")
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.parseString("include file(:" + f + ")")
        }
        assertTrue("wrong exception: " + e.getMessage,
            e.getMessage.contains("expecting include file() parameter to be a quoted string, rather than: ':'"))
    }

    @Test
    def includeFileUnclosedParens() {
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.parseString("include file(" + jsonQuotedResourceFile("test01") + " something")
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("expecting a close parentheses"))
    }

    @Test
    def includeURLBasename() {
        // "AnySyntax" trick doesn't work for url() includes
        val url = resourceFile("test01").toURI().toURL().toExternalForm()
        val conf = ConfigFactory.parseString("include url(" + quoteJsonString(url) + ")")

        assertTrue("including basename URL doesn't load anything", conf.isEmpty())
    }

    @Test
    def includeURLWithExtension() {
        val url = resourceFile("test01.conf").toURI().toURL().toExternalForm()
        val conf = ConfigFactory.parseString("include url(" + quoteJsonString(url) + ")")

        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertFalse(conf.hasPath("fromJson1"))
        assertFalse(conf.hasPath("fromProps.abc"))
    }

    @Test
    def includeURLInvalid() {
        val e = intercept[ConfigException.Parse] {
            ConfigFactory.parseString("include url(\"junk:junk:junk\")")
        }
        assertTrue("wrong exception: " + e.getMessage, e.getMessage.contains("invalid URL"))
    }

    @Test
    def includeResources() {
        val conf = ConfigFactory.parseString("include classpath(\"test01\")")

        // should have loaded conf, json, properties
        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertEquals(1, conf.getInt("fromJson1"))
        assertEquals("abc", conf.getString("fromProps.abc"))
    }

    @Test
    def includeRequiredMissing() {
        // set this to allowMissing=true to demonstrate that the missing inclusion causes failure despite this setting
        val missing = ConfigParseOptions.defaults().setAllowMissing(true)

        val ex = intercept[Exception] {
            ConfigFactory.parseString("include required(classpath( \"nonexistant\") )", missing)
        }

        val actual = ex.getMessage
        val expected = ".*resource not found on classpath.*"
        assertTrue(s"expected match for <$expected> but got <$actual>", actual.matches(expected))
    }

    @Test
    def includeRequiredFoundButNestedIncludeMissing() {
        // set this to allowMissing=true to demonstrate that the missing nested inclusion is permitted despite this setting
        val missing = ConfigParseOptions.defaults().setAllowMissing(false)

        // test03 has a missing include
        val conf = ConfigFactory.parseString("include required(classpath( \"test03\") )", missing)

        val expected = "This is in the included file"
        val actual = conf.getString("foo")
        assertTrue(s"expected match for <$expected> but got <$actual>", actual.matches(expected))
    }

    @Test
    def includeRequiredFound() {
        val confs = Seq(
            "include required(\"test01\")",
            "include required( \"test01\" )",

            "include required(classpath(\"test01\"))",
            "include required( classpath(\"test01\"))",
            "include required(classpath(\"test01\") )",
            "include required( classpath(\"test01\") )",

            "include required(classpath( \"test01\" ))",
            "include required( classpath( \"test01\" ))",
            "include required(classpath( \"test01\" ) )",
            "include required( classpath( \"test01\" ) )")

        // should have loaded conf, json, properties
        confs.foreach { c =>
            try {
                val conf = ConfigFactory.parseString(c)
                assertEquals(42, conf.getInt("ints.fortyTwo"))
                assertEquals(1, conf.getInt("fromJson1"))
                assertEquals("abc", conf.getString("fromProps.abc"))
            } catch {
                case ex: Exception =>
                    System.err.println(s"failed parsing: $c")
                    throw ex
            }
        }
    }

    @Test
    def includeURLHeuristically() {
        val url = resourceFile("test01.conf").toURI().toURL().toExternalForm()
        val conf = ConfigFactory.parseString("include " + quoteJsonString(url))

        assertEquals(42, conf.getInt("ints.fortyTwo"))
        assertFalse(conf.hasPath("fromJson1"))
        assertFalse(conf.hasPath("fromProps.abc"))
    }

    @Test
    def includeURLBasenameHeuristically() {
        // "AnySyntax" trick doesn't work for url includes
        val url = resourceFile("test01").toURI().toURL().toExternalForm()
        val conf = ConfigFactory.parseString("include " + quoteJsonString(url))

        assertTrue("including basename URL doesn't load anything", conf.isEmpty())
    }

    @Test
    def acceptBOMStartingFile() {
        // BOM at start of file should be ignored
        val conf = ConfigFactory.parseResources("bom.conf")
        assertEquals("bar", conf.getString("foo"))
    }

    @Test
    def acceptBOMStartOfStringConfig() {
        // BOM at start of file is just whitespace, so ignored
        val conf = ConfigFactory.parseString("\uFEFFfoo=bar")
        assertEquals("bar", conf.getString("foo"))
    }

    @Test
    def acceptBOMInStringValue() {
        // BOM inside quotes should be preserved, just as other whitespace would be
        val conf = ConfigFactory.parseString("foo=\"\uFEFF\uFEFF\"")
        assertEquals("\uFEFF\uFEFF", conf.getString("foo"))
    }

    @Test
    def acceptBOMWhitespace() {
        // BOM here should be treated like other whitespace (ignored, since no quotes)
        val conf = ConfigFactory.parseString("foo= \uFEFFbar\uFEFF")
        assertEquals("bar", conf.getString("foo"))
    }

    @Test
    def acceptMultiPeriodNumericPath() {
        val conf1 = ConfigFactory.parseString("0.1.2.3=foobar1")
        assertEquals("foobar1", conf1.getString("0.1.2.3"))
        val conf2 = ConfigFactory.parseString("0.1.2.3.ABC=foobar2")
        assertEquals("foobar2", conf2.getString("0.1.2.3.ABC"))
        val conf3 = ConfigFactory.parseString("ABC.0.1.2.3=foobar3")
        assertEquals("foobar3", conf3.getString("ABC.0.1.2.3"))
    }
}
