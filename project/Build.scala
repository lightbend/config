import sbt._
import Keys._

object ConfigBuild extends Build {
    lazy val root = Project(id = "root",
                            base = file(".")) aggregate(testLib, configLib)

    lazy val configLib = Project(id = "config",
                                 base = file("config")) dependsOn(testLib % "test->test")

    lazy val testLib = Project(id = "test-lib",
                               base = file("test-lib"))
}
