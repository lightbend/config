package com.typesafe.config.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigSyntax;

/**
 * This is public but it's only for use by the config package; DO NOT TOUCH. The
 * point of this class is to avoid "propagating" each overload on
 * "thing which can be parsed" through multiple interfaces. Most interfaces can
 * have just one overload that takes a Parseable.
 */
public abstract class Parseable {
    protected Parseable() {
    }

    // the general idea is that any work should be in here, not in the
    // constructor,
    // so that exceptions are thrown from the public parse() function and not
    // from the creation of the Parseable. Essentially this is a lazy field.
    // The parser should close the reader when it's done with it.
    // ALSO, IMPORTANT: if the file or URL is not found, this must throw.
    // to support the "allow missing" feature.
    abstract Reader reader() throws IOException;

    ConfigSyntax guessSyntax() {
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private static ConfigSyntax syntaxFromExtension(String name) {
        if (name.endsWith(".json"))
            return ConfigSyntax.JSON;
        else if (name.endsWith(".conf"))
            return ConfigSyntax.CONF;
        else if (name.endsWith(".properties"))
            return ConfigSyntax.PROPERTIES;
        else
            return null;
    }

    private static Reader readerFromStream(InputStream input) {
        try {
            // well, this is messed up. If we aren't going to close
            // the passed-in InputStream then we have no way to
            // close these readers. So maybe we should not have an
            // InputStream version, only a Reader version.
            Reader reader = new InputStreamReader(input, "UTF-8");
            return new BufferedReader(reader);
        } catch (UnsupportedEncodingException e) {
            throw new ConfigException.BugOrBroken(
                    "Java runtime does not support UTF-8", e);
        }
    }

    private static Reader doNotClose(Reader input) {
        return new FilterReader(input) {
            @Override
            public void close() {
                // NOTHING.
            }
        };
    }

    private final static class ParseableInputStream extends Parseable {
        final private InputStream input;

        ParseableInputStream(InputStream input) {
            this.input = input;
        }

        @Override
        Reader reader() {
            return doNotClose(readerFromStream(input));
        }
    }

    /**
     * note that we will never close this stream; you have to do it when parsing
     * is complete.
     */
    public static Parseable newInputStream(InputStream input) {
        return new ParseableInputStream(input);
    }

    private final static class ParseableReader extends Parseable {
        final private Reader reader;

        ParseableReader(Reader reader) {
            this.reader = reader;
        }

        @Override
        Reader reader() {
            return reader;
        }
    }

    /**
     * note that we will never close this reader; you have to do it when parsing
     * is complete.
     */
    public static Parseable newReader(Reader reader) {
        return new ParseableReader(doNotClose(reader));
    }

    private final static class ParseableString extends Parseable {
        final private String input;

        ParseableString(String input) {
            this.input = input;
        }

        @Override
        Reader reader() {
            return new StringReader(input);
        }
    }

    public static Parseable newString(String input) {
        return new ParseableString(input);
    }

    private final static class ParseableURL extends Parseable {
        final private URL input;

        ParseableURL(URL input) {
            this.input = input;
        }

        @Override
        Reader reader() throws IOException {
            InputStream stream = input.openStream();
            return readerFromStream(stream);
        }

        @Override
        ConfigSyntax guessSyntax() {
            return syntaxFromExtension(input.getPath());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + input.toExternalForm()
                    + ")";
        }
    }

    public static Parseable newURL(URL input) {
        return new ParseableURL(input);
    }

    private final static class ParseableFile extends Parseable {
        final private File input;

        ParseableFile(File input) {
            this.input = input;
        }

        @Override
        Reader reader() throws IOException {
            InputStream stream = new FileInputStream(input);
            return readerFromStream(stream);
        }

        @Override
        ConfigSyntax guessSyntax() {
            return syntaxFromExtension(input.getName());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + input.getPath() + ")";
        }
    }

    public static Parseable newFile(File input) {
        return new ParseableFile(input);
    }

    private final static class ParseableResource extends Parseable {
        final private Class<?> klass;
        final private String resource;

        ParseableResource(Class<?> klass, String resource) {
            this.klass = klass;
            this.resource = resource;
        }

        @Override
        Reader reader() throws IOException {
            InputStream stream = klass.getResourceAsStream(resource);
            return readerFromStream(stream);
        }

        @Override
        ConfigSyntax guessSyntax() {
            return syntaxFromExtension(resource);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + resource + ","
                    + klass.getName()
                    + ")";
        }
    }

    public static Parseable newResource(Class<?> klass, String resource) {
        return new ParseableResource(klass, resource);
    }
}
