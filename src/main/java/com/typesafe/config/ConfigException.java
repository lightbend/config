package com.typesafe.config;

public class ConfigException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected ConfigException(ConfigOrigin origin, String message,
            Throwable cause) {
        super(origin.description() + ": " + message, cause);
    }

    protected ConfigException(ConfigOrigin origin, String message) {
        this(origin.description() + ": " + message, null);
    }

    protected ConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    protected ConfigException(String message) {
        this(message, null);
    }

    public static class WrongType extends ConfigException {
        private static final long serialVersionUID = 1L;

        public WrongType(ConfigOrigin origin, String path, String expected,
                String actual,
                Throwable cause) {
            super(origin, path + " has type " + actual + " rather than "
                    + expected,
                    cause);
        }

        public WrongType(ConfigOrigin origin, String path, String expected,
                String actual) {
            this(origin, path, expected, actual, null);
        }

        WrongType(ConfigOrigin origin, String message, Throwable cause) {
            super(origin, message, cause);
        }

        WrongType(ConfigOrigin origin, String message) {
            this(origin, message, null);
        }
    }

    /**
     * Exception indicates that the setting was never set to anything, not even
     * null.
     */
    public static class Missing extends ConfigException {
        private static final long serialVersionUID = 1L;

        public Missing(String path, Throwable cause) {
            super("No configuration setting found for key '" + path + "'",
                    cause);
        }

        public Missing(String path) {
            this(path, null);
        }

    }

    /**
     * Exception indicates that the type was wrong and specifically the value
     * was null instead of a real value.
     */
    public static class Null extends WrongType {
        private static final long serialVersionUID = 1L;

        public Null(ConfigOrigin origin, String path, String expected,
                Throwable cause) {
            super(origin, "Configuration key '" + path
                    + "' is set to null but expected " + expected, cause);
        }

        public Null(ConfigOrigin origin, String path, String expected) {
            this(origin, path, expected, null);
        }
    }

    public static class BadValue extends ConfigException {
        private static final long serialVersionUID = 1L;

        public BadValue(ConfigOrigin origin, String path, String message,
                Throwable cause) {
            super(origin, "Invalid value at '" + path + "': " + message, cause);
        }

        public BadValue(ConfigOrigin origin, String path, String message) {
            this(origin, path, message, null);
        }

        public BadValue(String path, String message, Throwable cause) {
            super("Invalid value at '" + path + "': " + message, cause);
        }

        public BadValue(String path, String message) {
            this(path, message, null);
        }
    }

    public static class BadPath extends ConfigException {
        private static final long serialVersionUID = 1L;

        public BadPath(ConfigOrigin origin, String path, String message,
                Throwable cause) {
            super(origin, "Invalid path '" + path + "': " + message, cause);
        }

        public BadPath(ConfigOrigin origin, String path, String message) {
            this(origin, path, message, null);
        }

        public BadPath(String path, String message, Throwable cause) {
            super("Invalid path '" + path + "': " + message, cause);
        }

        public BadPath(String path, String message) {
            this(path, message, null);
        }
    }

    /**
     * Exception indicating that there's a bug in something or the runtime
     * environment is broken. This exception should never be handled; instead,
     * something should be fixed to keep the exception from occurring.
     *
     */
    public static class BugOrBroken extends ConfigException {
        private static final long serialVersionUID = 1L;

        public BugOrBroken(String message, Throwable cause) {
            super(message, cause);
        }

        public BugOrBroken(String message) {
            this(message, null);
        }
    }

    public static class IO extends ConfigException {
        private static final long serialVersionUID = 1L;

        public IO(ConfigOrigin origin, String message, Throwable cause) {
            super(origin, message, cause);
        }

        public IO(ConfigOrigin origin, String message) {
            this(origin, message, null);
        }
    }

    public static class Parse extends ConfigException {
        private static final long serialVersionUID = 1L;

        public Parse(ConfigOrigin origin, String message, Throwable cause) {
            super(origin, message, cause);
        }

        public Parse(ConfigOrigin origin, String message) {
            this(origin, message, null);
        }
    }
}
