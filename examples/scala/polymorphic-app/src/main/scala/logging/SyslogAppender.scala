package logging

import com.typesafe.config.ConfigTypeName

import scala.beans.BeanProperty

@ConfigTypeName("syslog")
class SyslogAppender extends Appender {

    @BeanProperty var host: String = _
    @BeanProperty var port: Int = _
    @BeanProperty var facility: String = _

    override def log(event: String) {
        printf("Logging to '%s/%d/%s' using syslog logger: %s\n", host, port, facility, event)
    }
}
