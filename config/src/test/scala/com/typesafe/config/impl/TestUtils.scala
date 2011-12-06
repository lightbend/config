/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.ConfigOrigin
import java.io.Reader
import java.io.StringReader
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.Config
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigFactory
import java.io.File

abstract trait TestUtils {
    protected def intercept[E <: Throwable: Manifest](block: => Unit): E = {
        val expectedClass = manifest.erasure.asInstanceOf[Class[E]]
        var thrown: Option[Throwable] = None
        try {
            block
        } catch {
            case t: Throwable => thrown = Some(t)
        }
        thrown match {
            case Some(t) if expectedClass.isAssignableFrom(t.getClass) =>
                t.asInstanceOf[E]
            case Some(t) =>
                throw new Exception("Expected exception %s was not thrown, got %s".format(expectedClass.getName, t), t)
            case None =>
                throw new Exception("Expected exception %s was not thrown, no exception was thrown".format(expectedClass.getName))
        }
    }

    protected def describeFailure[A](desc: String)(code: => A): A = {
        try {
            code
        } catch {
            case t: Throwable =>
                println("Failure on: '%s'".format(desc))
                throw t
        }
    }

    private class NotEqualToAnythingElse {
        override def equals(other: Any) = {
            other match {
                case x: NotEqualToAnythingElse => true
                case _ => false
            }
        }

        override def hashCode() = 971
    }

    private object notEqualToAnything extends NotEqualToAnythingElse

    private def checkNotEqualToRandomOtherThing(a: Any) {
        assertFalse(a.equals(notEqualToAnything))
        assertFalse(notEqualToAnything.equals(a))
    }

    protected def checkNotEqualObjects(a: Any, b: Any) {
        assertFalse(a.equals(b))
        assertFalse(b.equals(a))
        // hashcode inequality isn't guaranteed, but
        // as long as it happens to work it might
        // detect a bug (if hashcodes are equal,
        // check if it's due to a bug or correct
        // before you remove this)
        assertFalse(a.hashCode() == b.hashCode())
        checkNotEqualToRandomOtherThing(a)
        checkNotEqualToRandomOtherThing(b)
    }

    protected def checkEqualObjects(a: Any, b: Any) {
        assertTrue(a.equals(b))
        assertTrue(b.equals(a))
        assertTrue(a.hashCode() == b.hashCode())
        checkNotEqualToRandomOtherThing(a)
        checkNotEqualToRandomOtherThing(b)
    }

    def fakeOrigin() = {
        SimpleConfigOrigin.newSimple("fake origin")
    }

    def includer() = {
        ConfigImpl.defaultIncluder();
    }

    case class ParseTest(liftBehaviorUnexpected: Boolean, whitespaceMatters: Boolean, test: String)
    object ParseTest {
        def apply(liftBehaviorUnexpected: Boolean, test: String): ParseTest = {
            ParseTest(liftBehaviorUnexpected, false, test);
        }
    }
    implicit def string2jsontest(test: String): ParseTest = ParseTest(false, test)

    // note: it's important to put {} or [] at the root if you
    // want to test "invalidity reasons" other than "wrong root"
    private val invalidJsonInvalidConf = List[ParseTest](
        "{",
        "}",
        "[",
        "]",
        ",",
        "10", // value not in array or object
        "\"foo\"", // value not in array or object
        "\"", // single quote by itself
        ParseTest(true, "[,]"), // array with just a comma in it; lift is OK with this
        ParseTest(true, "[,,]"), // array with just two commas in it; lift is cool with this too
        ParseTest(true, "[1,2,,]"), // array with two trailing commas
        ParseTest(true, "[,1,2]"), // array with initial comma
        ParseTest(true, "{ , }"), // object with just a comma in it
        ParseTest(true, "{ , , }"), // object with just two commas in it
        "{ 1,2 }", // object with single values not key-value pair
        ParseTest(true, "{ , \"foo\" : 10 }"), // object starts with comma
        ParseTest(true, "{ \"foo\" : 10 ,, }"), // object has two trailing commas
        " \"a\" : 10 ,, ", // two trailing commas for braceless root object
        "{ \"foo\" : }", // no value in object
        "{ : 10 }", // no key in object
        " \"foo\" : ", // no value in object with no braces
        " : 10 ", // no key in object with no braces
        " \"foo\" : 10 } ", // close brace but no open
        " \"foo\" : 10 [ ", // no-braces object with trailing gunk 
        "{ \"foo\" }", // no value or colon
        "{ \"a\" : [ }", // [ is not a valid value
        "{ \"foo\" : 10, true }", // non-key after comma
        "{ foo \n bar : 10 }", // newline in the middle of the unquoted key
        "[ 1, \\", // ends with backslash
        // these two problems are ignored by the lift tokenizer
        "[:\"foo\", \"bar\"]", // colon in an array; lift doesn't throw (tokenizer erases it)
        "[\"foo\" : \"bar\"]", // colon in an array another way, lift ignores (tokenizer erases it)
        "[ 10e3e3 ]", // two exponents. ideally this might parse to a number plus string "e3" but it's hard to implement.
        "[ 1-e3 ]", // malformed number but all chars can appear in a number
        "[ \"hello ]", // unterminated string
        ParseTest(true, "{ \"foo\" , true }"), // comma instead of colon, lift is fine with this
        ParseTest(true, "{ \"foo\" : true \"bar\" : false }"), // missing comma between fields, lift fine with this
        "[ 10, }]", // array with } as an element
        "[ 10, {]", // array with { as an element
        "{}x", // trailing invalid token after the root object
        "[]x", // trailing invalid token after the root array
        ParseTest(true, "{}{}"), // trailing token after the root object - lift OK with it
        "{}true", // trailing token after the root object
        ParseTest(true, "[]{}"), // trailing valid token after the root array
        "[]true", // trailing valid token after the root array
        "[${]", // unclosed substitution
        "[$]", // '$' by itself
        "[$  ]", // '$' by itself with spaces after
        "[${}]", // empty substitution (no path)
        "[${?}]", // no path with ? substitution
        ParseTest(false, true, "[${ ?foo}]"), // space before ? not allowed
        """{ "a" : [1,2], "b" : y${a}z }""", // trying to interpolate an array in a string
        """{ "a" : { "c" : 2 }, "b" : y${a}z }""", // trying to interpolate an object in a string
        """{ "a" : ${a} }""", // simple cycle
        """[ { "a" : 2, "b" : ${${a}} } ]""", // nested substitution
        "[ = ]", // = is not a valid token in unquoted text
        "[ + ]",
        "[ # ]",
        "[ ` ]",
        "[ ^ ]",
        "[ ? ]",
        "[ ! ]",
        "[ @ ]",
        "[ * ]",
        "[ & ]",
        "[ \\ ]",
        ParseTest(true, "[ \"foo\nbar\" ]"), // unescaped newline in quoted string, lift doesn't care
        "[ # comment ]",
        "${ #comment }",
        "[ // comment ]",
        "${ // comment }",
        "{ include \"bar\" : 10 }", // include with a value after it
        "{ include foo }", // include with unquoted string
        "{ include : { \"a\" : 1 } }") // include used as unquoted key

    // We'll automatically try each of these with whitespace modifications
    // so no need to add every possible whitespace variation
    protected val validJson = List[ParseTest]("{}",
        "[]",
        """{ "foo" : "bar" }""",
        """["foo", "bar"]""",
        """{ "foo" : 42 }""",
        "{ \"foo\"\n : 42 }", // newline after key
        "{ \"foo\" : \n 42 }", // newline after colon
        """[10, 11]""",
        """[10,"foo"]""",
        """{ "foo" : "bar", "baz" : "boo" }""",
        """{ "foo" : { "bar" : "baz" }, "baz" : "boo" }""",
        """{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : "boo" }""",
        """{ "foo" : [10,11,12], "baz" : "boo" }""",
        """[{},{},{},{}]""",
        """[[[[[[]]]]]]""",
        """[[1], [1,2], [1,2,3], []]""", // nested multiple-valued array
        """{"a":{"a":{"a":{"a":{"a":{"a":{"a":{"a":42}}}}}}}}""",
        "[ \"#comment\" ]", // quoted # comment
        "[ \"//comment\" ]", // quoted // comment
        // this long one is mostly to test rendering
        """{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : { "bar" : "baz", "woo" : [1,2,3,4], "w00t" : true, "a" : false, "b" : 3.14, "c" : null } }""",
        "{}")

    private val validConfInvalidJson = List[ParseTest]("", // empty document
        " ", // empty document single space
        "\n", // empty document single newline
        " \n \n   \n\n\n", // complicated empty document
        "# foo", // just a comment
        "# bar\n", // just a comment with a newline
        "# foo\n//bar", // comment then another with no newline
        """{ "foo" = 42 }""", // equals rather than colon
        """{ foo { "bar" : 42 } }""", // omit the colon for object value
        """{ foo baz { "bar" : 42 } }""", // omit the colon with unquoted key with spaces
        """ "foo" : 42 """, // omit braces on root object
        """{ "foo" : bar }""", // no quotes on value
        """{ "foo" : null bar 42 baz true 3.14 "hi" }""", // bunch of values to concat into a string
        "{ foo : \"bar\" }", // no quotes on key
        "{ foo : bar }", // no quotes on key or value
        "{ foo.bar : bar }", // path expression in key
        "{ foo.\"hello world\".baz : bar }", // partly-quoted path expression in key
        "{ foo.bar \n : bar }", // newline after path expression in key
        "{ foo  bar : bar }", // whitespace in the key
        "{ true : bar }", // key is a non-string token
        ParseTest(true, """{ "foo" : "bar", "foo" : "bar2" }"""), // dup keys - lift just returns both
        ParseTest(true, "[ 1, 2, 3, ]"), // single trailing comma (lift fails to throw)
        ParseTest(true, "[1,2,3  , ]"), // single trailing comma with whitespace
        ParseTest(true, "[1,2,3\n\n , \n]"), // single trailing comma with newlines
        ParseTest(true, "[1,]"), // single trailing comma with one-element array
        ParseTest(true, "{ \"foo\" : 10, }"), // extra trailing comma (lift fails to throw)
        ParseTest(true, "{ \"a\" : \"b\", }"), // single trailing comma in object
        "{ a : b, }", // single trailing comma in object (unquoted strings)
        "{ a : b  \n  , \n }", // single trailing comma in object with newlines
        "a : b, c : d,", // single trailing comma in object with no root braces
        "{ a : b\nc : d }", // skip comma if there's a newline
        "a : b\nc : d", // skip comma if there's a newline and no root braces
        "a : b\nc : d,", // skip one comma but still have one at the end
        "[ foo ]", // not a known token in JSON
        "[ t ]", // start of "true" but ends wrong in JSON
        "[ tx ]",
        "[ tr ]",
        "[ trx ]",
        "[ tru ]",
        "[ trux ]",
        "[ truex ]",
        "[ 10x ]", // number token with trailing junk
        "[ / ]", // unquoted string "slash"
        "{ include \"foo\" }", // valid include
        "{ include\n\"foo\" }", // include with just a newline separating from string
        "{ include\"foo\" }", // include with no whitespace after it
        "[ include ]", // include can be a string value in an array
        "{ foo : include }", // include can be a field value also
        "{ include \"foo\", \"a\" : \"b\" }", // valid include followed by comma and field
        "{ foo include : 42 }", // valid to have a key not starting with include
        "[ ${foo} ]",
        "[ ${?foo} ]",
        "[ ${\"foo\"} ]",
        "[ ${foo.bar} ]",
        "[ abc  xyz  ${foo.bar}  qrs tuv ]", // value concatenation
        "[ 1, 2, 3, blah ]",
        "[ ${\"foo.bar\"} ]",
        "{} # comment",
        "{} // comment",
        """{ "foo" #comment
: 10 }""",
        """{ "foo" // comment
: 10 }""",
        """{ "foo" : #comment
 10 }""",
        """{ "foo" : // comment
 10 }""",
        """{ "foo" : 10 #comment
 }""",
        """{ "foo" : 10 // comment
 }""",
        """[ 10, # comment
 11]""",
        """[ 10, // comment
 11]""",
        """[ 10 # comment
, 11]""",
        """[ 10 // comment
, 11]""",
        """{ /a/b/c : 10 }""", // key has a slash in it
        ParseTest(false, true, "[${ foo.bar}]"), // substitution with leading spaces
        ParseTest(false, true, "[${foo.bar }]"), // substitution with trailing spaces
        ParseTest(false, true, "[${ \"foo.bar\"}]"), // substitution with leading spaces and quoted
        ParseTest(false, true, "[${\"foo.bar\" }]"), // substitution with trailing spaces and quoted
        """[ ${"foo""bar"} ]""", // multiple strings in substitution
        """[ ${foo  "bar"  baz} ]""", // multiple strings and whitespace in substitution
        "[${true}]") // substitution with unquoted true token

    protected val invalidJson = validConfInvalidJson ++ invalidJsonInvalidConf;

    protected val invalidConf = invalidJsonInvalidConf;

    // .conf is a superset of JSON so validJson just goes in here
    protected val validConf = validConfInvalidJson ++ validJson;

    protected def addOffendingJsonToException[R](parserName: String, s: String)(body: => R) = {
        try {
            body
        } catch {
            case t: Throwable =>
                val tokens = try {
                    "tokens: " + tokenizeAsList(s)
                } catch {
                    case e =>
                        "tokenizer failed: " + e.getMessage();
                }
                // don't use AssertionError because it seems to keep Eclipse
                // from showing the causing exception in JUnit view for some reason
                throw new Exception(parserName + " parser did wrong thing on '" + s + "', " + tokens, t)
        }
    }

    protected def whitespaceVariations(tests: Seq[ParseTest], validInLift: Boolean): Seq[ParseTest] = {
        val variations = List({ s: String => s }, // identity
            { s: String => " " + s },
            { s: String => s + " " },
            { s: String => " " + s + " " },
            { s: String => s.replace(" ", "") }, // this would break with whitespace in a key or value
            { s: String => s.replace(":", " : ") }, // could break with : in a key or value
            { s: String => s.replace(",", " , ") } // could break with , in a key or value
            )
        tests flatMap { t =>
            if (t.whitespaceMatters) {
                Seq(t)
            } else {
                val withNonAscii = if (t.test.contains(" "))
                    Seq(ParseTest(validInLift,
                        t.test.replace(" ", "\u2003"))) // 2003 = em space, to test non-ascii whitespace
                else
                    Seq()
                withNonAscii ++ (for (v <- variations)
                    yield ParseTest(t.liftBehaviorUnexpected, v(t.test)))
            }
        }
    }

    // it's important that these do NOT use the public API to create the
    // instances, because we may be testing that the public API returns the
    // right instance by comparing to these, so using public API here would
    // make the test compare public API to itself.
    protected def intValue(i: Int) = new ConfigInt(fakeOrigin(), i, null)
    protected def longValue(l: Long) = new ConfigLong(fakeOrigin(), l, null)
    protected def boolValue(b: Boolean) = new ConfigBoolean(fakeOrigin(), b)
    protected def nullValue() = new ConfigNull(fakeOrigin())
    protected def stringValue(s: String) = new ConfigString(fakeOrigin(), s)
    protected def doubleValue(d: Double) = new ConfigDouble(fakeOrigin(), d, null)

    protected def parseObject(s: String) = {
        parseConfig(s).root
    }

    protected def parseConfig(s: String) = {
        val options = ConfigParseOptions.defaults().
            setOriginDescription("test string").
            setSyntax(ConfigSyntax.CONF);
        ConfigFactory.parseString(s, options).asInstanceOf[SimpleConfig]
    }

    protected def subst(ref: String, optional: Boolean): ConfigSubstitution = {
        val path = Path.newPath(ref)
        val pieces = java.util.Collections.singletonList[Object](new SubstitutionExpression(path, optional))
        new ConfigSubstitution(fakeOrigin(), pieces)
    }

    protected def subst(ref: String): ConfigSubstitution = {
        subst(ref, false)
    }

    protected def substInString(ref: String, optional: Boolean): ConfigSubstitution = {
        import scala.collection.JavaConverters._
        val path = Path.newPath(ref)
        val pieces = List("start<", new SubstitutionExpression(path, optional), ">end")
        new ConfigSubstitution(fakeOrigin(), pieces.asJava)
    }

    protected def substInString(ref: String): ConfigSubstitution = {
        substInString(ref, false)
    }

    def tokenTrue = Tokens.newBoolean(fakeOrigin(), true)
    def tokenFalse = Tokens.newBoolean(fakeOrigin(), false)
    def tokenNull = Tokens.newNull(fakeOrigin())
    def tokenUnquoted(s: String) = Tokens.newUnquotedText(fakeOrigin(), s)
    def tokenString(s: String) = Tokens.newString(fakeOrigin(), s)
    def tokenDouble(d: Double) = Tokens.newDouble(fakeOrigin(), d, null)
    def tokenInt(i: Int) = Tokens.newInt(fakeOrigin(), i, null)
    def tokenLong(l: Long) = Tokens.newLong(fakeOrigin(), l, null)

    private def tokenMaybeOptionalSubstitution(optional: Boolean, expression: Token*) = {
        val l = new java.util.ArrayList[Token]
        for (t <- expression) {
            l.add(t);
        }
        Tokens.newSubstitution(fakeOrigin(), optional, l);
    }

    def tokenSubstitution(expression: Token*) = {
        tokenMaybeOptionalSubstitution(false, expression: _*)
    }

    def tokenOptionalSubstitution(expression: Token*) = {
        tokenMaybeOptionalSubstitution(true, expression: _*)
    }

    // quoted string substitution (no interpretation of periods)
    def tokenKeySubstitution(s: String) = tokenSubstitution(tokenString(s))

    def tokenize(origin: ConfigOrigin, input: Reader): java.util.Iterator[Token] = {
        Tokenizer.tokenize(origin, input, ConfigSyntax.CONF)
    }

    def tokenize(input: Reader): java.util.Iterator[Token] = {
        tokenize(SimpleConfigOrigin.newSimple("anonymous Reader"), input)
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

    // this is importantly NOT using Path.newPath, which relies on
    // the parser; in the test suite we are often testing the parser,
    // so we don't want to use the parser to build the expected result.
    def path(elements: String*) = new Path(elements: _*)

    val resourceDir = {
        val f = new File("config/src/test/resources")
        if (!f.exists())
            throw new Exception("Tests must be run from the root project directory containing " + f.getPath())
        f
    }

    protected def resourceFile(filename: String) = {
        new File(resourceDir, filename)
    }
}
