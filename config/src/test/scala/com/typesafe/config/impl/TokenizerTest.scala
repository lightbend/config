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
    }

    @Test
    def tokenizeEmptyString() {
        assertEquals(List(Tokens.START, Tokens.END),
            tokenizeAsList(""))
    }

    @Test
    def tokenizeNewlines() {
        assertEquals(List(Tokens.START, tokenLine(1), tokenLine(2), Tokens.END),
            tokenizeAsList("\n\n"))
    }

    @Test
    def tokenizeAllTypesNoSpaces() {
        // all token types with no spaces (not sure JSON spec wants this to work,
        // but spec is unclear to me when spaces are required, and banning them
        // is actually extra work).
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.EQUALS, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.PLUS_EQUALS, tokenString("foo"),
            tokenString("bar"), tokenTrue, tokenDouble(3.14), tokenFalse,
            tokenLong(42), tokenNull, tokenSubstitution(tokenUnquoted("a.b")),
            tokenOptionalSubstitution(tokenUnquoted("x.y")),
            tokenKeySubstitution("c.d"), tokenLine(1), Tokens.END)
        assertEquals(expected, tokenizeAsList(""",:=}{][+="foo"""" + "\"\"\"bar\"\"\"" + """true3.14false42null${a.b}${?x.y}${"c.d"}""" + "\n"))
    }

    @Test
    def tokenizeAllTypesWithSingleSpaces() {
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.EQUALS, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.PLUS_EQUALS, tokenString("foo"),
            tokenUnquoted(" "), tokenString("bar"), tokenUnquoted(" "), tokenLong(42), tokenUnquoted(" "), tokenTrue, tokenUnquoted(" "),
            tokenDouble(3.14), tokenUnquoted(" "), tokenFalse, tokenUnquoted(" "), tokenNull,
            tokenUnquoted(" "), tokenSubstitution(tokenUnquoted("a.b")), tokenUnquoted(" "),
            tokenOptionalSubstitution(tokenUnquoted("x.y")), tokenUnquoted(" "),
            tokenKeySubstitution("c.d"),
            tokenLine(1), Tokens.END)
        assertEquals(expected, tokenizeAsList(""" , : = } { ] [ += "foo" """ + "\"\"\"bar\"\"\"" + """ 42 true 3.14 false null ${a.b} ${?x.y} ${"c.d"} """ + "\n "))
    }

    @Test
    def tokenizeAllTypesWithMultipleSpaces() {
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.EQUALS, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.PLUS_EQUALS, tokenString("foo"),
            tokenUnquoted("   "), tokenString("bar"), tokenUnquoted("   "), tokenLong(42), tokenUnquoted("   "), tokenTrue, tokenUnquoted("   "),
            tokenDouble(3.14), tokenUnquoted("   "), tokenFalse, tokenUnquoted("   "), tokenNull,
            tokenUnquoted("   "), tokenSubstitution(tokenUnquoted("a.b")), tokenUnquoted("   "),
            tokenOptionalSubstitution(tokenUnquoted("x.y")), tokenUnquoted("   "),
            tokenKeySubstitution("c.d"),
            tokenLine(1), Tokens.END)
        assertEquals(expected, tokenizeAsList("""   ,   :   =   }   {   ]   [   +=   "foo"   """ + "\"\"\"bar\"\"\"" + """   42   true   3.14   false   null   ${a.b}   ${?x.y}   ${"c.d"}  """ + "\n   "))
    }

    @Test
    def tokenizeTrueAndUnquotedText() {
        val expected = List(Tokens.START, tokenTrue, tokenUnquoted("foo"), Tokens.END)
        assertEquals(expected, tokenizeAsList("""truefoo"""))
    }

    @Test
    def tokenizeFalseAndUnquotedText() {
        val expected = List(Tokens.START, tokenFalse, tokenUnquoted("foo"), Tokens.END)
        assertEquals(expected, tokenizeAsList("""falsefoo"""))
    }

    @Test
    def tokenizeNullAndUnquotedText() {
        val expected = List(Tokens.START, tokenNull, tokenUnquoted("foo"), Tokens.END)
        assertEquals(expected, tokenizeAsList("""nullfoo"""))
    }

    @Test
    def tokenizeUnquotedTextContainingTrue() {
        val expected = List(Tokens.START, tokenUnquoted("footrue"), Tokens.END)
        assertEquals(expected, tokenizeAsList("""footrue"""))
    }

    @Test
    def tokenizeUnquotedTextContainingSpaceTrue() {
        val expected = List(Tokens.START, tokenUnquoted("foo"), tokenUnquoted(" "), tokenTrue, Tokens.END)
        assertEquals(expected, tokenizeAsList("""foo true"""))
    }

    @Test
    def tokenizeTrueAndSpaceAndUnquotedText() {
        val expected = List(Tokens.START, tokenTrue, tokenUnquoted(" "), tokenUnquoted("foo"), Tokens.END)
        assertEquals(expected, tokenizeAsList("""true foo"""))
    }

    @Test
    def tokenizeUnquotedTextContainingSlash() {
        tokenizerTest(List(tokenUnquoted("a/b/c/")), "a/b/c/")
        tokenizerTest(List(tokenUnquoted("/")), "/")
        tokenizerTest(List(tokenUnquoted("/"), tokenUnquoted(" "), tokenUnquoted("/")), "/ /")
        tokenizerTest(List(tokenComment("")), "//")
    }

    @Test
    def tokenizeUnquotedTextTrimsSpaces() {
        val expected = List(Tokens.START, tokenUnquoted("foo"), tokenLine(1), Tokens.END)
        assertEquals(expected, tokenizeAsList("    foo     \n"))
    }

    @Test
    def tokenizeUnquotedTextKeepsInternalSpaces() {
        val expected = List(Tokens.START, tokenUnquoted("foo"), tokenUnquoted("  "), tokenUnquoted("bar"),
            tokenUnquoted(" "), tokenUnquoted("baz"), tokenLine(1), Tokens.END)
        assertEquals(expected, tokenizeAsList("    foo  bar baz   \n"))
    }

    @Test
    def tokenizeMixedUnquotedQuoted() {
        val expected = List(Tokens.START, tokenUnquoted("foo"),
            tokenString("bar"), tokenUnquoted("baz"),
            tokenLine(1), Tokens.END)
        assertEquals(expected, tokenizeAsList("    foo\"bar\"baz   \n"))
    }

    @Test
    def tokenizerUnescapeStrings(): Unit = {
        case class UnescapeTest(escaped: String, result: ConfigString)
        implicit def pair2unescapetest(pair: (String, String)): UnescapeTest = UnescapeTest(pair._1, new ConfigString(fakeOrigin(), pair._2))

        // getting the actual 6 chars we want in a string is a little pesky.
        // \u005C is backslash. Just prove we're doing it right here.
        assertEquals(6, "\\u0046".length)
        assertEquals('4', "\\u0046"(4))
        assertEquals('6', "\\u0046"(5))

        val tests = List[UnescapeTest]((""" "" """, ""),
            (" \"\\u0000\" ", Character.toString(0)), // nul byte
            (""" "\"\\\/\b\f\n\r\t" """, "\"\\/\b\f\n\r\t"),
            ("\"\\u0046\"", "F"),
            ("\"\\u0046\\u0046\"", "FF"))

        for (t <- tests) {
            describeFailure(t.toString) {
                assertEquals(List(Tokens.START, Tokens.newValue(t.result), Tokens.END),
                    tokenizeAsList(t.escaped))
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
            val maybeProblem = tokenized.find(Tokens.isProblem(_))
            assertTrue(maybeProblem.isDefined)
        }
    }

    @Test
    def tokenizerEmptyTripleQuoted(): Unit = {
        assertEquals(List(Tokens.START, tokenString(""), Tokens.END),
            tokenizeAsList("\"\"\"\"\"\""))
    }

    @Test
    def tokenizerTrivialTripleQuoted(): Unit = {
        assertEquals(List(Tokens.START, tokenString("bar"), Tokens.END),
            tokenizeAsList("\"\"\"bar\"\"\""))
    }

    @Test
    def tokenizerNoEscapesInTripleQuoted(): Unit = {
        assertEquals(List(Tokens.START, tokenString("\\n"), Tokens.END),
            tokenizeAsList("\"\"\"\\n\"\"\""))
    }

    @Test
    def tokenizerTrailingQuotesInTripleQuoted(): Unit = {
        assertEquals(List(Tokens.START, tokenString("\"\"\""), Tokens.END),
            tokenizeAsList("\"\"\"\"\"\"\"\"\""))
    }

    @Test
    def tokenizerNewlineInTripleQuoted(): Unit = {
        assertEquals(List(Tokens.START, tokenString("foo\nbar"), Tokens.END),
            tokenizeAsList("\"\"\"foo\nbar\"\"\""))
    }

    @Test
    def tokenizerParseNumbers(): Unit = {
        abstract class NumberTest(val s: String, val result: Token)
        case class LongTest(override val s: String, override val result: Token) extends NumberTest(s, result)
        case class DoubleTest(override val s: String, override val result: Token) extends NumberTest(s, result)
        implicit def pair2inttest(pair: (String, Int)) = LongTest(pair._1, tokenLong(pair._2))
        implicit def pair2longtest(pair: (String, Long)) = LongTest(pair._1, tokenLong(pair._2))
        implicit def pair2doubletest(pair: (String, Double)) = DoubleTest(pair._1, tokenDouble(pair._2))

        val tests = List[NumberTest](("1", 1),
            ("1.2", 1.2),
            ("1e6", 1e6),
            ("1e-6", 1e-6),
            ("1E-6", 1e-6), // capital E is allowed
            ("-1", -1),
            ("-1.2", -1.2))

        for (t <- tests) {
            describeFailure(t.toString()) {
                assertEquals(List(Tokens.START, t.result, Tokens.END),
                    tokenizeAsList(t.s))
            }
        }
    }

    @Test
    def commentsHandledInVariousContexts() {
        tokenizerTest(List(tokenString("//bar")), "\"//bar\"")
        tokenizerTest(List(tokenString("#bar")), "\"#bar\"")
        tokenizerTest(List(tokenUnquoted("bar"), tokenComment("comment")), "bar//comment")
        tokenizerTest(List(tokenUnquoted("bar"), tokenComment("comment")), "bar#comment")
        tokenizerTest(List(tokenInt(10), tokenComment("comment")), "10//comment")
        tokenizerTest(List(tokenInt(10), tokenComment("comment")), "10#comment")
        tokenizerTest(List(tokenDouble(3.14), tokenComment("comment")), "3.14//comment")
        tokenizerTest(List(tokenDouble(3.14), tokenComment("comment")), "3.14#comment")
        // be sure we keep the newline
        tokenizerTest(List(tokenInt(10), tokenComment("comment"), tokenLine(1), tokenInt(12)), "10//comment\n12")
        tokenizerTest(List(tokenInt(10), tokenComment("comment"), tokenLine(1), tokenInt(12)), "10#comment\n12")
    }

    @Test
    def tokenizeReservedChars() {
        for (invalid <- "+`^?!@*&\\") {
            val tokenized = tokenizeAsList(invalid.toString)
            assertEquals(3, tokenized.size)
            assertEquals(Tokens.START, tokenized(0))
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
