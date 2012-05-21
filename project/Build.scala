import sbt._
import Keys._
import com.typesafe.sbtosgi.OsgiPlugin._

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

    object sonatype extends PublishToSonatype(ConfigBuild) {
        def projectUrl    = "https://github.com/typesafehub/config"
        def developerId   = "havocp"
        def developerName = "Havoc Pennington"
        def developerUrl  = "http://ometer.com/"
        def scmUrl        = "git://github.com/typesafehub/config.git"
    }

    override val settings = super.settings ++ Seq(isSnapshot <<= isSnapshot or version(_ endsWith "-SNAPSHOT"))

    lazy val root = Project(id = "root",
                            base = file("."),
                            settings = Project.defaultSettings ++ unpublished) aggregate(testLib, configLib, simpleLib, simpleApp)

    lazy val configLib = Project(id = "config",
                                 base = file("config"),
                                 settings =
                                   Project.defaultSettings ++
                                   sonatype.settings ++
                                   osgiSettings ++
                                   Seq(
                                     OsgiKeys.exportPackage := Seq("com.typesafe.config"),
                                     packagedArtifact in (Compile, packageBin) <<= (artifact in (Compile, packageBin), OsgiKeys.bundle).identityMap,
                                     artifact in (Compile, packageBin) ~= { _.copy(`type` = "bundle") }
                                   )) dependsOn(testLib % "test->test")

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

// from https://raw.github.com/paulp/scala-improving/master/project/PublishToSonatype.scala

abstract class PublishToSonatype(build: Build) {
  import build._

  val ossSnapshots = "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
  val ossStaging   = "Sonatype OSS Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/"

  def projectUrl: String
  def developerId: String
  def developerName: String
  def developerUrl: String

  def licenseName         = "Apache License, Version 2.0"
  def licenseUrl          = "http://www.apache.org/licenses/LICENSE-2.0"
  def licenseDistribution = "repo"
  def scmUrl: String
  def scmConnection       = "scm:git:" + scmUrl

  def generatePomExtra(scalaVersion: String): xml.NodeSeq = {
    <url>{ projectUrl }</url>
      <licenses>
        <license>
          <name>{ licenseName }</name>
          <url>{ licenseUrl }</url>
          <distribution>{ licenseDistribution }</distribution>
        </license>
      </licenses>
    <scm>
      <url>{ scmUrl }</url>
      <connection>{ scmConnection }</connection>
    </scm>
    <developers>
      <developer>
        <id>{ developerId }</id>
        <name>{ developerName }</name>
        <url>{ developerUrl }</url>
      </developer>
    </developers>
  }

  def settings: Seq[Setting[_]] = Seq(
    publishMavenStyle := true,
    publishTo <<= (isSnapshot) { (snapshot) => Some(if (snapshot) ossSnapshots else ossStaging) },
    publishArtifact in Test := false,
    pomIncludeRepository := (_ => false),
    pomExtra <<= (scalaVersion)(generatePomExtra)
  )
}
