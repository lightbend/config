/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config._
import org.junit.Assert._
import org.junit.Test

class EnvOverridesRenderingTest {
    private def checkRendering(config: Config): Unit = {
        assertEquals("5", config.getString("b"))
        assertEquals("2", config.getString("a.b.c"))
        for (json <- List(true, false); formatted <- List(true, false)) {
            val options = ConfigRenderOptions.defaults().setJson(json)
                .setFormatted(formatted).setOriginComments(false)
            val hidden = config.root().render(options.setShowEnvVariableValues(false))
            val reparsed = ConfigFactory.parseString(hidden)
            assertEquals(hidden, "<env variable>", reparsed.getString("b"))
            assertEquals(hidden, "<env variable>", reparsed.getString("a.b.c"))
            assertEquals("5", ConfigFactory.parseString(config.root().render(options)).getString("b"))
            assertEquals("2", ConfigFactory.parseString(config.root().render(options)).getString("a.b.c"))
        }
        assertEquals("\"<env variable>\"", config.getValue("b").render(
            ConfigRenderOptions.concise().setShowEnvVariableValues(false)))
        assertEquals("\"5\"", config.getValue("b").render(ConfigRenderOptions.concise()))
    }

    @Test
    def directOverridesHideValues(): Unit = {
        checkRendering(ConfigFactory.systemEnvironmentOverrides())
    }

    @Test
    def resolvedOverridesHideValuesAndPreserveOrdinaryValues(): Unit = {
        val config = ConfigFactory.systemEnvironmentOverrides().withFallback(
            ConfigFactory.parseString("b=ordinary, a.b.c=ordinary, ordinary=visible, copy=${b}"))
            .resolve()
        checkRendering(config)
        val hidden = ConfigFactory.parseString(config.root().render(
            ConfigRenderOptions.concise().setShowEnvVariableValues(false)))
        assertEquals("visible", hidden.getString("ordinary"))
        assertEquals("<env variable>", hidden.getString("copy"))
        assertEquals("5", config.getString("copy"))
    }
}
