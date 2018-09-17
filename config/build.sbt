fork in test := true
fork in Test := true
fork in run := true
fork in run in Test := true

//env vars for tests
envVars in Test ++= Map("testList.0" -> "0", "testList.1" -> "1")

autoScalaLibrary := false
crossPaths := false

libraryDependencies += "net.liftweb" %% "lift-json" % "2.5" % "test"
libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

checkstyleConfigLocation := CheckstyleConfigLocation.File((baseDirectory.value / "checkstyle-config.xml").toString)

checkstyle in Compile := {
  val log = streams.value.log
  (checkstyle in Compile).value
  val resultFile = (checkstyleOutputFile in Compile).value
  val results = scala.xml.XML.loadFile(resultFile)
  val errorFiles = results \\ "checkstyle" \\ "file"

  def errorFromXml(node: scala.xml.NodeSeq): (String, String, String) = {
    val line: String = (node \ "@line" text)
    val msg: String = (node \ "@message" text)
    val source: String = (node \ "@source" text)
    (line, msg, source)
  }
  def errorsFromXml(fileNode: scala.xml.NodeSeq): Seq[(String, String, String, String)] = {
    val name: String = (fileNode \ "@name" text)
    val errors = (fileNode \\ "error") map { e => errorFromXml(e) }
    errors map { case (line, error, source) => (name, line, error, source) }
  }

  val errors = errorFiles flatMap { f => errorsFromXml(f) }

  if (errors.nonEmpty) {
    for (e <- errors) {
      log.error(s"${e._1}:${e._2}: ${e._3} (from ${e._4})")
    }
    throw new RuntimeException(s"Checkstyle failed with ${errors.size} errors")
  }
  log.info("No errors from checkstyle")
}

// add checkstyle as a dependency of doc
doc in Compile := ((doc in Compile).dependsOn(checkstyle in Compile)).value

findbugsReportType := Some(FindbugsReport.Html)
findbugsReportPath := Some(crossTarget.value / "findbugs.html")
findbugsEffort := FindbugsEffort.Maximum
findbugsMaxMemory := 2000

javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8",
                                           "-g", "-Xlint:unchecked")

// because we test some global state such as singleton caches,
// we have to run tests in serial.
parallelExecution in Test := false

javacOptions in (Compile, doc) ++= Seq("-group", s"Public API (version ${version.value})", "com.typesafe.config:com.typesafe.config.parser",
                                       "-group", "Internal Implementation - Not ABI Stable", "com.typesafe.config.impl")

javadocSourceBaseUrl := {
  for (gitHead <- com.typesafe.sbt.SbtGit.GitKeys.gitHeadCommit.value)
    yield s"https://github.com/lightbend/config/blob/$gitHead/config/src/main/java"
}
