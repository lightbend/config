package simplelib

import com.typesafe.config._

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
