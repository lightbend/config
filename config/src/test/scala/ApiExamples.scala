/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
import org.junit.Assert._
import org.junit._
import com.typesafe.config._
import scala.collection.JavaConverters._
import scala.collection.mutable
import language.implicitConversions

/**
 * This is to show how the API works and to be sure it's usable
 * from outside of the library's package and in Scala.
 * It isn't intended to be asserting anything or adding test coverage.
 */
class ApiExamples {
    @Test
    def readSomeConfig() {
        val conf = ConfigFactory.load("test01")

        // you don't have to write the types explicitly of course,
        // just doing that to show what they are.
        val a: Int = conf.getInt("ints.fortyTwo")
        val child: Config = conf.getConfig("ints")
        val b: Int = child.getInt("fortyTwo")
        val ms: Long = conf.getMilliseconds("durations.halfSecond")

        // a Config has an associated tree of values, with a ConfigObject
        // at the root. The ConfigObject implements java.util.Map
        val obj: ConfigObject = conf.root

        // this is how you do conf.getInt "manually" on the value tree, if you
        // were so inclined. (This is not a good approach vs. conf.getInt() above,
        // just showing how ConfigObject stores a tree of values.)
        val c: Int = obj.get("ints")
            .asInstanceOf[ConfigObject]
            .get("fortyTwo").unwrapped()
            .asInstanceOf[java.lang.Integer]

        // this is unfortunate; asScala creates a mutable map but
        // a ConfigObject is in fact immutable.
        val objAsScalaMap: mutable.Map[String, ConfigValue] = obj.asScala

        // this is sort of ugly, but you could improve it
        // with a Scala implicit wrapper, see below...
        val d: Int = conf.getAnyRef("ints.fortyTwo") match {
            case x: java.lang.Integer => x
            case x: java.lang.Long => x.intValue
        }

        // our implicit wrapper: do stuff like this to get a nicer Scala API
        class EnhancedConfig(c: Config) {
            def getAny(path: String): Any = c.getAnyRef(path)
        }
        implicit def config2enhanced(c: Config): EnhancedConfig = new EnhancedConfig(c)

        // somewhat nicer now
        val e: Int = conf.getAny("ints.fortyTwo") match {
            case x: Int => x
            case x: Long => x.intValue
        }
        assertEquals(42, e)
    }
}
