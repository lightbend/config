import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
object Util {
    def time(body: () => Unit, iterations: Int): Long = {
        // warm up
        for (i <- 1 to 20) {
            body()
        }

        val start = System.currentTimeMillis()
        for (i <- 1 to iterations) {
            body()
        }
        val end = System.currentTimeMillis()
        (end - start) / iterations
    }

    def loop(args: Seq[String], body: () => Unit) {
        if (args.contains("-loop")) {
            println("looping; ctrl+C to escape")
            while (true) {
                body()
            }
        }
    }
}

object FileLoad extends App {
    def task() {
        val conf = ConfigFactory.load("test04")
        if (!"2.0-SNAPSHOT".equals(conf.getString("akka.version"))) {
            throw new Exception("broken file load")
        }
    }

    val ms = Util.time(task, 100)
    println("file load: " + ms + "ms")

    Util.loop(args, task)
}

object Resolve extends App {
    val conf = ConfigFactory.load("test02")

    def task() {
        conf.resolve()
        if (conf.getInt("103_a") != 103) {
            throw new Exception("broken file load")
        }
    }

    val ms = Util.time(task, 10000)
    println("resolve: " + ms + "ms")

    Util.loop(args, task)
}
