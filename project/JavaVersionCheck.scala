import sbt._

object JavaVersionCheck {
  val javacVersionPrefix = taskKey[Option[String]]("java version prefix required by javacVersionCheck")
  val javacVersionCheck = taskKey[String]("checks the Java version vs. javacVersionPrefix, returns actual version")

  def javacVersionCheckSettings: Seq[Setting[_]] = Seq(
    javacVersionPrefix := Some("1.6"),
    javacVersionCheck := {
      val realLog = Keys.streams.value.log
      val javac = (Keys.compileInputs in Keys.compile in Compile).value.compilers.javac

      val captureVersionLog = new sbt.Logger() {
        var captured: Option[String] = None
         def log(level: sbt.Level.Value,message: => String): Unit = {
           val m = message
           if (level == Level.Warn && m.startsWith("javac ")) {
             captured = Some(m.substring("javac ".length).trim)
           } else {
             realLog.log(level, m)
           }
         }
        def success(message: => String): Unit = realLog.success(message)
        def trace(t: => Throwable): Unit = realLog.trace(t)
      }

      javac(sources=Nil, classpath=Nil, outputDirectory=file("."), options=Seq("-version"))(captureVersionLog)

      val version = captureVersionLog.captured match {
        case Some(v) => v
        case None =>
          throw new Exception("Failed to get or parse the output of javac -version")
      }

      javacVersionPrefix.value match {
        case Some(prefix) =>
          if (!version.startsWith(prefix)) {
            throw new Exception(s"javac version ${version} may not be used to publish, it has to start with ${prefix} due to javacVersionPrefix setting")
          }
        case None =>
      }

      version
    },
    // we hook onto deliverConfiguration to run the version check as early as possible,
    // before we actually do anything. But we don't want to require the version check
    // just for compile.
    Keys.deliverConfiguration := {
      val log = Keys.streams.value.log
      val javacVersion = javacVersionCheck.value
      log.info("Will publish locally with javac version " + javacVersion)
      Keys.deliverConfiguration.value
    },
    Keys.deliverLocalConfiguration := {
      val log = Keys.streams.value.log
      val javacVersion = javacVersionCheck.value
      log.info("Will publish locally with javac version " + javacVersion)
      Keys.deliverLocalConfiguration.value
    }
  )
}
