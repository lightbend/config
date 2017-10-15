import com.typesafe.config._
import logging.Appender

import scala.beans.BeanProperty
import scala.collection.JavaConversions._

object PolymorphicApp extends App {

    class LoggerConfiguration {
        @BeanProperty var level: String = _
        @BeanProperty var appenders: java.util.List[Appender] = _
    }

    // This app is "polymorphic" because we have one interface type with
    // multiple implementations that are deserialized to correct type based
    // on the type defined in configuration.

    // Scala version relies on 'META-INF/services' directory for subtype
    // discovery due to the fact that annotations have some Java specific
    // elements.

    val config = ConfigFactory.load("polymorphic")

    val loggerConfiguration =
        ConfigBeanFactory.create(config.getConfig("polymorphic-app.logger"), classOf[LoggerConfiguration])

    val demoMessage = config.getString("polymorphic-app.demo-message")

    loggerConfiguration.getAppenders.toList.foreach(a => a.log(demoMessage))
}
