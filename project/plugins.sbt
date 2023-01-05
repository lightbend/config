addSbtPlugin("com.github.sbt" % "sbt-findbugs" % "2.0.0")
//addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.4.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.9.6")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

//addSbtPlugin("com.etsy" % "sbt-checkstyle-plugin" % "3.1.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.2")
addSbtPlugin("com.eed3si9n" % "sbt-nocomma" % "0.1.0")

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
