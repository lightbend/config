// to release, bump major/minor/micro as appropriate,
// update NEWS, update version in README.md, tag, then
// publishSigned.
// Release tags should follow: http://semver.org/
import scalariform.formatter.preferences._

ThisBuild / git.baseVersion         := "1.4.0"
ThisBuild / organization            := "com.typesafe"
ThisBuild / Compile / scalacOptions := List("-unchecked", "-deprecation", "-feature")
ThisBuild / Test / scalacOptions    := List("-unchecked", "-deprecation", "-feature")
ThisBuild / scalaVersion            := "2.12.8"

ThisBuild / scmInfo                 := Option(
  ScmInfo(url("https://github.com/lightbend/config"), "scm:git@github.com:lightbend/config.git")
)
ThisBuild / developers              := List(
  Developer(
    id    = "havocp",
    name  = "Havoc Pennington",
    email = "@havocp",
    url   = url("http://ometer.com/")
  )
)
ThisBuild / description             := "configuration library for JVM languages using HOCON files"
ThisBuild / licenses                := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / homepage                := Option(url("https://github.com/lightbend/config"))
ThisBuild / pomIncludeRepository    := { _ => false }
ThisBuild / publishTo               := {
  val nexus = "https://oss.sonatype.org/"
  if ((ThisBuild / isSnapshot).value) Option("Sonatype OSS Snapshots" at nexus + "content/repositories/snapshots")
  else Option("Sonatype OSS Staging" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle       := true

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning)
  .aggregate(
    testLib, configLib,
    simpleLibScala, simpleAppScala, complexAppScala,
    simpleLibJava, simpleAppJava, complexAppJava
  )
  .settings(commonSettings)
  .settings(nocomma {
    name                                   := "config-root"
    git.baseVersion                        := (ThisBuild / git.baseVersion).value
    doc / aggregate                        := false
    doc                                    := (configLib / Compile / doc).value
    packageDoc / aggregate                 := false
    packageDoc                             := (configLib / Compile / packageDoc).value
//    checkstyle / aggregate                 := false
//    checkstyle                             := (configLib / Compile / checkstyle).value
    PgpKeys.publishSigned / aggregate      := false
    PgpKeys.publishSigned                  := (configLib / PgpKeys.publishSigned).value
    PgpKeys.publishLocalSigned / aggregate := false
    PgpKeys.publishLocalSigned             := (configLib / PgpKeys.publishLocalSigned).value
  })

lazy val configLib =  Project("config", file("config"))
  .enablePlugins(SbtOsgi)
  .dependsOn(testLib % "test->test")
  .settings(osgiSettings)
  .settings(nocomma {
    autoScalaLibrary                       := false
    crossPaths                             := false
    libraryDependencies                    += "net.liftweb" %% "lift-json" % "3.3.0" % Test
    libraryDependencies                    += "com.novocode" % "junit-interface" % "0.11" % Test

    Compile / compile / javacOptions       ++= Seq("-source", "1.8", "-target", "1.8",
                                                   "-g", "-Xlint:unchecked")

    Compile / doc / javacOptions           ++= Seq("-group", s"Public API (version ${version.value})", "com.typesafe.config:com.typesafe.config.parser",
                                                   "-group", "Internal Implementation - Not ABI Stable", "com.typesafe.config.impl")
    javadocSourceBaseUrl := {
      for (gitHead <- com.typesafe.sbt.SbtGit.GitKeys.gitHeadCommit.value)
        yield s"https://github.com/lightbend/config/blob/$gitHead/config/src/main/java"
    }
    // because we test some global state such as singleton caches,
    // we have to run tests in serial.
    Test / parallelExecution               := false

    test / fork                            := true
    Test / fork                            := true
    run / fork                             := true
    Test/ run / fork                       := true

    //env vars for tests
    Test / envVars                         ++= Map("testList.0" -> "0",
      "testList.1" -> "1",
      "CONFIG_FORCE_b" -> "5",
      "CONFIG_FORCE_testList_0" -> "10",
      "CONFIG_FORCE_testList_1" -> "11",
      "CONFIG_FORCE_42___a" -> "1",
      "CONFIG_FORCE_a_b_c" -> "2",
      "CONFIG_FORCE_a__c" -> "3",
      "CONFIG_FORCE_a___c" -> "4",
      "CONFIG_FORCE_akka_version" -> "foo",
      "CONFIG_FORCE_akka_event__handler__dispatcher_max__pool__size" -> "10")

    OsgiKeys.exportPackage                 := Seq("com.typesafe.config", "com.typesafe.config.impl")
    publish                                := sys.error("use publishSigned instead of plain publish")
    publishLocal                           := sys.error("use publishLocalSigned instead of plain publishLocal")
    Compile / packageBin / packageOptions  +=
      Package.ManifestAttributes("Automatic-Module-Name" -> "typesafe.config" )
    scalariformPreferences                 := scalariformPreferences.value
      .setPreference(IndentSpaces, 4)
      .setPreference(FirstArgumentOnNewline, Preserve)

//    checkstyleConfigLocation               := CheckstyleConfigLocation.File((baseDirectory.value / "checkstyle-config.xml").toString)

//    Compile / checkstyle := {
//      val log = streams.value.log
//      (Compile / checkstyle).value
//      val resultFile = (Compile / checkstyleOutputFile).value
//      val results = scala.xml.XML.loadFile(resultFile)
//      val errorFiles = results \\ "checkstyle" \\ "file"
//
//      def errorFromXml(node: scala.xml.NodeSeq): (String, String, String) = {
//        val line: String = (node \ "@line" text)
//        val msg: String = (node \ "@message" text)
//        val source: String = (node \ "@source" text)
//        (line, msg, source)
//      }
//      def errorsFromXml(fileNode: scala.xml.NodeSeq): Seq[(String, String, String, String)] = {
//        val name: String = (fileNode \ "@name" text)
//        val errors = (fileNode \\ "error") map { e => errorFromXml(e) }
//        errors map { case (line, error, source) => (name, line, error, source) }
//      }
//
//      val errors = errorFiles flatMap { f => errorsFromXml(f) }
//
//      if (errors.nonEmpty) {
//        for (e <- errors) {
//          log.error(s"${e._1}:${e._2}: ${e._3} (from ${e._4})")
//        }
//        throw new RuntimeException(s"Checkstyle failed with ${errors.size} errors")
//      }
//      log.info("No errors from checkstyle")
//    }

    // add checkstyle as a dependency of doc
    Compile / doc                          := (Compile / doc)
//      .dependsOn(Compile / checkstyle)
      .value

    findbugsReportType                     := Some(FindbugsReport.Html)
    findbugsReportPath                     := Some(crossTarget.value / "findbugs.html")
    findbugsEffort                         := FindbugsEffort.Maximum
    findbugsMaxMemory                      := 2000
  })

lazy val commonSettings: Seq[Setting[_]] = Def.settings(
  unpublished,
  scalariformPreferences := scalariformPreferences.value
    .setPreference(IndentSpaces, 4)
    .setPreference(FirstArgumentOnNewline, Preserve),
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
)

def proj(id: String, base: File) = Project(id, base) settings commonSettings

lazy val testLib = proj("config-test-lib", file("test-lib"))

lazy val simpleLibScala  = proj("config-simple-lib-scala",  file("examples/scala/simple-lib"))  dependsOn configLib
lazy val simpleAppScala  = proj("config-simple-app-scala",  file("examples/scala/simple-app"))  dependsOn simpleLibScala
lazy val complexAppScala = proj("config-complex-app-scala", file("examples/scala/complex-app")) dependsOn simpleLibScala

lazy val simpleLibJava  = proj("config-simple-lib-java",  file("examples/java/simple-lib"))  dependsOn configLib
lazy val simpleAppJava  = proj("config-simple-app-java",  file("examples/java/simple-app"))  dependsOn simpleLibJava
lazy val complexAppJava = proj("config-complex-app-java", file("examples/java/complex-app")) dependsOn simpleLibJava

val unpublished = Seq(
  // no artifacts in this project
  publishArtifact               := false,
  // make-pom has a more specific publishArtifact setting already
  // so needs specific override
  makePom / publishArtifact     := false,
  // no docs to publish
  packageDoc / publishArtifact  := false,
  publish / skip := true
)
