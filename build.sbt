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
ThisBuild / publishMavenStyle       := true

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning)
  .aggregate(
    testLib, configLib,
    simpleLibScala, simpleAppScala, complexAppScala,
    simpleLibJava, simpleAppJava, complexAppJava
  )
  .settings(commonSettings)
  .settings(
    name                                   := "config-root",
    git.baseVersion                        := (ThisBuild / git.baseVersion).value,
    doc / aggregate                        := false,
    doc                                    := (configLib / Compile / doc).value,
    packageDoc / aggregate                 := false,
    packageDoc                             := (configLib / Compile / packageDoc).value
  )

lazy val configLib =  Project("config", file("config"))
  .enablePlugins(SbtOsgi)
  .dependsOn(testLib % "test->test")
  .settings(osgiSettings)
  .settings(
    autoScalaLibrary                       := false,
    crossPaths                             := false,
    libraryDependencies                    += "net.liftweb" %% "lift-json" % "3.3.0" % Test,
    libraryDependencies                    += "com.novocode" % "junit-interface" % "0.11" % Test,

    Compile / compile / javacOptions       ++= Seq("-source", "1.8", "-target", "1.8",
                                                   "-g", "-Xlint:unchecked"),

    Compile / doc / javacOptions           ++= Seq("-group", s"Public API (version ${version.value})", "com.typesafe.config:com.typesafe.config.parser",
                                                   "-group", "Internal Implementation - Not ABI Stable", "com.typesafe.config.impl"),
    javadocSourceBaseUrl := Some("https://github.com/lightbend/config/tree/main/config/src/main/java"),
    // because we test some global state such as singleton caches,
    // we have to run tests in serial.
    Test / parallelExecution               := false,

    test / fork                            := true,
    Test / fork                            := true,
    run / fork                             := true,
    Test/ run / fork                       := true,

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
      "CONFIG_FORCE_akka_event__handler__dispatcher_max__pool__size" -> "10",
      "SECRET_A" -> "A", // ConfigTest.renderShowEnvVariableValues
      "SECRET_B" -> "B", // ConfigTest.renderShowEnvVariableValues
      "SECRET_C" -> "C", // ConfigTest.renderShowEnvVariableValues
      "MY_LIST_0" -> "a", // ConfigTest.envVariableListExpansion
      "MY_LIST_1" -> "b", // ConfigTest.envVariableListExpansion
      "MY_LIST_2" -> "c", // ConfigTest.envVariableListExpansion
      "NUM_LIST_0" -> "1", // ConfigTest.envVariableListExpansion
      "NUM_LIST_1" -> "2", // ConfigTest.envVariableListExpansion
      "NUM_LIST_2" -> "3" // ConfigTest.envVariableListExpansion
    ),

    OsgiKeys.exportPackage                 := Seq("com.typesafe.config", "com.typesafe.config.impl"),
    Compile / packageBin / packageOptions  +=
      Package.ManifestAttributes("Automatic-Module-Name" -> "typesafe.config" )
  )

lazy val commonSettings: Seq[Setting[_]] = Def.settings(
  unpublished,
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
