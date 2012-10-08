import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions

object RenderExample extends App {
    val formatted = args.contains("--formatted")
    val originComments = args.contains("--origin-comments")
    val comments = args.contains("--comments")
    val hocon = args.contains("--hocon")
    val options = ConfigRenderOptions.defaults()
        .setFormatted(formatted)
        .setOriginComments(originComments)
        .setComments(comments)
        .setJson(!hocon)

    def render(what: String) {
        val conf = ConfigFactory.defaultOverrides()
            .withFallback(ConfigFactory.parseResourcesAnySyntax(classOf[ConfigFactory], "/" + what))
            .withFallback(ConfigFactory.defaultReference())

        println("=== BEGIN UNRESOLVED " + what)
        println(conf.root.render(options))
        println("=== END UNRESOLVED " + what)

        println("=== BEGIN RESOLVED " + what)
        println(conf.resolve().root.render(options))
        println("=== END RESOLVED " + what)

        println("=== BEGIN UNRESOLVED toString() " + what)
        println(conf.root.toString())
        println("=== END UNRESOLVED toString() " + what)
    }

    render("test01")
    render("test06")
    render("test05")
}
