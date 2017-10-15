import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import logging.Appender;

import java.util.List;

class PolymorphicApp {

    public static class LoggerConfiguration {

        private String level;
        private List<Appender> appenders;

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public List<Appender> getAppenders() {
            return appenders;
        }

        public void setAppenders(List<Appender> appenders) {
            this.appenders = appenders;
        }
    }

    public static void main(String[] args) {
        // This app is "polymorphic" because we have one interface type with
        // multiple implementations that are deserialized to correct type based
        // on the type defined in configuration.

        // Java version showcases two types of defining subtypes and naming
        // them: defining one subtype in the main interface, and one in the
        // 'META-INF/services' directory.

        Config config = ConfigFactory.load("polymorphic");

        LoggerConfiguration loggerConfiguration =
            ConfigBeanFactory.create(config.getConfig("polymorphic-app.logger"), LoggerConfiguration.class);

        String demoMessage = config.getString("polymorphic-app.demo-message");

        for (Appender appender : loggerConfiguration.getAppenders()) {
            appender.log(demoMessage);
        }
    }
}
