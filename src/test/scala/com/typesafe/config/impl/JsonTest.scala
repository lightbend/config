package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import net.liftweb.{ json => lift }
import java.io.Reader
import java.io.StringReader
import com.typesafe.config._
import java.util.HashMap

class JsonTest extends TestUtils {

    @org.junit.Before
    def setup() {
    }

    def tokenize(origin: ConfigOrigin, input: Reader): java.util.Iterator[Token] = {
        Tokenizer.tokenize(origin, input)
    }

    def tokenize(input: Reader): java.util.Iterator[Token] = {
        tokenize(new SimpleConfigOrigin("anonymous Reader"), input)
    }

    def tokenize(s: String): java.util.Iterator[Token] = {
        tokenize(new StringReader(s))
    }

    def tokenizeAsList(s: String) = {
        import scala.collection.JavaConverters._
        tokenize(s).asScala.toList
    }

    def parse(s: String): ConfigValue = {
        Parser.parse(SyntaxFlavor.JSON, new SimpleConfigOrigin("test string"), s)
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
        // is actually extra work)
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.newString(fakeOrigin(), "foo"),
            Tokens.newLong(fakeOrigin(), 42), Tokens.newBoolean(fakeOrigin(), true), Tokens.newDouble(fakeOrigin(), 3.14),
            Tokens.newBoolean(fakeOrigin(), false), Tokens.newNull(fakeOrigin()), Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList(""",:}{]["foo"42true3.14falsenull""" + "\n"))
    }

    @Test
    def tokenizeAllTypesWithSingleSpaces() {
        // all token types with no spaces (not sure JSON spec wants this to work,
        // but spec is unclear to me when spaces are required, and banning them
        // is actually extra work)
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.newString(fakeOrigin(), "foo"),
            Tokens.newLong(fakeOrigin(), 42), Tokens.newBoolean(fakeOrigin(), true), Tokens.newDouble(fakeOrigin(), 3.14),
            Tokens.newBoolean(fakeOrigin(), false), Tokens.newNull(fakeOrigin()), Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList(""" , : } { ] [ "foo" 42 true 3.14 false null """ + "\n "))
    }

    @Test
    def tokenizeAllTypesWithMultipleSpaces() {
        // all token types with no spaces (not sure JSON spec wants this to work,
        // but spec is unclear to me when spaces are required, and banning them
        // is actually extra work)
        val expected = List(Tokens.START, Tokens.COMMA, Tokens.COLON, Tokens.CLOSE_CURLY,
            Tokens.OPEN_CURLY, Tokens.CLOSE_SQUARE, Tokens.OPEN_SQUARE, Tokens.newString(fakeOrigin(), "foo"),
            Tokens.newLong(fakeOrigin(), 42), Tokens.newBoolean(fakeOrigin(), true), Tokens.newDouble(fakeOrigin(), 3.14),
            Tokens.newBoolean(fakeOrigin(), false), Tokens.newNull(fakeOrigin()), Tokens.newLine(0), Tokens.END)
        assertEquals(expected, tokenizeAsList("""   ,   :   }   {   ]   [   "foo"   42   true   3.14   false   null   """ + "\n   "))
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
        implicit def pair2inttest(pair: (String, Int)) = LongTest(pair._1, Tokens.newLong(fakeOrigin(), pair._2))
        implicit def pair2longtest(pair: (String, Long)) = LongTest(pair._1, Tokens.newLong(fakeOrigin(), pair._2))
        implicit def pair2doubletest(pair: (String, Double)) = DoubleTest(pair._1, Tokens.newDouble(fakeOrigin(), pair._2))

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

    private[this] def toLift(value: ConfigValue): lift.JValue = {
        import scala.collection.JavaConverters._

        value match {
            case v: ConfigObject =>
                lift.JObject(v.keySet().asScala.map({ k => lift.JField(k, toLift(v.get(k))) }).toList)
            case v: ConfigList =>
                lift.JArray(v.asJavaList().asScala.toList.map({ elem => toLift(elem) }))
            case v: ConfigBoolean =>
                lift.JBool(v.unwrapped())
            case v: ConfigInt =>
                lift.JInt(BigInt(v.unwrapped()))
            case v: ConfigLong =>
                lift.JInt(BigInt(v.unwrapped()))
            case v: ConfigDouble =>
                lift.JDouble(v.unwrapped())
            case v: ConfigString =>
                lift.JString(v.unwrapped())
            case v: ConfigNull =>
                lift.JNull
        }
    }

    private[this] def fromLift(liftValue: lift.JValue): ConfigValue = {
        import scala.collection.JavaConverters._

        liftValue match {
            case lift.JObject(fields) =>
                val m = new HashMap[String, ConfigValue]()
                fields.foreach({ field => m.put(field.name, fromLift(field.value)) })
                new SimpleConfigObject(fakeOrigin(), null, m)
            case lift.JArray(values) =>
                new ConfigList(fakeOrigin(), values.map(fromLift(_)).asJava)
            case lift.JField(name, value) =>
                throw new IllegalStateException("either JField was a toplevel from lift-json or this function is buggy")
            case lift.JInt(i) =>
                if (i.isValidInt) new ConfigInt(fakeOrigin(), i.intValue) else new ConfigLong(fakeOrigin(), i.longValue)
            case lift.JBool(b) =>
                new ConfigBoolean(fakeOrigin(), b)
            case lift.JDouble(d) =>
                new ConfigDouble(fakeOrigin(), d)
            case lift.JString(s) =>
                new ConfigString(fakeOrigin(), s)
            case lift.JNull =>
                new ConfigNull(fakeOrigin())
            case lift.JNothing =>
                throw new ConfigException.BugOrBroken("Lift returned JNothing, probably an empty document (?)")
        }
    }

    private def withLiftExceptionsConverted[T](block: => T): T = {
        try {
            block
        } catch {
            case e: lift.JsonParser.ParseException =>
                throw new ConfigException.Parse(new SimpleConfigOrigin("lift parser"), e.getMessage(), e)
        }
    }

    // parse a string using Lift's AST. We then test by ensuring we have the same results as
    // lift for a variety of JSON strings.

    private def fromJsonWithLiftParser(json: String): ConfigValue = {
        withLiftExceptionsConverted(fromLift(lift.JsonParser.parse(json)))
    }

    private def fromJsonWithLiftParser(json: Reader): ConfigValue = {
        withLiftExceptionsConverted(fromLift(lift.JsonParser.parse(json)))
    }

    case class JsonTest(liftBehaviorUnexpected: Boolean, test: String)
    implicit def string2jsontest(test: String): JsonTest = JsonTest(false, test)

    private val invalidJson = List[JsonTest]("", // empty document
        "{",
        "}",
        "[",
        "]",
        "10", // value not in array or object
        "\"foo\"", // value not in array or object
        "\"", // single quote by itself
        "{ \"foo\" : }", // no value in object
        "{ : 10 }", // no key in object
        // these two problems are ignored by the lift tokenizer
        "[:\"foo\", \"bar\"]", // colon in an array; lift doesn't throw (tokenizer erases it)
        "[\"foo\" : \"bar\"]", // colon in an array another way, lift ignores (tokenizer erases it)
        "[ foo ]", // not a known token
        "[ t ]", // start of "true" but ends wrong
        "[ tx ]",
        "[ tr ]",
        "[ trx ]",
        "[ tru ]",
        "[ trux ]",
        "[ truex ]",
        "[ 10x ]", // number token with trailing junk
        "[ 10e3e3 ]", // two exponents
        "[ \"hello ]", // unterminated string
        JsonTest(true, "{ \"foo\" , true }"), // comma instead of colon, lift is fine with this
        JsonTest(true, "{ \"foo\" : true \"bar\" : false }"), // missing comma between fields, lift fine with this
        "[ 10, }]", // array with } as an element
        "[ 10, {]", // array with { as an element
        "{}x", // trailing invalid token after the root object
        "[]x", // trailing invalid token after the root array
        JsonTest(true, "{}{}"), // trailing token after the root object - lift OK with it
        "{}true", // trailing token after the root object
        JsonTest(true, "[]{}"), // trailing valid token after the root array
        "[]true", // trailing valid token after the root array
        "") // empty document again, just for clean formatting of this list ;-)

    // We'll automatically try each of these with whitespace modifications
    // so no need to add every possible whitespace variation
    private val validJson = List[JsonTest]("{}",
        "[]",
        """{ "foo" : "bar" }""",
        """["foo", "bar"]""",
        """{ "foo" : 42 }""",
        """[10, 11]""",
        """[10,"foo"]""",
        """{ "foo" : "bar", "baz" : "boo" }""",
        """{ "foo" : { "bar" : "baz" }, "baz" : "boo" }""",
        """{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : "boo" }""",
        """{ "foo" : [10,11,12], "baz" : "boo" }""",
        JsonTest(true, """{ "foo" : "bar", "foo" : "bar2" }"""), // dup keys - lift just returns both, we use last one
        """[{},{},{},{}]""",
        """[[[[[[]]]]]]""",
        """{"a":{"a":{"a":{"a":{"a":{"a":{"a":{"a":42}}}}}}}}""",
        // this long one is mostly to test rendering
        """{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : { "bar" : "baz", "woo" : [1,2,3,4], "w00t" : true, "a" : false, "b" : 3.14, "c" : null } }""",
        "{}")

    // For string quoting, check behavior of escaping a random character instead of one on the list;
    // lift-json seems to oddly treat that as a \ literal

    private def whitespaceVariations(tests: Seq[JsonTest]): Seq[JsonTest] = {
        val variations = List({ s: String => s }, // identity
            { s: String => " " + s },
            { s: String => s + " " },
            { s: String => " " + s + " " },
            { s: String => s.replace(" ", "") }, // this would break with whitespace in a key or value
            { s: String => s.replace(":", " : ") }, // could break with : in a key or value
            { s: String => s.replace(",", " , ") } // could break with , in a key or value
            )
        for {
            t <- tests
            v <- variations
        } yield JsonTest(t.liftBehaviorUnexpected, v(t.test))
    }

    private def addOffendingJsonToException[R](parserName: String, s: String)(body: => R) = {
        try {
            body
        } catch {
            case t: Throwable =>
                throw new AssertionError(parserName + " parser failed on '" + s + "'", t)
        }
    }

    @Test
    def invalidJsonThrows(): Unit = {
        // be sure Lift throws on the string
        for (invalid <- whitespaceVariations(invalidJson)) {
            if (invalid.liftBehaviorUnexpected) {
                // lift unexpectedly doesn't throw, confirm that
                fromJsonWithLiftParser(invalid.test)
                fromJsonWithLiftParser(new java.io.StringReader(invalid.test))
            } else {
                addOffendingJsonToException("lift", invalid.test) {
                    intercept[ConfigException] {
                        fromJsonWithLiftParser(invalid.test)
                    }
                    intercept[ConfigException] {
                        fromJsonWithLiftParser(new java.io.StringReader(invalid.test))
                    }
                }
            }
        }
        // be sure we also throw
        for (invalid <- whitespaceVariations(invalidJson)) {
            addOffendingJsonToException("config", invalid.test) {
                intercept[ConfigException] {
                    parse(invalid.test)
                }
            }
        }
    }

    @Test
    def validJsonWorks(): Unit = {
        // be sure we do the same thing as Lift when we build our JSON "DOM"
        for (valid <- whitespaceVariations(validJson)) {
            val liftAST = addOffendingJsonToException("lift", valid.test) {
                fromJsonWithLiftParser(valid.test)
            }
            val ourAST = addOffendingJsonToException("config", valid.test) {
                parse(valid.test)
            }
            if (valid.liftBehaviorUnexpected) {
                // ignore this for now
            } else {
                addOffendingJsonToException("config", valid.test) {
                    assertEquals(liftAST, ourAST)
                }
            }
        }
    }
}
