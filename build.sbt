
// to release, bump major/minor/micro as appropriate,
// update NEWS, update version in README.md, drop SNAPSHOT,
// tag and publish.
// then add snapshot back so git master is previous release
// with -SNAPSHOT.
// when releasing a SNAPSHOT to the repo, bump the micro
// version at least.
// Versions and git tags should follow: http://semver.org/
// except using -SNAPSHOT instead of without hyphen.

version in GlobalScope := "0.4.1"

organization in GlobalScope := "com.typesafe"

scalacOptions in GlobalScope in Compile := Seq("-unchecked", "-deprecation")

scalacOptions in GlobalScope in Test := Seq("-unchecked", "-deprecation")
