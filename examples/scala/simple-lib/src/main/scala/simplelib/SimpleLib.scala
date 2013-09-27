package simplelib

import com.typesafe.config._

// Whenever you write a library, allow people to supply a Config but
// also default to ConfigFactory.load if they don't supply one.
// Libraries generally have some kind of Context or other object
// where it's convenient to place the configuration.

// we have a constructor allowing the app to provide a custom Config
class SimpleLibContext(config: Config) {

    // This verifies that the Config is sane and has our
    // reference config. Importantly, we specify the "simple-lib"
    // path so we only validate settings that belong to this
    // library. Otherwise, we might throw mistaken errors about
    // settings we know nothing about.
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    // This uses the standard default Config, if none is provided,
    // which simplifies apps willing to use the defaults
    def this() {
        this(ConfigFactory.load())
    }

    // this is the amazing functionality provided by simple-lib
    def printSetting(path: String) {
        println("The setting '" + path + "' is: " + config.getString(path))
    }
}

// Here is an OPTIONAL alternative way to access settings, which
// has the advantage of validating fields on startup and avoiding
// typos. This is redundant with the SimpleLibContext above,
// in fact we'll show a settings-based context below.
class SimpleLibSettings(config: Config) {

    // checkValid(), just as in the plain SimpleLibContext
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    // note that these fields are NOT lazy, because if we're going to
    // get any exceptions, we want to get them on startup.
    val foo = config.getString("simple-lib.foo")
    val hello = config.getString("simple-lib.hello")
    val whatever = config.getString("simple-lib.whatever")
}

// This is a different way to do SimpleLibContext, using the
// SimpleLibSettings class to encapsulate and validate your
// settings on startup
class SimpleLibContext2(config: Config) {
    val settings = new SimpleLibSettings(config)

    def this() {
        this(ConfigFactory.load())
    }

    // this is the amazing functionality provided by simple-lib with a Settings class
    def printSettings() {
        println("foo=" + settings.foo)
        println("hello=" + settings.hello)
        println("whatever=" + settings.whatever)
    }
}
