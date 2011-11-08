package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._
import net.liftweb.{ json => lift }
import java.io.Reader
import java.io.StringReader
import com.typesafe.config._
import java.util.HashMap
import java.io.File
import org.junit.runner.RunWith
import org.junit.runners.AllTests

class EquivalentsTest extends TestUtils {

    private def equivDirs() = {
        val resourceDir = new File("src/test/resources")
        if (!resourceDir.exists())
            throw new RuntimeException("This test can only be run from the project's root directory")
        val rawEquivs = resourceDir.listFiles()
        val equivs = rawEquivs.filter({ f => f.getName().startsWith("equiv") })
        equivs
    }

    private def filesForEquiv(equiv: File) = {
        val rawFiles = equiv.listFiles()
        val files = rawFiles.filter({ f => f.getName().endsWith(".json") || f.getName().endsWith(".conf") })
        files
    }

    // would like each "equivNN" directory to be a suite and each file in the dir
    // to be a test, but not sure how to convince junit to do that.
    @Test
    def testEquivalents() {
        var dirCount = 0
        var fileCount = 0
        for (equiv <- equivDirs()) {
            dirCount += 1

            val files = filesForEquiv(equiv)
            val (originals, others) = files.partition({ f => f.getName().startsWith("original.") })
            if (originals.isEmpty)
                throw new RuntimeException("Need a file named 'original' in " + equiv.getPath())
            if (originals.size > 1)
                throw new RuntimeException("Multiple 'original' files in " + equiv.getPath() + ": " + originals)
            val original = Parser.parse(originals(0))

            for (testFile <- others) {
                fileCount += 1
                val value = Parser.parse(testFile)
                describeFailure(testFile.getPath()) {
                    assertEquals(original, value)
                }

                // check that all .json files can be parsed as .conf,
                // i.e. .conf must be a superset of JSON
                if (testFile.getName().endsWith(".json")) {
                    val parsedAsConf = Parser.parse(SyntaxFlavor.CONF, testFile)
                    describeFailure(testFile.getPath() + " parsed as .conf") {
                        assertEquals(original, parsedAsConf)
                    }
                }
            }
        }

        // This is a little "checksum" to be sure we really tested what we were expecting.
        // it breaks every time you add a file, so you have to update it.
        assertEquals(1, dirCount)
        assertEquals(2, fileCount)
    }
}
