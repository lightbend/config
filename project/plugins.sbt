addSbtPlugin("de.johoop" % "findbugs4sbt" % "1.1.2")

libraryDependencies ++= Seq(
  "org.jacoco" % "org.jacoco.core" % "0.5.3.201107060350" artifacts(Artifact("org.jacoco.core", "jar", "jar")),
  "org.jacoco" % "org.jacoco.report" % "0.5.3.201107060350" artifacts(Artifact("org.jacoco.report", "jar", "jar")))

addSbtPlugin("de.johoop" % "jacoco4sbt" % "1.2.0")

