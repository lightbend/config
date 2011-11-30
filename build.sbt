
// to release, bump major/minor/micro as appropriate,
// drop SNAPSHOT, tag and publish.
// add snapshot back so git master is previous release
// with -SNAPSHOT.
// when releasing a SNAPSHOT to the repo, bump the micro
// version at least.
// Also, change the version number in the README.md
// Versions and git tags should follow: http://semver.org/
// except using -SNAPSHOT instead of without hyphen.

version in GlobalScope := "0.1.0-SNAPSHOT"

organization in GlobalScope := "com.typesafe.config"

scalacOptions in GlobalScope in Compile := Seq("-unchecked", "-deprecation")

scalacOptions in GlobalScope in Test := Seq("-unchecked", "-deprecation")
