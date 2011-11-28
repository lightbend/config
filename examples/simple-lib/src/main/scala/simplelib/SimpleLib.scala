package simplelib

import com.typesafe.config._

// we have a constructor allowing the app to provide a custom Config
class SimpleLibContext(config: Config) {

    // This verifies that the Config is sane and has our
    // reference config.
    config.checkValid(ConfigFactory.defaultReference())

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
