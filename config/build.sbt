import de.johoop.findbugs4sbt.FindBugs._
import de.johoop.findbugs4sbt.ReportType
import de.johoop.findbugs4sbt.Effort
import de.johoop.jacoco4sbt._
import JacocoPlugin._

fork in test := true

fork in run := true

fork in run in Test := true

autoScalaLibrary := false

crossPaths := false

libraryDependencies += "net.liftweb" %% "lift-json" % "2.5" % "test"

libraryDependencies += "com.novocode" % "junit-interface" % "0.10-M4" % "test"

externalResolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

seq(findbugsSettings : _*)

findbugsReportType := Some(ReportType.Html)

findbugsReportName := Some("findbugs.html")

findbugsEffort := Effort.High

findbugsMaxMemory := 1000

seq(jacoco.settings : _*)

javacOptions in (Compile,compile) ++= Seq("-source", "1.6", "-target", "1.6", "-g")

// because we test some global state such as singleton caches,
// we have to run tests in serial.
parallelExecution in Test := false

sources in (Compile, doc) := (sources in (Compile, doc)).value.filter(_.getParentFile.getName != "impl")

JavaVersionCheck.javacVersionCheckSettings
