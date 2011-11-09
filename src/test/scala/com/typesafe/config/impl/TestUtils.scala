package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._

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
        new SimpleConfigOrigin("fake origin")
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
    private val invalidJsonInvalidConf = List[ParseTest]("", // empty document
        "{",
        "}",
        "[",
        "]",
        "10", // value not in array or object
        "\"foo\"", // value not in array or object
        "\"", // single quote by itself
        "{ \"foo\" : }", // no value in object
        "{ : 10 }", // no key in object
        "{ \"foo\" }", // no value or colon
        "{ \"a\" : [ }", // [ is not a valid value
        "{ \"foo\" : 10, }", // extra trailing comma
        "{ \"foo\" : 10, true }", // non-key after comma
        "{ foo : \"bar\" }", // no quotes on key
        "{ foo : bar }", // no quotes on key or value
        "[ 1, \\", // ends with backslash
        // these two problems are ignored by the lift tokenizer
        "[:\"foo\", \"bar\"]", // colon in an array; lift doesn't throw (tokenizer erases it)
        "[\"foo\" : \"bar\"]", // colon in an array another way, lift ignores (tokenizer erases it)
        "[ 10e3e3 ]", // two exponents. ideally this might parse to a number plus string "e3" but it's hard to implement.
        "[ 1-e3 ]", // malformed number but all chars can appear in a number
        "[ \"hello ]", // unterminated string
        "[ 1, 2, 3, ]", // array with empty element
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
        """${"foo""bar"}""", // multiple strings in substitution
        """{ "a" : [1,2], "b" : y${a}z }""", // trying to interpolate an array in a string
        """{ "a" : { "c" : 2 }, "b" : y${a}z }""", // trying to interpolate an object in a string
        ParseTest(false, true, "[${ foo.bar}]"), // substitution with leading spaces
        ParseTest(false, true, "[${foo.bar }]"), // substitution with trailing spaces
        ParseTest(false, true, "[${ \"foo.bar\"}]"), // substitution with leading spaces and quoted
        ParseTest(false, true, "[${\"foo.bar\" }]"), // substitution with trailing spaces and quoted
        "[${true}]", // substitution with unquoted true token
        "[ = ]", // = is not a valid token
        "") // empty document again, just for clean formatting of this list ;-)

    // We'll automatically try each of these with whitespace modifications
    // so no need to add every possible whitespace variation
    protected val validJson = List[ParseTest]("{}",
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
        ParseTest(true, """{ "foo" : "bar", "foo" : "bar2" }"""), // dup keys - lift just returns both, we use last one
        """[{},{},{},{}]""",
        """[[[[[[]]]]]]""",
        """{"a":{"a":{"a":{"a":{"a":{"a":{"a":{"a":42}}}}}}}}""",
        // this long one is mostly to test rendering
        """{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : { "bar" : "baz", "woo" : [1,2,3,4], "w00t" : true, "a" : false, "b" : 3.14, "c" : null } }""",
        "{}")

    private val validConfInvalidJson = List[ParseTest](
        """{ "foo" : bar }""", // no quotes on value
        """{ "foo" : null bar 42 baz true 3.14 "hi" }""", // bunch of values to concat into a string
        "[ foo ]", // not a known token in JSON
        "[ t ]", // start of "true" but ends wrong in JSON
        "[ tx ]",
        "[ tr ]",
        "[ trx ]",
        "[ tru ]",
        "[ trux ]",
        "[ truex ]",
        "[ 10x ]", // number token with trailing junk
        "[ ${foo} ]",
        "[ ${\"foo\"} ]",
        "[ ${foo.bar} ]",
        "[ abc  xyz  ${foo.bar}  qrs tuv ]", // value concatenation
        "[ 1, 2, 3, blah ]",
        "[ ${\"foo.bar\"} ]")

    protected val invalidJson = validConfInvalidJson ++ invalidJsonInvalidConf;

    protected val invalidConf = invalidJsonInvalidConf;

    // .conf is a superset of JSON so validJson just goes in here
    protected val validConf = validConfInvalidJson ++ validJson;

    protected def whitespaceVariations(tests: Seq[ParseTest]): Seq[ParseTest] = {
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
                return Seq(t)
            } else {
                val withNonAscii = ParseTest(true,
                    t.test.replace(" ", "\u2003")) // 2003 = em space, to test non-ascii whitespace
                Seq(withNonAscii) ++ (for (v <- variations)
                    yield ParseTest(t.liftBehaviorUnexpected, v(t.test)))
            }
        }
    }

    protected def intValue(i: Int) = new ConfigInt(fakeOrigin(), i)
    protected def longValue(l: Long) = new ConfigLong(fakeOrigin(), l)
    protected def boolValue(b: Boolean) = new ConfigBoolean(fakeOrigin(), b)
    protected def nullValue() = new ConfigNull(fakeOrigin())
    protected def stringValue(s: String) = new ConfigString(fakeOrigin(), s)
    protected def doubleValue(d: Double) = new ConfigDouble(fakeOrigin(), d)

    protected def parseObject(s: String) = {
        Parser.parse(SyntaxFlavor.CONF, new SimpleConfigOrigin("test string"), s).asInstanceOf[AbstractConfigObject]
    }

    protected def subst(ref: String, style: SubstitutionStyle = SubstitutionStyle.PATH) = {
        val pieces = java.util.Collections.singletonList[Object](new Substitution(ref, style))
        new ConfigSubstitution(fakeOrigin(), pieces)
    }

    protected def substInString(ref: String, style: SubstitutionStyle = SubstitutionStyle.PATH) = {
        import scala.collection.JavaConverters._
        val pieces = List("start<", new Substitution(ref, style), ">end")
        new ConfigSubstitution(fakeOrigin(), pieces.asJava)
    }

    def tokenTrue = Tokens.newBoolean(fakeOrigin(), true)
    def tokenFalse = Tokens.newBoolean(fakeOrigin(), false)
    def tokenNull = Tokens.newNull(fakeOrigin())
    def tokenUnquoted(s: String) = Tokens.newUnquotedText(fakeOrigin(), s)
    def tokenKeySubstitution(s: String) = Tokens.newSubstitution(fakeOrigin(), s, SubstitutionStyle.KEY)
    def tokenPathSubstitution(s: String) = Tokens.newSubstitution(fakeOrigin(), s, SubstitutionStyle.PATH)
    def tokenString(s: String) = Tokens.newString(fakeOrigin(), s)
    def tokenDouble(d: Double) = Tokens.newDouble(fakeOrigin(), d)
    def tokenInt(i: Int) = Tokens.newInt(fakeOrigin(), i)
    def tokenLong(l: Long) = Tokens.newLong(fakeOrigin(), l)
}
