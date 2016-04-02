// to release, bump major/minor/micro as appropriate,
// update NEWS, update version in README.md, tag, then
// publishSigned.
// Release tags should follow: http://semver.org/

enablePlugins(GitVersioning)
git.baseVersion := "1.3.0"

organization in GlobalScope := "com.typesafe"

scalacOptions in GlobalScope in Compile := Seq("-unchecked", "-deprecation", "-feature")
scalacOptions in GlobalScope in Test := Seq("-unchecked", "-deprecation", "-feature")

scalaVersion in ThisBuild := "2.10.4"

useGpg := true

aggregate in PgpKeys.publishSigned := false
PgpKeys.publishSigned := (PgpKeys.publishSigned in configLib).value

aggregate in PgpKeys.publishLocalSigned := false
PgpKeys.publishLocalSigned := (PgpKeys.publishLocalSigned in configLib).value
