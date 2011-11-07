package com.typesafe.config.impl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;

final class Parser {
    /**
     * Parses an input stream, which must be in UTF-8 encoding and should be
     * buffered. Does not close the stream; you have to arrange to do that
     * yourself.
     */
    static AbstractConfigObject parse(ConfigOrigin origin, InputStream input) {
        try {
            return parse(origin, new InputStreamReader(input, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new ConfigException.BugOrBroken(
                    "Java runtime does not support UTF-8");
        }
    }

    static AbstractConfigObject parse(ConfigOrigin origin,
            Reader input) {
        Iterator<Tokens.Token> tokens = Tokenizer.tokenize(origin, input);
        return parse(origin, tokens);
    }

    private static AbstractConfigObject parse(ConfigOrigin origin,
            Iterator<Tokens.Token> tokens) {
        return null; // FIXME
    }
}
