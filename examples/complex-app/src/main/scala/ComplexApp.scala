import com.typesafe.config._
import simplelib._

object ComplexApp extends App {

    // This app is "complex" because we load multiple separate app
    // configs into a single JVM and we have a separately-configurable
    // context for simple lib.

    // using a custom Config with the simple-lib library
    // (simple-lib is a library in this same examples/ directory)
    def demoConfigInSimpleLib(config: Config) {
        val context = new SimpleLibContext(config)
        context.printSetting("simple-lib.foo")
        context.printSetting("simple-lib.hello")
        context.printSetting("simple-lib.whatever")
    }

    // system property overrides work, but the properties must be set
    // before the config lib is used (config lib will not notice changes
    // once it loads the properties)
    System.setProperty("simple-lib.whatever", "This value comes from a system property")

    ///////////

    // "config1" is just an example of using a file other than application.conf
    val config1 = ConfigFactory.load("complex1")

    // use the config ourselves
    println("config1, complex-app.something=" + config1.getString("complex-app.something"))

    // use the config for a library
    demoConfigInSimpleLib(config1)

    //////////

    // "config2" shows how to configure a library with a custom settings subtree
    val config2 = ConfigFactory.load("complex2");

    // use the config ourselves
    println("config2, complex-app.something=" + config2.getString("complex-app.something"))

    // pull out complex-app.simple-lib-context and move it to
    // the toplevel, creating a new config suitable for our SimpleLibContext.
    // The defaultOverrides() have to be put back on top of the stack so
    // they still override any simple-lib settings.
    // We fall back to config2 again to be sure we get simple-lib's
    // reference.conf plus any other settings we've set. You could
    // also just fall back to ConfigFactory.referenceConfig() if
    // you don't want complex2.conf settings outside of
    // complex-app.simple-lib-context to be used.
    val simpleLibConfig2 = ConfigFactory.defaultOverrides()
        .withFallback(config2.getConfig("complex-app.simple-lib-context"))
        .withFallback(config2)

    demoConfigInSimpleLib(simpleLibConfig2)

    //////////

    // Here's an illustration that simple-lib will get upset if we pass it
    // a bad config. In this case, we'll fail to merge the reference
    // config in to complex-app.simple-lib-context, so simple-lib will
    // point out that some settings are missing.
    try {
        demoConfigInSimpleLib(config2.getConfig("complex-app.simple-lib-context"))
    } catch {
        case e: ConfigException.ValidationFailed =>
            println("when we passed a bad config to simple-lib, it said: " + e.getMessage)
    }
}
