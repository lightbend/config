package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import org.junit.BeforeClass
import java.net.URL
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigException

class HttpTest extends TestUtils {
    import HttpTest._

    private def foreachSyntax(body: Option[ConfigSyntax] => Unit): Unit = {
        for (syntax <- Seq(Some(ConfigSyntax.JSON), Some(ConfigSyntax.CONF), Some(ConfigSyntax.PROPERTIES), None))
            body(syntax)
    }

    private def foreachSyntaxOptions(body: ConfigParseOptions => Unit): Unit = foreachSyntax { syntaxOption =>
        val options = syntaxOption map { syntax =>
            ConfigParseOptions.defaults.setSyntax(syntax)
        } getOrElse {
            ConfigParseOptions.defaults()
        }

        body(options)
    }

    def url(path: String): URL = new URL(s"$baseUrl/$path")

    @Test
    def parseEmpty(): Unit = {
        foreachSyntaxOptions { options =>
            val conf = ConfigFactory.parseURL(url("empty"), options)
            assertTrue("empty conf was parsed", conf.root.isEmpty)
        }
    }

    @Test
    def parseFooIs42(): Unit = {
        foreachSyntaxOptions { options =>
            val conf = ConfigFactory.parseURL(url("foo"), options)
            assertEquals(42, conf.getInt("foo"))
        }
    }

    @Test
    def notFoundThrowsIO(): Unit = {
        val e = intercept[ConfigException.IO] {
            ConfigFactory.parseURL(url("notfound"), ConfigParseOptions.defaults().setAllowMissing(false))
        }
        assertTrue(s"expected different exception for notfound, got $e", e.getMessage.contains("/notfound"))
    }

    @Test
    def internalErrorThrowsBroken(): Unit = {
        val e = intercept[ConfigException.BugOrBroken] {
            ConfigFactory.parseURL(url("error"), ConfigParseOptions.defaults().setAllowMissing(false))
        }
        assertTrue(s"expected different exception for error url, got $e", e.getMessage.contains("/error"))
    }

    @Test
    def notFoundDoesNotThrowIfAllowingMissing(): Unit = {
        val conf = ConfigFactory.parseURL(url("notfound"), ConfigParseOptions.defaults().setAllowMissing(true))
        assertEquals(0, conf.root.size)
    }

    @Test
    def internalErrorThrowsEvenIfAllowingMissing(): Unit = {
        val e = intercept[ConfigException.BugOrBroken] {
            ConfigFactory.parseURL(url("error"), ConfigParseOptions.defaults().setAllowMissing(true))
        }
        assertTrue(s"expected different exception for error url when allowing missing, got $e", e.getMessage.contains("/error"))
    }

    @Test
    def relativeInclude(): Unit = {
        val conf = ConfigFactory.parseURL(url("includes-a-friend"))
        assertEquals(42, conf.getInt("foo"))
        assertEquals(43, conf.getInt("bar"))
    }
}

object HttpTest {
    import ToyHttp.{ Request, Response }

    final val jsonContentType = "application/json"
    final val propertiesContentType = "text/x-java-properties"
    final val hoconContentType = "application/hocon"

    private var server: Option[ToyHttp] = None

    def port: Int = server.map(_.port).getOrElse(throw new Exception("http server isn't running"))
    def baseUrl = s"http://127.0.0.1:$port"

    private def handleThreeTypes(request: Request, json: String, props: String, hocon: String): Response = {
        request.headers.get("accept") match {
            case Some(`jsonContentType`) | None => Response(200, jsonContentType, json)
            case Some(`propertiesContentType`) => Response(200, propertiesContentType, props)
            case Some(`hoconContentType`) => Response(200, hoconContentType, hocon)
            case Some(other) => Response(500, "text/plain", s"bad content type '$other'")
        }
    }

    private def handleRequest(request: Request): Response = {
        request.path match {
            case "/empty" =>
                handleThreeTypes(request, "{}", "", "")

            case "/foo" | "/foo.conf" =>
                handleThreeTypes(request, "{ \"foo\" : 42 }", "foo:42", "foo=42")

            case "/notfound" =>
                Response(404, "text/plain", "This is never found")

            case "/error" =>
                Response(500, "text/plain", "This is always an error")

            case "/includes-a-friend" =>
                // currently, a suffix-less include like this will cause
                // us to search for foo.conf, foo.json, foo.properties, but
                // not load plain foo.
                Response(200, hoconContentType, """
                  include "foo"
                  include "foo/bar"
                  """)

            case "/foo/bar.conf" =>
                Response(200, hoconContentType, "{ bar = 43 }")

            case path =>
                Response(404, "text/plain", s"Never heard of '$path'")
        }
    }

    @BeforeClass
    def startServer(): Unit = {
        server = Some(ToyHttp(handleRequest))
    }

    @AfterClass
    def stopServer(): Unit = {
        server.foreach(_.stop())
    }
}
