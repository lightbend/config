package com.typesafe.config.impl

import com.typesafe.config.{ ConfigFactory, ConfigParseOptions, ConfigRenderOptions }
import org.junit.Test

// Regression tests for rendering old behaviour compatibility
class ConfigDefaultRenderingTest extends TestUtils {
    private val parseOptions = ConfigParseOptions.defaults.setAllowMissing(true)
    private val myDefaultRenderOptions = ConfigRenderOptions.defaults
        .setJson(false)
        .setOriginComments(false)
        .setComments(true)
        .setFormatted(true)

    def formatHocon(
        str: String): String =
        ConfigFactory
            .parseString(str, parseOptions)
            .root
            .render(
                myDefaultRenderOptions)

    @Test
    def properArrayConcat(): Unit = {
        val in =
            """ex1 = [1, 2]
              |except: ${ex1} ${ex1}
              |myEmpty: " "
              |""".stripMargin
        val result = formatHocon(in)

        println(result)

        ConfigFactory
          .parseString(result, parseOptions.setAllowMissing(false))
          .resolve()
          .root()
          .render()
    }

}
