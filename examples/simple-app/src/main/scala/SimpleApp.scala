import com.typesafe.config._
import simplelib._

object SimpleApp extends App {
    // example of how system properties override
    System.setProperty("simple-lib.whatever", "This value comes from a system property")

    // In this simple app, we're allowing SimpleLibContext() to
    // use the default config in application.conf
    val context = new SimpleLibContext()
    context.printSetting("simple-lib.foo")
    context.printSetting("simple-lib.hello")
    context.printSetting("simple-lib.whatever")
}
