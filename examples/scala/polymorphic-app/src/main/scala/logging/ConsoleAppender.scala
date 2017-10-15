package logging

import com.typesafe.config.ConfigTypeName

import scala.beans.BeanProperty

@ConfigTypeName("console")
class ConsoleAppender extends Appender {

    @BeanProperty var target: String = _

    override def log(event: String) {
        printf("Logging to '%s' using console logger: %s\n", target, event)
    }
}
