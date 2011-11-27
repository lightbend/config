import sbt._
import Keys._

object ConfigBuild extends Build {
    lazy val root = Project(id = "root",
                            base = file(".")) aggregate(configLib)

    lazy val configLib = Project(id = "config",
                                 base = file("config"))
}
