import de.johoop.findbugs4sbt.FindBugs._
import de.johoop.findbugs4sbt.{ Effort, ReportType }
import de.johoop.jacoco4sbt.JacocoPlugin.jacoco

fork in test := true
fork in run := true
fork in run in Test := true

autoScalaLibrary := false
crossPaths := false

libraryDependencies += "net.liftweb" %% "lift-json" % "2.5" % "test"
libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

externalResolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

findbugsSettings
findbugsReportType := Some(ReportType.Html)
findbugsReportName := Some("findbugs.html")
findbugsEffort := Effort.High
findbugsMaxMemory := 1000

jacoco.settings

javacOptions in (Compile, compile) ++= Seq("-source", "1.6", "-target", "1.6", "-g")

// because we test some global state such as singleton caches,
// we have to run tests in serial.
parallelExecution in Test := false

sources in (Compile, doc) ~= (_.filter(_.getParentFile.getName != "impl"))

javaVersionPrefix in javaVersionCheck := Some("1.6")
