package logging;

public class ConsoleAppender implements Appender {

    private String target;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public void log(String event) {
        System.out.printf("Logging to '%s' using console logger: %s\n",
            target, event);
    }
}
