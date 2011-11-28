import com.typesafe.config._
import simplelib._

object ComplexApp extends App {
    // This app is "complex" because we load multiple separate app
    // configs into a single JVM and we have a separately-configurable
    // context for simple lib.

    System.setProperty("simple-lib.whatever", "This value comes from a system property")

    // "config1" is just an example of using a file other than application.conf
    val config1 = ConfigFactory.load("complex1")

    demoConfigInSimpleLib(config1)

    // "config2" shows how to configure a library with a custom settings subtree
    val config2 = ConfigFactory.load("complex2");

    // pull out complex-app.simple-lib-context and move it to
    // the toplevel, creating a new config suitable for our SimpleLibContext.
    // The defaultOverrides() have to be put back on top of the stack so
    // they still override any simple-lib settings.
    // We fall back to config2 again to be sure we get simple-lib's
    // reference.conf plus any other settings we've set. You could
    // also just fall back to ConfigFactory.referenceConfig() if
    // you don't want application.conf settings outside of
    // complex-app.simple-lib-context to be used.
    val simpleLibConfig2 = ConfigFactory.defaultOverrides()
        .withFallback(config2.getConfig("complex-app.simple-lib-context"))
        .withFallback(config2)

    demoConfigInSimpleLib(simpleLibConfig2)

    // Now let's illustrate that simple-lib will get upset if we pass it
    // a bad config. In this case, we'll fail to merge the reference
    // config in to complex-app.simple-lib-context, so simple-lib will
    // point out that some settings are missing.
    try {
        demoConfigInSimpleLib(config2.getConfig("complex-app.simple-lib-context"))
    } catch {
        case e: ConfigException.ValidationFailed =>
            println("when we passed a bad config to simple-lib, it said: " + e.getMessage)
    }

    def demoConfigInSimpleLib(config: Config) {
        val context = new SimpleLibContext(config)
        context.printSetting("simple-lib.foo")
        context.printSetting("simple-lib.hello")
        context.printSetting("simple-lib.whatever")
    }
}
