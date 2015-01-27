import sbt._
import Keys._
import com.typesafe.sbt.osgi.SbtOsgi.{ OsgiKeys, osgiSettings }

object ConfigBuild extends Build {
    val unpublished = Seq(
        // no artifacts in this project
        publishArtifact := false,
        // make-pom has a more specific publishArtifact setting already
        // so needs specific override
        publishArtifact in makePom := false,
        // no docs to publish
        publishArtifact in packageDoc := false,
        // can't seem to get rid of ivy files except by no-op'ing the entire publish task
        publish := {},
        publishLocal := {}
    )

    object sonatype extends PublishToSonatype {
        def projectUrl    = "https://github.com/typesafehub/config"
        def developerId   = "havocp"
        def developerName = "Havoc Pennington"
        def developerUrl  = "http://ometer.com/"
        def scmUrl        = "git://github.com/typesafehub/config.git"
    }

    override val settings = super.settings ++ Seq(isSnapshot <<= isSnapshot or version(_ endsWith "-SNAPSHOT"))

    lazy val rootSettings: Seq[Setting[_]] =
      unpublished ++
      Seq(aggregate in doc := false,
          doc := (doc in (configLib, Compile)).value,
          aggregate in packageDoc := false,
          packageDoc := (packageDoc in (configLib, Compile)).value)

    lazy val root = Project(id = "root",
                            base = file("."),
                            settings = rootSettings) aggregate(testLib, configLib,
                                                               simpleLibScala, simpleAppScala, complexAppScala,
                                                               simpleLibJava, simpleAppJava, complexAppJava)

    lazy val configLib = Project(id = "config",
                                 base = file("config"),
                                 settings =
                                   sonatype.settings ++
                                   osgiSettings ++
                                   Seq(
                                     OsgiKeys.exportPackage := Seq("com.typesafe.config", "com.typesafe.config.impl"),
                                     packagedArtifact in (Compile, packageBin) <<= (artifact in (Compile, packageBin), OsgiKeys.bundle).identityMap,
                                     artifact in (Compile, packageBin) ~= { _.copy(`type` = "bundle") },
                                     publish := sys.error("use publishSigned instead of plain publish"),
                                     publishLocal := sys.error("use publishLocalSigned instead of plain publishLocal")
                                   )) dependsOn testLib % "test->test"

    def project(id: String, base: File) = Project(id, base, settings = unpublished)

    lazy val testLib = project("config-test-lib", file("test-lib"))

    lazy val simpleLibScala  = project("config-simple-lib-scala",  file("examples/scala/simple-lib"))  dependsOn configLib
    lazy val simpleAppScala  = project("config-simple-app-scala",  file("examples/scala/simple-app"))  dependsOn simpleLibScala
    lazy val complexAppScala = project("config-complex-app-scala", file("examples/scala/complex-app")) dependsOn simpleLibScala

    lazy val simpleLibJava  = project("config-simple-lib-java",  file("examples/java/simple-lib"))  dependsOn configLib
    lazy val simpleAppJava  = project("config-simple-app-java",  file("examples/java/simple-app"))  dependsOn simpleLibJava
    lazy val complexAppJava = project("config-complex-app-java", file("examples/java/complex-app")) dependsOn simpleLibJava
}

// from https://raw.github.com/paulp/scala-improving/master/project/PublishToSonatype.scala

abstract class PublishToSonatype {
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

  def generatePomExtra: xml.NodeSeq = {
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
    publishTo <<= isSnapshot { (snapshot) => Some(if (snapshot) ossSnapshots else ossStaging) },
    publishArtifact in Test := false,
    pomIncludeRepository := (_ => false),
    pomExtra := generatePomExtra
  )
}
