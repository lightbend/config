import org.junit.Assert._
import org.junit._
import com.typesafe.config._
import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * This is to show how the API works and to be sure it's usable
 * from outside of the library's package and in Scala.
 * It isn't intended to be asserting anything or adding test coverage.
 */
class ApiExamples {
    @Test
    def readSomeConfig() {
        val conf = Config.load("test01")

        // you don't have to write the types explicitly of course,
        // just doing that to show what they are.
        val a: Int = conf.getInt("ints.fortyTwo")
        val obj: ConfigObject = conf.getObject("ints")
        val c: Int = obj.getInt("fortyTwo")

        // this is unfortunate; asScala creates a mutable map but
        // the ConfigObject is in fact immutable.
        val objAsScalaMap: mutable.Map[String, ConfigValue] = obj.asScala

        // this is sort of ugly...
        val d: Int = conf.getAnyRef("ints.fortyTwo") match {
            case x: java.lang.Integer => x
            case x: java.lang.Long => x.intValue
        }

        class EnhancedConfigObject(obj: ConfigObject) {
            def getAny(path: String): Any = obj.getAnyRef(path)
        }
        implicit def obj2enhanced(obj: ConfigObject) = new EnhancedConfigObject(obj)

        // somewhat nicer
        val e: Int = conf.getAny("ints.fortyTwo") match {
            case x: Int => x
            case x: Long => x.intValue
        }
        assertEquals(42, e)
    }
}
