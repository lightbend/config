package com.typesafe.config.impl

import org.junit._
import org.junit.Assert._

import com.typesafe.config.{
  ConfigException,
  ConfigFactory,
  ConfigParseOptions,
  ConfigSyntax
}
import com.typesafe.config.parser.ConfigDocumentFactory

// Issue #632: deeply nested input overflows the stack and escapes as an Error;
// the parser rejects it with ConfigException instead
class DeepNestingTest {
  private def assertThrows[T <: Throwable](expected: Class[T], action: () => Any): T = {
    try { action() } catch {
      case t: Throwable =>
        assertTrue("expected " + expected + ", got " + t, expected.isInstance(t))
        return expected.cast(t)
    }
    throw new AssertionError("expected " + expected)
  }

  private val deepObjects =
    ("k { " * 105) + "flag = true" + (" }" * 105)
  private val deepArrays = "a = " + ("[" * 105) + "1" + ("]" * 105)

  @Test def nestedObjectsPastTheLimitFailToParse(): Unit = {
    val e = assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigFactory.parseString(deepObjects)
    )
    assertTrue(e.getMessage.contains("nesting"))
  }

  @Test def nestedArraysPastTheLimitFailToParse(): Unit = {
    val e = assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigFactory.parseString(deepArrays)
    )
    assertTrue(e.getMessage.contains("nesting"))
  }

  @Test def documentParserEnforcesTheSameLimit(): Unit = {
    val e = assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigDocumentFactory.parseString(deepObjects)
    )
    assertTrue(e.getMessage.contains("nesting"))
  }

  @Test def nestingJustUnderTheLimitParses(): Unit = {
    val text = ("k { " * 99) + "flag = true" + (" }" * 99)
    var conf = ConfigFactory.parseString(text)
    for (_ <- 1 to 99) conf = conf.getConfig("k")
    assertTrue(conf.getBoolean("flag"))
  }
  @Test def exactObjectBoundaryMatchesWithAndWithoutRootBraces(): Unit = {
    val allowed = ("k { " * 99) + "flag = true" + (" }" * 99)
    val rejected = ("k { " * 100) + "flag = true" + (" }" * 100)
    for (text <- Seq(allowed, "{" + allowed + "}")) {
      ConfigFactory.parseString(text)
      ConfigDocumentFactory.parseString(text)
    }
    for (text <- Seq(rejected, "{" + rejected + "}")) {
      assertThrows(
        classOf[ConfigException.Parse],
        () => ConfigFactory.parseString(text)
      )
      assertThrows(
        classOf[ConfigException.Parse],
        () => ConfigDocumentFactory.parseString(text)
      )
    }
  }

  @Test def arraysAndMixedCollectionsShareTheDepthBudget(): Unit = {
    val allowed = "a=" + ("[" * 99) + "1" + ("]" * 99)
    val rejected = "a=" + ("[" * 100) + "1" + ("]" * 100)
    ConfigFactory.parseString(allowed)
    ConfigDocumentFactory.parseString(allowed)
    assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigFactory.parseString(rejected)
    )
    assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigDocumentFactory.parseString(rejected)
    )
    val mixed = "a=" + ("[{k=" * 50) + "1" + ("}]" * 50)
    assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigFactory.parseString(mixed)
    )
    assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigDocumentFactory.parseString(mixed)
    )
  }

  @Test def jsonAndDocumentReplacementEnforceTheLimit(): Unit = {
    val options = ConfigParseOptions.defaults.setSyntax(ConfigSyntax.JSON)
    val allowed = ("[" * 100) + "1" + ("]" * 100)
    val rejected = ("[" * 101) + "1" + ("]" * 101)
    ConfigDocumentFactory.parseString(allowed, options)
    assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigDocumentFactory.parseString(rejected, options)
    )
    val objectText = ("{\"k\":" * 101) + "1" + ("}" * 101)
    assertThrows(
      classOf[ConfigException.Parse],
      () => ConfigFactory.parseString(objectText, options)
    )
    assertThrows(
      classOf[ConfigException.Parse],
      () =>
        ConfigDocumentFactory.parseString("a=1").withValueText("a", rejected)
    )
  }

  @Test def siblingsDoNotConsumeEachOthersDepthBudget(): Unit = {
    val nested = ("[" * 99) + "1" + ("]" * 99)
    val text = "a=" + nested + "\nb=" + nested
    val config = ConfigFactory.parseString(text)
    assertEquals(1, config.getList("a").size())
    assertEquals(1, config.getList("b").size())
    ConfigDocumentFactory.parseString(text)
  }

  // Bypass the document parser so its guard cannot mask a missing value-parser guard.
  @Test def valueParserEnforcesItsOwnArrayBoundary(): Unit = {
    val origin = SimpleConfigOrigin.newSimple("depth-test")
    def document(depth: Int): ConfigNodeRoot = {
      var node: AbstractConfigNode = new ConfigNodeArray(java.util.Collections.emptyList[AbstractConfigNode]())
      for (_ <- 1 until depth)
        node = new ConfigNodeArray(java.util.Collections.singletonList(node))
      new ConfigNodeRoot(java.util.Collections.singletonList(node), origin)
    }
    val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)
    ConfigParser.parse(document(100), origin, options, null)
    val e = assertThrows(classOf[ConfigException.Parse],
      () => ConfigParser.parse(document(101), origin, options, null))
    assertTrue(e.getMessage.contains("nesting"))
  }

}
