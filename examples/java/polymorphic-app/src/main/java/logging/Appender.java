package logging;

import com.typesafe.config.ConfigSubTypes;
import com.typesafe.config.ConfigSubTypes.Type;
import com.typesafe.config.ConfigTypeInfo;

@ConfigTypeInfo
@ConfigSubTypes({
    @Type(value = ConsoleAppender.class, name = "console")
})
public interface Appender {

    void log(String event);
}
