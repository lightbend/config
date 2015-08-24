import sbt._
import Keys._
import plugins.JvmPlugin

object LinkSourcePlugin extends AutoPlugin {

  object autoImport {
    lazy val javadocSourceBaseUrl = settingKey[Option[String]]("base URL (no trailing slash) for source code")
  }

  import autoImport._

  override def trigger = allRequirements
  override def requires = JvmPlugin
  override lazy val projectSettings = Seq(
    javadocSourceBaseUrl := None,
    javacOptions in (Compile, doc) := {
      val old = (javacOptions in doc).value
      if (old.contains("-linksource"))
        old
      else
        "-linksource" +: old
    },
    (doc in Compile) := {
      val result = (doc in Compile).value

      val dir = (target in doc in Compile).value

      javadocSourceBaseUrl.value.foreach { url =>
        rewriteSourceLinks(dir, url, streams.value.log)
      }

      result
    }
  )


  private def rewriteSourceLinks(dir: File, sourceBaseUrl: String, log: Logger): Unit = {
    // Convert <a href="../../../src-html/com.twitter_typesafe/config/Config.html#line.165"> to
    // "https://github.com.twitter_typesafehub/config/blob/v1.2.1/config/src/main/java/com.twitter_typesafe/config/Config.java#L165"
    // in all .html files found underneath dir
    val origRegex = "href=\".*src-html/([^\"]+)\"".r
    def listFiles(d: File): Seq[File] = IO.listFiles(d).toSeq.flatMap { f =>
      if (f.isDirectory)
        listFiles(f)
      else
        Seq(f)
    }
    val htmlFiles = listFiles(dir).filter(_.getName.endsWith(".html"))
    for (f <- htmlFiles) {
      val content = IO.read(f)
      val changed = origRegex.replaceAllIn(content, { m: scala.util.matching.Regex.Match =>
        val oldFileLine = m.group(1)
        val fileLine = oldFileLine.replace("line.", "L").replace(".html", ".java")
        s""" href="$sourceBaseUrl/$fileLine" target="_blank" """
      })
      if (content != changed) {
        log.info(s"Replacing source links in $f")
        IO.write(f, changed)
      }
    }
  }
}
