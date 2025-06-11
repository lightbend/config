package com.typesafe.config;

import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

public class SystemOverride {

    /** Runs the specified synchronous (blocking) operation
     * guaranteeing that all SystemOverride methods return the specified values
     * while the operation is being executed.
     * <p>
     * The configuration library only ever accesses System through SystemOverride,
     * so this method is useful whenever you want to influence the configuration resolution
     * through altering the environment variables (also system properties and standard error stream) programmatically.
     *
     * @param systemProperties replacement for the system properties
     * @param environmentVariables replacement for the environment variables
     * @param errorStream replacement for the standard error stream
     *
     * @return T the result of the specified operation
     * @throws RuntimeException in case the specified operation throws
     * */
    public static <T> T withSystemOverride(
            Map<String, String> systemProperties,
            Map<String, String> environmentVariables,
            PrintStream errorStream,
            Supplier<T> configurationAccessOperation) {
        SystemImplementation overridden = new OverriddenSystemImplementation(
                systemProperties,
                environmentVariables,
                errorStream);
        try {
            current.set(overridden);
            return configurationAccessOperation.get();
        } finally {
            current.set(real);
        }
    }

    public static Properties getProperties() {
        return current.get().getProperties();
    }

    public static String getProperty(String propertyKey) {
        return current.get().getProperty(propertyKey);
    }

    public static String getenv(String environmentVariableName) {
        return current.get().getenv(environmentVariableName);
    }

    public static Map<String, String> getenv() {
        return current.get().getenv();
    }

    public static PrintStream err() {
        return current.get().err();
    }

    private static interface SystemImplementation {
        Properties getProperties();

        String getProperty(String propertyKey);

        String getenv(String environmentVariableName);

        Map<String, String> getenv();

        PrintStream err();

    }

    private static class LiveSystemImplementation implements SystemImplementation {
        public Properties getProperties() {
            return System.getProperties();
        }

        public String getProperty(String propertyKey) {
            return System.getProperty(propertyKey);
        }

        public String getenv(String environmentVariableName) {
            return System.getenv(environmentVariableName);
        }

        public Map<String, String> getenv() {
            return System.getenv();
        }

        public PrintStream err() {
            return System.err;
        }


    }

    private static class OverriddenSystemImplementation implements SystemImplementation {

        private final Map<String, String> systemProperties;
        private final Map<String, String> environmentVariables;
        private final PrintStream errorStream;

        private OverriddenSystemImplementation(Map<String, String> systemProperties, Map<String, String> environmentVariables, PrintStream errorStream) {
            this.systemProperties = systemProperties;
            this.environmentVariables = environmentVariables;
            this.errorStream = errorStream;
        }

        public Properties getProperties() {
            Properties result = new Properties();
            result.putAll(systemProperties);
            return result;
        }

        public String getProperty(String propertyKey) {
            return systemProperties.get(propertyKey);
        }

        public String getenv(String environmentVariableName) {
            return environmentVariables.get(environmentVariableName);
        }

        public Map<String, String> getenv() {
            return environmentVariables;
        }

        public PrintStream err() {
            return errorStream;
        }
    }

    private static final SystemImplementation real = new LiveSystemImplementation();

    private static final ThreadLocal<SystemImplementation> current = ThreadLocal.withInitial(() -> real);

}
