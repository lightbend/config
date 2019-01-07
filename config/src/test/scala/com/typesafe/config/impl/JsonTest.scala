/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.Reader
import java.util

import com.typesafe.config._
import net.liftweb.{ json => lift }
import org.junit.Assert._
import org.junit._

class JsonTest extends TestUtils {

    def parse(s: String): ConfigValue = {
        val options = ConfigParseOptions.defaults().
            setOriginDescription("test json string").
            setSyntax(ConfigSyntax.JSON)
        Parseable.newString(s, options).parseValue()
    }

    def parseAsConf(s: String): ConfigValue = {
        val options = ConfigParseOptions.defaults().
            setOriginDescription("test conf string").
            setSyntax(ConfigSyntax.CONF)
        Parseable.newString(s, options).parseValue()
    }

    private[this] def toLift(value: ConfigValue): lift.JValue = {
        import scala.collection.JavaConverters._

        value match {
            case v: ConfigObject =>
                lift.JObject(v.keySet().asScala.map({ k => lift.JField(k, toLift(v.get(k))) }).toList)
            case v: ConfigList =>
                lift.JArray(v.asScala.toList.map({ elem => toLift(elem) }))
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

    private[this] def fromLift(liftValue: lift.JValue): AbstractConfigValue = {
        import scala.collection.JavaConverters._

        liftValue match {
            case lift.JObject(fields) =>
                val m = new util.HashMap[String, AbstractConfigValue]()
                fields.foreach({ field => m.put(field.name, fromLift(field.value)) })
                new SimpleConfigObject(fakeOrigin(), m)
            case lift.JArray(values) =>
                new SimpleConfigList(fakeOrigin(), values.map(fromLift).asJava)
            case lift.JInt(i) =>
                if (i.isValidInt) intValue(i.intValue) else longValue(i.longValue)
            case lift.JBool(b) =>
                new ConfigBoolean(fakeOrigin(), b)
            case lift.JDouble(d) =>
                doubleValue(d)
            case lift.JString(s) =>
                new ConfigString.Quoted(fakeOrigin(), s)
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
                throw new ConfigException.Parse(SimpleConfigOrigin.newSimple("lift parser"), e.getMessage(), e)
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

    // For string quoting, check behavior of escaping a random character instead of one on the list;
    // lift-json seems to oddly treat that as a \ literal

    @Test
    def invalidJsonThrows(): Unit = {
        var tested = 0
        // be sure Lift throws on the string
        for (invalid <- whitespaceVariations(invalidJson, false)) {
            if (invalid.liftBehaviorUnexpected) {
                // lift unexpectedly doesn't throw, confirm that
                addOffendingJsonToException("lift-nonthrowing", invalid.test) {
                    fromJsonWithLiftParser(invalid.test)
                    fromJsonWithLiftParser(new java.io.StringReader(invalid.test))
                }
            } else {
                addOffendingJsonToException("lift", invalid.test) {
                    intercept[ConfigException] {
                        fromJsonWithLiftParser(invalid.test)
                    }
                    intercept[ConfigException] {
                        fromJsonWithLiftParser(new java.io.StringReader(invalid.test))
                    }
                    tested += 1
                }
            }

        }

        assertTrue(tested > 100) // just checking we ran a bunch of tests
        tested = 0

        // be sure we also throw
        for (invalid <- whitespaceVariations(invalidJson, false)) {
            addOffendingJsonToException("config", invalid.test) {
                intercept[ConfigException] {
                    parse(invalid.test)
                }
                tested += 1
            }
        }

        assertTrue(tested > 100)
    }

    @Test
    def validJsonWorks(): Unit = {
        var tested = 0

        // be sure we do the same thing as Lift when we build our JSON "DOM"
        for (valid <- whitespaceVariations(validJson, validInLift = true)) {
            val liftAST = if (valid.liftBehaviorUnexpected) {
                SimpleConfigObject.empty()
            } else {
                addOffendingJsonToException("lift", valid.test) {
                    fromJsonWithLiftParser(valid.test)
                }
            }
            val ourAST = addOffendingJsonToException("config-json", valid.test) {
                parse(valid.test)
            }
            val ourConfAST = addOffendingJsonToException("config-conf", valid.test) {
                parseAsConf(valid.test)
            }
            if (valid.liftBehaviorUnexpected) {
                // ignore this for now
            } else {
                addOffendingJsonToException("config", valid.test) {
                    assertEquals(liftAST, ourAST)
                }
            }

            // check that our parser gives the same result in JSON mode and ".conf" mode.
            // i.e. this tests that ".conf" format is a superset of JSON.
            addOffendingJsonToException("config", valid.test) {
                assertEquals(ourAST, ourConfAST)
            }

            tested += 1
        }

        assertTrue(tested > 100) // just verify we ran a lot of tests
    }

    @Test
    def renderingJsonStrings() {
        def r(s: String) = ConfigImplUtil.renderJsonString(s)
        assertEquals(""""abcdefg"""", r("""abcdefg"""))
        assertEquals(""""\" \\ \n \b \f \r \t"""", r("\" \\ \n \b \f \r \t"))
        // control characters are escaped. Remember that unicode escapes
        // are weird and happen on the source file before doing other processing.
        assertEquals("\"\\" + "u001f\"", r("\u001f"))
    }
}
