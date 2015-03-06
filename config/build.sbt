import de.johoop.findbugs4sbt.FindBugs._
import de.johoop.findbugs4sbt.{ Effort, ReportType }
import de.johoop.jacoco4sbt.JacocoPlugin.jacoco
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

SbtScalariform.scalariformSettings

val formatPrefs = FormattingPreferences()
  .setPreference(IndentSpaces, 4)

ScalariformKeys.preferences in Compile := formatPrefs
ScalariformKeys.preferences in Test := formatPrefs

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

javacOptions in (Compile, compile) ++= Seq("-source", "1.6", "-target", "1.8", "-g")

// because we test some global state such as singleton caches,
// we have to run tests in serial.
parallelExecution in Test := false

javacOptions in (Compile, doc) ++= Seq("-group", s"Public API (version ${version.value})", "com.typesafe.config",
                                       "-group", "Internal Implementation - Not ABI Stable", "com.typesafe.config.impl")

javadocSourceBaseUrl := {
  for (gitHead <- com.typesafe.sbt.SbtGit.GitKeys.gitHeadCommit.value)
    yield s"https://github.com/typesafehub/config/blob/$gitHead/config/src/main/java"
}

javaVersionPrefix in javaVersionCheck := Some("1.8")
