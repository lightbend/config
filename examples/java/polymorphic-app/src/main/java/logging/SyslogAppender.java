package logging;

import com.typesafe.config.ConfigTypeName;

@ConfigTypeName("syslog")
public class SyslogAppender implements Appender {

    private String host;
    private int port;
    private String facility;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    @Override
    public void log(String event) {
        System.out.printf("Logging to '%s/%d/%s' using syslog logger: %s\n",
            host, port, facility, event);
    }
}
