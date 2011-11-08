package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import net.liftweb.{ json => lift }
import java.io.Reader
import java.io.StringReader
import com.typesafe.config._
import java.util.HashMap

class TokenizerTest extends TestUtils {

    @org.junit.Before
    def setup() {
    }

    def tokenTrue = Tokens.newBoolean(fakeOrigin(), true)
    def tokenFalse = Tokens.newBoolean(fakeOrigin(), false)
    def tokenNull = Tokens.newNull(fakeOrigin())
    def tokenUnquoted(s: String) = Tokens.newUnquotedText(fakeOrigin(), s)
    def tokenKeySubstitution(s: String) = Tokens.newSubstitution(fakeOrigin(), s, true /* wasQuoted */ )
    def tokenPathSubstitution(s: String) = Tokens.newSubstitution(fakeOrigin(), s, false /* wasQuoted */ )
    def tokenString(s: String) = Tokens.newString(fakeOrigin(), s)
    def tokenDouble(d: Double) = Tokens.newDouble(fakeOrigin(), d)
    def tokenInt(i: Int) = Tokens.newInt(fakeOrigin(), i)
    def tokenLong(l: Long) = Tokens.newLong(fakeOrigin(), l)

    def tokenize(origin: ConfigOrigin, input: Reader): java.util.Iterator[Token] = {
        Tokenizer.tokenize(origin, input)
    }

    def tokenize(input: Reader): java.util.Iterator[Token] = {
        tokenize(new SimpleConfigOrigin("anonymous Reader"), input)
    }

    def tokenize(s: String): java.util.Iterator[Token] = {
        val reader = new StringReader(s)
        val result = tokenize(reader)
        // reader.close() // can't close until the iterator is traversed, so this tokenize() flavor is inherently broken
        result
    }

    def tokenizeAsList(s: String) = {
        import scala.collection.JavaConverters._
        tokenize(s).asScala.toList
    }

    @Test
    def tokenizeEmptyString() {
        assertEquals(List(Tokens.START, Tokens.END),
            tokenizeAsList(""))
    }

    @Test
    def tokenizeAllTypesNoSpaces() {
        // all token types with no spaces (not sure JSON spec wants this to work,
        // but spec is unclear to me when spaces are required, and banning them
        // is actually extra work).
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, tokenString("foo"),
            tokenTrue, tokenDouble(3.14), tokenFalse,
            tokenLong(42), tokenNull, tokenPathSubstitution("a.b"),
            tokenKeySubstitution("c.d"), Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList(""",:}{]["foo"true3.14false42null${a.b}${"c.d"}""" + "\n"))
    }

    @Test
    def tokenizeAllTypesWithSingleSpaces() {
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, tokenString("foo"),
            tokenUnquoted(" "), tokenLong(42), tokenUnquoted(" "), tokenTrue, tokenUnquoted(" "),
            tokenDouble(3.14), tokenUnquoted(" "), tokenFalse, tokenUnquoted(" "), tokenNull,
            tokenUnquoted(" "), tokenPathSubstitution("a.b"), tokenUnquoted(" "), tokenKeySubstitution("c.d"),
            Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList(""" , : } { ] [ "foo" 42 true 3.14 false null ${a.b} ${"c.d"} """ + "\n "))
    }

    @Test
    def tokenizeAllTypesWithMultipleSpaces() {
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, tokenString("foo"),
            tokenUnquoted("   "), tokenLong(42), tokenUnquoted("   "), tokenTrue, tokenUnquoted("   "),
            tokenDouble(3.14), tokenUnquoted("   "), tokenFalse, tokenUnquoted("   "), tokenNull,
            tokenUnquoted("   "), tokenPathSubstitution("a.b"), tokenUnquoted("   "),
            tokenKeySubstitution("c.d"),
            Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList("""   ,   :   }   {   ]   [   "foo"   42   true   3.14   false   null   ${a.b}   ${"c.d"}  """ + "\n   "))
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
    def tokenizeUnquotedTextTrimsSpaces() {
        val expected = List(Tokens.START, tokenUnquoted("foo"), Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList("    foo     \n"))
    }

    @Test
    def tokenizeUnquotedTextKeepsInternalSpaces() {
        val expected = List(Tokens.START, tokenUnquoted("foo"), tokenUnquoted("  "), tokenUnquoted("bar"),
            tokenUnquoted(" "), tokenUnquoted("baz"), Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList("    foo  bar baz   \n"))
    }

    @Test
    def tokenizeMixedUnquotedQuoted() {
        val expected = List(Tokens.START, tokenUnquoted("foo"),
            tokenString("bar"), tokenUnquoted("baz"),
            Tokens.newLine(0), Tokens.END)
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
            (" \"\0\" ", "\0"), // nul byte
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
    def tokenizerThrowsOnInvalidStrings(): Unit = {
        val invalidTests = List(""" "\" """, // nothing after a backslash
            """ "\q" """, // there is no \q escape sequence
            "\"\\u123\"", // too short
            "\"\\u12\"", // too short
            "\"\\u1\"", // too short
            "\"\\u\"", // too short
            "\"", // just a single quote
            """ "abcdefg""" // no end quote
            )

        for (t <- invalidTests) {
            describeFailure(t) {
                intercept[ConfigException] {
                    tokenizeAsList(t)
                }
            }
        }
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
            ("-1", -1),
            ("-1.2", -1.2))

        for (t <- tests) {
            describeFailure(t.toString()) {
                assertEquals(List(Tokens.START, t.result, Tokens.END),
                    tokenizeAsList(t.s))
            }
        }
    }
}
