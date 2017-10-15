package logging

import com.typesafe.config.ConfigTypeInfo

@ConfigTypeInfo
trait Appender {

    def log(event: String)
}
