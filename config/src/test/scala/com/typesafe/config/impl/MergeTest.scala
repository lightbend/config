/**
 * Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions, ConfigResolver, ConfigValue}

class MergeTest extends TestUtils {

    @Test
    def mergeSubstitutionWithFallback() {
        val fallback = parseConfig("a: 123")
        val conf = parseConfig("b: 234, a: ${b}")
        val merged = conf
          .withFallback(fallback)
          .resolveWith(
              ConfigFactory.empty(),
              ConfigResolveOptions.defaults().setAllowUnresolved(true)
          ).resolve()
        assertEquals(234, merged.getInt("a"))
    }

    @Test
    def mergeSubstitutionWithReplacementsAndFallback() {
        val replacements = parseConfig("b: 567")
        val fallback = parseConfig("a: 123, b: 456")
        val conf = parseConfig("b: 234, a: ${b}")
        val merged = conf
          .withFallback(fallback)
          .resolveWith(
              replacements,
              ConfigResolveOptions.defaults().setAllowUnresolved(true)
          ).resolve()
        assertEquals(567, merged.getInt("a"))
    }

    @Test
    def mergeSubstitutionWithFallbackInChild() {
        val fallback = parseConfig("a: 123")
        val conf = parseConfig("b: {d: 234}, a: {c: ${b.d} }")
        val merged = conf
          .withFallback(fallback)
          .resolveWith(
              ConfigFactory.empty(),
              ConfigResolveOptions.defaults().setAllowUnresolved(true)
          ).resolve()
        assertEquals(234, merged.getInt("a.c"))
    }

    @Test
    def mergeSubstitutionFromFallback() {
        val fallback = parseConfig("a: 123")
        val conf = parseConfig("b: ${a}")
        val merged = conf
          .withFallback(fallback)
          .resolveWith(
              ConfigFactory.empty(),
              ConfigResolveOptions.defaults().setAllowUnresolved(true)
          ).resolve()
        assertEquals(123, merged.getInt("a"))
    }

    @Test
    def mergeSubstitutionInObjectWithFallback() {
        val fallback = parseConfig("a: {c: 1, d: 2}")
        val conf = parseConfig("b: {d: 3, e: 4}, a: ${b}")
        val merged = conf
          .withFallback(fallback)
          .resolveWith(
              ConfigFactory.empty(),
              ConfigResolveOptions.defaults().setAllowUnresolved(true)
          ).resolve()
        assertEquals(3, merged.getInt("a.d"))
    }
}
