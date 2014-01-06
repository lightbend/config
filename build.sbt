import com.typesafe.sbt.SbtGit
import com.typesafe.sbt.SbtPgp.PgpKeys.useGpg

// to release, bump major/minor/micro as appropriate,
// update NEWS, update version in README.md, tag, then
// publishSigned.
// Release tags should follow: http://semver.org/

SbtGit.versionWithGit

SbtGit.git.baseVersion in GlobalScope := "1.1.0"

organization in GlobalScope := "com.typesafe"

scalacOptions in GlobalScope in Compile := Seq("-unchecked", "-deprecation", "-feature")

scalacOptions in GlobalScope in Test := Seq("-unchecked", "-deprecation", "-feature")

scalaVersion in ThisBuild := "2.10.2"

useGpg in GlobalScope := true
