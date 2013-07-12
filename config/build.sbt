import de.johoop.findbugs4sbt.FindBugs._
import de.johoop.findbugs4sbt.ReportType
import de.johoop.jacoco4sbt._
import JacocoPlugin._

fork in test := true

fork in run := true

fork in run in Test := true

autoScalaLibrary := false

crossPaths := false

libraryDependencies += "net.liftweb" %% "lift-json" % "2.5" % "test"

libraryDependencies += "com.novocode" % "junit-interface" % "0.7" % "test"

externalResolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

seq(findbugsSettings : _*)

findbugsReportType := ReportType.Html

findbugsReportName := "findbugs.html"

seq(jacoco.settings : _*)

javacOptions in (Compile,doc) ++= Seq("-exclude", "com.typesafe.config.impl")

javacOptions in (Compile,compile) ++= Seq("-source", "1.6", "-target", "1.6", "-g")
