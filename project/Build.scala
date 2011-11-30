import sbt._
import Keys._

object ConfigBuild extends Build {
    val unpublished = Seq(
        // no artifacts in this project
        publishArtifact := false,
        // make-pom has a more specific publishArtifact setting already
        // so needs specific override
        publishArtifact in makePom := false,
        // can't seem to get rid of ivy files except by no-op'ing the entire publish task
        publish := {},
        publishLocal := {}
    )

    // this is in newer sbt versions, for now cut-and-pasted
    val isSnapshot = SettingKey[Boolean]("is-snapshot", "True if the the version of the project is a snapshot version.")

    override val settings = super.settings ++ Seq(isSnapshot <<= isSnapshot or version(_ endsWith "-SNAPSHOT"))

    lazy val root = Project(id = "root",
                            base = file("."),
                            settings = Project.defaultSettings ++ unpublished) aggregate(testLib, configLib, simpleLib, simpleApp)

    lazy val configLib = Project(id = "config",
                                 base = file("config")) dependsOn(testLib % "test->test")

    lazy val testLib = Project(id = "test-lib",
                               base = file("test-lib"),
                               settings = Project.defaultSettings ++ unpublished)

    lazy val simpleLib = Project(id = "simple-lib",
                                 base = file("examples/simple-lib"),
                                 settings = Project.defaultSettings ++ unpublished) dependsOn(configLib)

    lazy val simpleApp = Project(id = "simple-app",
                                 base = file("examples/simple-app"),
                                 settings = Project.defaultSettings ++ unpublished) dependsOn(simpleLib)

    lazy val complexApp = Project(id = "complex-app",
                                  base = file("examples/complex-app"),
                                  settings = Project.defaultSettings ++ unpublished) dependsOn(simpleLib)
}
