import sbt._, Keys._

// from https://raw.github.com/paulp/scala-improving/master/project/PublishToSonatype.scala
abstract class PublishToSonatype {
  val ossSnapshots = "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
  val ossStaging   = "Sonatype OSS Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/"

  def projectUrl: String
  def developerId: String
  def developerName: String
  def developerUrl: String

  def licenseName         = "Apache License, Version 2.0"
  def licenseUrl          = "https://www.apache.org/licenses/LICENSE-2.0"
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
    publishTo := Some(if (isSnapshot.value) ossSnapshots else ossStaging),
    publishArtifact in Test := false,
    pomIncludeRepository := (_ => false),
    pomExtra := generatePomExtra
  )
}
