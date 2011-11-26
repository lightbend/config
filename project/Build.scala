import sbt._
import Keys._

object ConfigBuild extends Build {
    lazy val root = Project(id = "config",
                            base = file("."))
}
