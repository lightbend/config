/** Copyright (C) 2011 Typesafe Inc. <http://typesafe.com> */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit.Test
import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigRenderOptions, ConfigResolveOptions}
import scala.collection.JavaConverters._

class PartialMergeTest extends TestUtils {
    private val partially = ConfigResolveOptions.noSystem().setAllowUnresolved(true)
    private val fully = ConfigResolveOptions.noSystem()
    private def partial(input: String) = ConfigFactory.parseString(input).resolve(partially)
    private val appends = "a = [${s}-1]\na += ${s}-2\na += ${s}-3\na += ${s}-4"
    private val strings = "a = ${s}-1\na = ${a}-2\na = ${a}-3\na = ${a}-4"
    private val lists = "a = ${base}\na += ${s}-2\na += ${s}-3\na += ${s}-4"
    private val fallback = ConfigFactory.parseString("s = abc\nbase = [z]")

    private def bothOrders(input: String)(check: Config => Unit): Unit = {
        val p = partial(input)
        check(p.withFallback(fallback).resolve(fully))
        check(fallback.withFallback(p).resolve(fully))
    }

    @Test def appendedListsResolveInEitherOrder(): Unit = bothOrders(appends) { c =>
        assertEquals(List("abc-1", "abc-2", "abc-3", "abc-4"), c.getStringList("a").asScala.toList)
    }

    @Test def stringSelfReferencesResolveInEitherOrder(): Unit = bothOrders(strings) { c =>
        assertEquals("abc-1-2-3-4", c.getString("a"))
    }

    @Test def appendedSubstitutionsResolveInEitherOrder(): Unit = bothOrders(lists) { c =>
        assertEquals(List("z", "abc-2", "abc-3", "abc-4"), c.getStringList("a").asScala.toList)
    }

    // O01 removes nested merges during resolution; no renderer changes from P03.
    @Test def partiallyResolvedValuesRoundTrip(): Unit = {
        for (input <- List(appends, strings, lists); formatted <- List(true, false); comments <- List(true, false)) {
            val options = ConfigRenderOptions.defaults().setJson(false)
                .setOriginComments(false).setComments(comments).setFormatted(formatted)
            var p = partial(input)
            val expected = ConfigFactory.parseString(input).withFallback(fallback).resolve(fully).root()
            for (_ <- 1 to 5) {
                val rendered = p.root().render(options)
                assertFalse(rendered, rendered.contains("unresolved merge"))
                val reparsed = ConfigFactory.parseString(rendered)
                assertEquals(expected, reparsed.withFallback(fallback).resolve(fully).root())
                assertEquals(rendered, reparsed.root().render(options))
                p = reparsed
            }
        }
    }

    @Test def unresolvedListHidesEarlierSubstitution(): Unit = {
        val p = partial("a = ${nope}\na = [${s}]")
        assertEquals("{\"a\":[${s}]}", p.root().render(ConfigRenderOptions.concise()))
        assertEquals(List("abc"), p.withFallback(fallback).resolve(fully).getStringList("a").asScala.toList)
    }

    @Test def listSelfReferenceRetainsEarlierElements(): Unit = {
        val p = partial("a = [1]\na = ${a} [${s}]")
        assertEquals("{\"a\":[1,${s}]}", p.root().render(ConfigRenderOptions.concise()))
        assertEquals(List(1, 2), p.withFallback(ConfigFactory.parseString("s = 2"))
            .resolve(fully).getIntList("a").asScala.toList)
    }

    @Test def possibleObjectsKeepEarlierObject(): Unit = {
        for ((value, completion, expected) <- List(
            ("${x}", "x = { r = 2 }", "{q=1,r=2}"),
            ("${x} { r = 2 }", "x = { p = 0 }", "{p=0,q=1,r=2}"),
            ("${x} ${y}", "x = { r = 2 }\ny = { t = 3 }", "{q=1,r=2,t=3}"),
            ("${x}\u2009${y}", "x = { r = 2 }\ny = { t = 3 }", "{q=1,r=2,t=3}"))) {
            val input = "a = { q = 1 }\na = " + value
            val f = ConfigFactory.parseString(completion)
            val p = partial(input)
            val result = p.withFallback(f).resolve(fully)
            assertEquals(ConfigFactory.parseString(expected).root(), result.getConfig("a").root())
            assertEquals(ConfigFactory.parseString(input).withFallback(f).resolve(fully).root(), result.root())
        }
    }

    @Test def quotedWhitespaceAndTextCannotBecomeObjects(): Unit = {
        for (suffix <- List("foo", "\" \"", "\"\"", "\u200b")) {
            val input = "a = { q = 1 }\na = ${x}" + suffix
            val f = ConfigFactory.parseString("x = { r = 2 }")
            intercept[ConfigException.WrongType] { partial(input).withFallback(f).resolve(fully) }
            intercept[ConfigException.WrongType] { ConfigFactory.parseString(input).withFallback(f).resolve(fully) }
        }
    }
}
