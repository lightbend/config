import sbt._
import Keys._

object ConfigBuild extends Build {
    lazy val root = Project(id = "root",
                            base = file(".")) aggregate(testLib, configLib, simpleLib, simpleApp)

    lazy val configLib = Project(id = "config",
                                 base = file("config")) dependsOn(testLib % "test->test")

    lazy val testLib = Project(id = "test-lib",
                               base = file("test-lib"))

    lazy val simpleLib = Project(id = "simple-lib",
                                 base = file("examples/simple-lib")) dependsOn(configLib)

    lazy val simpleApp = Project(id = "simple-app",
                                 base = file("examples/simple-app")) dependsOn(simpleLib)

    lazy val complexApp = Project(id = "complex-app",
                                  base = file("examples/complex-app")) dependsOn(simpleLib)
}
