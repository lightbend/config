/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit.Test
import com.typesafe.config.ConfigException
import language.implicitConversions

class TokenizerTest extends TestUtils {

    // FIXME most of this file should be using this method
    private def tokenizerTest(expected: List[Token], s: String) {
        assertEquals(List(Tokens.START) ++ expected ++ List(Tokens.END),
            tokenizeAsList(s))
        assertEquals(s, tokenizeAsString(s))
    }

    @Test
    def tokenizeEmptyString() {
        val source = ""
        val expected = List()
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeNewlines() {
        val source = "\n\n"
        val expected = List(tokenLine(1), tokenLine(2))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeAllTypesNoSpaces() {
        // all token types with no spaces (not sure JSON spec wants this to work,
        // but spec is unclear to me when spaces are required, and banning them
        // is actually extra work).
        val source = """,:=}{][+="foo"""" + "\"\"\"bar\"\"\"" + """true3.14false42null${a.b}${?x.y}${"c.d"}""" + "\n"
        val expected = List(Tokens.COMMA, Tokens.COLON, Tokens.EQUALS, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.PLUS_EQUALS, tokenString("foo"),
            tokenString("bar"), tokenTrue, tokenDouble(3.14), tokenFalse,
            tokenLong(42), tokenNull, tokenSubstitution(tokenUnquoted("a.b")),
            tokenOptionalSubstitution(tokenUnquoted("x.y")),
            tokenKeySubstitution("c.d"), tokenLine(1))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeAllTypesWithSingleSpaces() {
        val source = """ , : = } { ] [ += "foo" """ + "\"\"\"bar\"\"\"" + """ 42 true 3.14 false null ${a.b} ${?x.y} ${"c.d"} """ + "\n "
        val expected = List(tokenWhitespace(" "), Tokens.COMMA, tokenWhitespace(" "), Tokens.COLON, tokenWhitespace(" "),
            Tokens.EQUALS, tokenWhitespace(" "), Tokens.CLOSE_CURLY, tokenWhitespace(" "), Tokens.OPEN_CURLY, tokenWhitespace(" "),
            Tokens.CLOSE_SQUARE, tokenWhitespace(" "), Tokens.OPEN_SQUARE, tokenWhitespace(" "), Tokens.PLUS_EQUALS, tokenWhitespace(" "),
            tokenString("foo"), tokenUnquoted(" "), tokenString("bar"), tokenUnquoted(" "), tokenLong(42), tokenUnquoted(" "),
            tokenTrue, tokenUnquoted(" "), tokenDouble(3.14), tokenUnquoted(" "), tokenFalse, tokenUnquoted(" "), tokenNull,
            tokenUnquoted(" "), tokenSubstitution(tokenUnquoted("a.b")), tokenUnquoted(" "),
            tokenOptionalSubstitution(tokenUnquoted("x.y")), tokenUnquoted(" "),
            tokenKeySubstitution("c.d"), tokenWhitespace(" "),
            tokenLine(1), tokenWhitespace(" "))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeAllTypesWithMultipleSpaces() {
        val source = """   ,   :   =   }   {   ]   [   +=   "foo"   """ + "\"\"\"bar\"\"\"" + """   42   true   3.14   false   null   ${a.b}   ${?x.y}   ${"c.d"}  """ + "\n   "
        val expected = List(tokenWhitespace("   "), Tokens.COMMA, tokenWhitespace("   "), Tokens.COLON, tokenWhitespace("   "),
            Tokens.EQUALS, tokenWhitespace("   "), Tokens.CLOSE_CURLY, tokenWhitespace("   "), Tokens.OPEN_CURLY, tokenWhitespace("   "), Tokens.CLOSE_SQUARE,
            tokenWhitespace("   "), Tokens.OPEN_SQUARE, tokenWhitespace("   "), Tokens.PLUS_EQUALS, tokenWhitespace("   "), tokenString("foo"),
            tokenUnquoted("   "), tokenString("bar"), tokenUnquoted("   "), tokenLong(42), tokenUnquoted("   "), tokenTrue, tokenUnquoted("   "),
            tokenDouble(3.14), tokenUnquoted("   "), tokenFalse, tokenUnquoted("   "), tokenNull,
            tokenUnquoted("   "), tokenSubstitution(tokenUnquoted("a.b")), tokenUnquoted("   "),
            tokenOptionalSubstitution(tokenUnquoted("x.y")), tokenUnquoted("   "),
            tokenKeySubstitution("c.d"), tokenWhitespace("  "),
            tokenLine(1), tokenWhitespace("   "))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeTrueAndUnquotedText() {
        val source = """truefoo"""
        val expected = List(tokenTrue, tokenUnquoted("foo"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeFalseAndUnquotedText() {
        val source = """falsefoo"""
        val expected = List(tokenFalse, tokenUnquoted("foo"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeNullAndUnquotedText() {
        val source = """nullfoo"""
        val expected = List(tokenNull, tokenUnquoted("foo"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeUnquotedTextContainingRoundBrace() {
        val source = """(footrue)"""
        val expected = List(tokenUnquoted("(footrue)"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeUnquotedTextContainingTrue() {
        val source = """footrue"""
        val expected = List(tokenUnquoted("footrue"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeUnquotedTextContainingSpaceTrue() {
        val source = """foo true"""
        val expected = List(tokenUnquoted("foo"), tokenUnquoted(" "), tokenTrue)
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeTrueAndSpaceAndUnquotedText() {
        val source = """true foo"""
        val expected = List(tokenTrue, tokenUnquoted(" "), tokenUnquoted("foo"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeUnquotedTextContainingSlash() {
        tokenizerTest(List(tokenUnquoted("a/b/c/")), "a/b/c/")
        tokenizerTest(List(tokenUnquoted("/")), "/")
        tokenizerTest(List(tokenUnquoted("/"), tokenUnquoted(" "), tokenUnquoted("/")), "/ /")
        tokenizerTest(List(tokenCommentDoubleSlash("")), "//")
    }

    @Test
    def tokenizeUnquotedTextKeepsSpaces() {
        val source = "    foo     \n"
        val expected = List(tokenWhitespace("    "), tokenUnquoted("foo"), tokenWhitespace("     "),
            tokenLine(1))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeUnquotedTextKeepsInternalSpaces() {
        val source = "    foo  bar baz   \n"
        val expected = List(tokenWhitespace("    "), tokenUnquoted("foo"), tokenUnquoted("  "),
            tokenUnquoted("bar"), tokenUnquoted(" "), tokenUnquoted("baz"), tokenWhitespace("   "),
            tokenLine(1))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizeMixedUnquotedQuoted() {
        val source = "    foo\"bar\"baz   \n"
        val expected = List(tokenWhitespace("    "), tokenUnquoted("foo"),
            tokenString("bar"), tokenUnquoted("baz"), tokenWhitespace("   "),
            tokenLine(1))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizerUnescapeStrings(): Unit = {
        case class UnescapeTest(escaped: String, result: ConfigString)
        implicit def pair2unescapetest(pair: (String, String)): UnescapeTest = UnescapeTest(pair._1, new ConfigString.Quoted(fakeOrigin(), pair._2))

        // getting the actual 6 chars we want in a string is a little pesky.
        // \u005C is backslash. Just prove we're doing it right here.
        assertEquals(6, "\\u0046".length)
        assertEquals('4', "\\u0046"(4))
        assertEquals('6', "\\u0046"(5))

        val tests = List[UnescapeTest]((""" "" """, ""),
            (" \"\\u0000\" ", Character.toString(0)), // nul byte
            (""" "\"\\\/\b\f\n\r\t" """, "\"\\/\b\f\n\r\t"),
            (" \"\\u0046\" ", "F"),
            (" \"\\u0046\\u0046\" ", "FF"))

        for (t <- tests) {
            describeFailure(t.toString) {
                val expected = List(tokenWhitespace(" "), Tokens.newValue(t.result, t.toString),
                    tokenWhitespace(" "))
                tokenizerTest(expected, t.escaped)
            }
        }
    }

    @Test
    def tokenizerReturnsProblemOnInvalidStrings(): Unit = {
        val invalidTests = List(""" "\" """, // nothing after a backslash
            """ "\q" """, // there is no \q escape sequence
            "\"\\u123\"", // too short
            "\"\\u12\"", // too short
            "\"\\u1\"", // too short
            "\"\\u\"", // too short
            "\"", // just a single quote
            """ "abcdefg""", // no end quote
            """\"\""", // file ends with a backslash
            "$", // file ends with a $
            "${" // file ends with a ${
        )

        for (t <- invalidTests) {
            val tokenized = tokenizeAsList(t)
            val maybeProblem = tokenized.find(Tokens.isProblem)
            assertTrue(s"expected failure for <$t> but got $t", maybeProblem.isDefined)
        }
    }

    @Test
    def tokenizerEmptyTripleQuoted(): Unit = {
        val source = "\"\"\"\"\"\""
        val expected = List(tokenString(""))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizerTrivialTripleQuoted(): Unit = {
        val source = "\"\"\"bar\"\"\""
        val expected = List(tokenString("bar"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizerNoEscapesInTripleQuoted(): Unit = {
        val source = "\"\"\"\\n\"\"\""
        val expected = List(tokenString("\\n"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizerTrailingQuotesInTripleQuoted(): Unit = {
        val source = "\"\"\"\"\"\"\"\"\""
        val expected = List(tokenString("\"\"\""))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizerNewlineInTripleQuoted(): Unit = {
        val source = "\"\"\"foo\nbar\"\"\""
        val expected = List(tokenString("foo\nbar"))
        tokenizerTest(expected, source)
    }

    @Test
    def tokenizerParseNumbers(): Unit = {
        abstract class NumberTest(val s: String, val result: Token)
        case class LongTest(override val s: String, override val result: Token) extends NumberTest(s, result)
        case class DoubleTest(override val s: String, override val result: Token) extends NumberTest(s, result)
        implicit def pair2inttest(pair: (String, Int)): LongTest = LongTest(pair._1, tokenLong(pair._2))
        implicit def pair2longtest(pair: (String, Long)): LongTest = LongTest(pair._1, tokenLong(pair._2))
        implicit def pair2doubletest(pair: (String, Double)): DoubleTest = DoubleTest(pair._1, tokenDouble(pair._2))

        val tests = List[NumberTest](("1", 1),
            ("1.2", 1.2),
            ("1e6", 1e6),
            ("1e-6", 1e-6),
            ("1E-6", 1e-6), // capital E is allowed
            ("-1", -1),
            ("-1.2", -1.2))

        for (t <- tests) {
            describeFailure(t.toString()) {
                val expected = List(t.result)
                tokenizerTest(expected, t.s)
            }
        }
    }

    @Test
    def commentsHandledInVariousContexts() {
        tokenizerTest(List(tokenString("//bar")), "\"//bar\"")
        tokenizerTest(List(tokenString("#bar")), "\"#bar\"")
        tokenizerTest(List(tokenUnquoted("bar"), tokenCommentDoubleSlash("comment")), "bar//comment")
        tokenizerTest(List(tokenUnquoted("bar"), tokenCommentHash("comment")), "bar#comment")
        tokenizerTest(List(tokenInt(10), tokenCommentDoubleSlash("comment")), "10//comment")
        tokenizerTest(List(tokenInt(10), tokenCommentHash("comment")), "10#comment")
        tokenizerTest(List(tokenDouble(3.14), tokenCommentDoubleSlash("comment")), "3.14//comment")
        tokenizerTest(List(tokenDouble(3.14), tokenCommentHash("comment")), "3.14#comment")
        // be sure we keep the newline
        tokenizerTest(List(tokenInt(10), tokenCommentDoubleSlash("comment"), tokenLine(1), tokenInt(12)), "10//comment\n12")
        tokenizerTest(List(tokenInt(10), tokenCommentHash("comment"), tokenLine(1), tokenInt(12)), "10#comment\n12")
        // be sure we handle multi-line comments
        tokenizerTest(List(tokenCommentDoubleSlash("comment"), tokenLine(1), tokenCommentDoubleSlash("comment2")),
            "//comment\n//comment2")
        tokenizerTest(List(tokenCommentHash("comment"), tokenLine(1), tokenCommentHash("comment2")),
            "#comment\n#comment2")
        tokenizerTest(List(tokenWhitespace("        "), tokenCommentDoubleSlash("comment\r"),
            tokenLine(1), tokenWhitespace("        "), tokenCommentDoubleSlash("comment2        "),
            tokenLine(2), tokenCommentDoubleSlash("comment3        "),
            tokenLine(3), tokenLine(4), tokenCommentDoubleSlash("comment4")),
            "        //comment\r\n        //comment2        \n//comment3        \n\n//comment4")
        tokenizerTest(List(tokenWhitespace("        "), tokenCommentHash("comment\r"),
            tokenLine(1), tokenWhitespace("        "), tokenCommentHash("comment2        "),
            tokenLine(2), tokenCommentHash("comment3        "),
            tokenLine(3), tokenLine(4), tokenCommentHash("comment4")),
            "        #comment\r\n        #comment2        \n#comment3        \n\n#comment4")
    }

    @Test
    def tokenizeReservedChars() {
        for (invalid <- "+`^?!@*&\\") {
            val tokenized = tokenizeAsList(invalid.toString)
            assertEquals(3, tokenized.size)
            assertEquals(Tokens.START, tokenized.head)
            assertEquals(Tokens.END, tokenized(2))
            val problem = tokenized(1)
            assertTrue("reserved char is a problem", Tokens.isProblem(problem))
            if (invalid == '+')
                assertEquals("end of file", Tokens.getProblemWhat(problem))
            else
                assertEquals("" + invalid, Tokens.getProblemWhat(problem))
        }
    }
}
