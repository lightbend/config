package com.typesafe.config.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

final class Parser {
    /**
     * Parses an input stream, which must be in UTF-8 encoding and should be
     * buffered. Does not close the stream; you have to arrange to do that
     * yourself.
     */
    static AbstractConfigValue parse(SyntaxFlavor flavor, ConfigOrigin origin,
            InputStream input) {
        try {
            return parse(flavor, origin, new InputStreamReader(input, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new ConfigException.BugOrBroken(
                    "Java runtime does not support UTF-8");
        }
    }

    static AbstractConfigValue parse(SyntaxFlavor flavor, ConfigOrigin origin,
            Reader input) {
        Iterator<Token> tokens = Tokenizer.tokenize(origin, input);
        return parse(flavor, origin, tokens);
    }

    static AbstractConfigValue parse(SyntaxFlavor flavor, ConfigOrigin origin,
            String input) {
        return parse(flavor, origin, new StringReader(input));
    }

    static AbstractConfigValue parse(File f) {
        ConfigOrigin origin = new SimpleConfigOrigin(f.getPath());
        SyntaxFlavor flavor = null;
        if (f.getName().endsWith(".json"))
            flavor = SyntaxFlavor.JSON;
        else if (f.getName().endsWith(".conf"))
            flavor = SyntaxFlavor.HOCON;
        else
            throw new ConfigException.IO(origin, "Unknown filename extension");
        AbstractConfigValue result = null;
        try {
            InputStream stream = new BufferedInputStream(new FileInputStream(f));
            result = parse(flavor, origin, stream);
            stream.close();
        } catch (IOException e) {
            throw new ConfigException.IO(origin, "failed to read file", e);
        }
        return result;
    }

    static private final class ParseContext {
        private int lineNumber;
        private SyntaxFlavor flavor;
        private ConfigOrigin baseOrigin;

        ParseContext(SyntaxFlavor flavor, ConfigOrigin origin) {
            lineNumber = 0;
            this.flavor = flavor;
            this.baseOrigin = origin;
        }

        private Token nextTokenIgnoringNewline(Iterator<Token> tokens) {
            Token t = tokens.next();
            while (Tokens.isNewline(t)) {
                lineNumber = Tokens.getLineNumber(t);
                t = tokens.next();
            }
            return t;
        }

        private ConfigOrigin lineOrigin() {
            return new SimpleConfigOrigin(baseOrigin.description() + ": line "
                    + lineNumber);
        }

        private ConfigException parseError(String message) {
            return parseError(message, null);
        }

        private ConfigException parseError(String message, Throwable cause) {
            return new ConfigException.Parse(lineOrigin(), message, cause);
        }

        private AbstractConfigValue parseValue(Token token,
                Iterator<Token> tokens) {
            if (Tokens.isValue(token)) {
                return Tokens.getValue(token);
            } else if (token == Tokens.OPEN_CURLY) {
                return parseObject(tokens);
            } else if (token == Tokens.OPEN_SQUARE) {
                return parseArray(tokens);
            } else {
                throw parseError("Expecting a value but got wrong token: "
                        + token);
            }
        }

        private AbstractConfigObject parseObject(Iterator<Token> tokens) {
            // invoked just after the OPEN_CURLY
            Map<String, ConfigValue> values = new HashMap<String, ConfigValue>();
            ConfigOrigin objectOrigin = lineOrigin();
            while (true) {
                Token t = nextTokenIgnoringNewline(tokens);
                if (Tokens.isValueWithType(t, ConfigValueType.STRING)) {
                    String key = (String) Tokens.getValue(t).unwrapped();
                    Token afterKey = nextTokenIgnoringNewline(tokens);
                    if (afterKey != Tokens.COLON) {
                        throw parseError("Key not followed by a colon, followed by token "
                                + afterKey);
                    }
                    Token valueToken = nextTokenIgnoringNewline(tokens);

                    // note how we handle duplicate keys: the last one just
                    // wins.
                    // FIXME in strict JSON, dups should be an error; while in
                    // our custom config language, they should be merged if the
                    // value is an object.
                    values.put(key, parseValue(valueToken, tokens));
                } else if (t == Tokens.CLOSE_CURLY) {
                    break;
                } else {
                    throw parseError("Expecting close brace } or a field name, got "
                            + t);
                }
                t = nextTokenIgnoringNewline(tokens);
                if (t == Tokens.CLOSE_CURLY) {
                    break;
                } else if (t == Tokens.COMMA) {
                    // continue looping
                } else {
                    throw parseError("Expecting close brace } or a comma, got "
                            + t);
                }
            }
            return new SimpleConfigObject(objectOrigin, null, values);
        }

        private ConfigList parseArray(Iterator<Token> tokens) {
            // invoked just after the OPEN_SQUARE
            ConfigOrigin arrayOrigin = lineOrigin();
            List<ConfigValue> values = new ArrayList<ConfigValue>();
            Token t = nextTokenIgnoringNewline(tokens);

            // special-case the first element
            if (t == Tokens.CLOSE_SQUARE) {
                return new ConfigList(arrayOrigin,
                        Collections.<ConfigValue> emptyList());
            } else if (Tokens.isValue(t)) {
                values.add(parseValue(t, tokens));
            } else if (t == Tokens.OPEN_CURLY) {
                values.add(parseObject(tokens));
            } else if (t == Tokens.OPEN_SQUARE) {
                values.add(parseArray(tokens));
            } else {
                throw parseError("List should have ] or a first element after the open [, instead had token: "
                        + t);
            }

            // now remaining elements
            while (true) {
                // just after a value
                t = nextTokenIgnoringNewline(tokens);
                if (t == Tokens.CLOSE_SQUARE) {
                    return new ConfigList(arrayOrigin, values);
                } else if (t == Tokens.COMMA) {
                    // OK
                } else {
                    throw parseError("List should have ended with ] or had a comma, instead had token: "
                            + t);
                }

                // now just after a comma
                t = nextTokenIgnoringNewline(tokens);
                if (Tokens.isValue(t)) {
                    values.add(parseValue(t, tokens));
                } else if (t == Tokens.OPEN_CURLY) {
                    values.add(parseObject(tokens));
                } else if (t == Tokens.OPEN_SQUARE) {
                    values.add(parseArray(tokens));
                } else {
                    throw parseError("List should have had new element after a comma, instead had token: "
                            + t);
                }
            }
        }

        AbstractConfigValue parse(Iterator<Token> tokens) {
            Token t = nextTokenIgnoringNewline(tokens);
            if (t == Tokens.START) {
                // OK
            } else {
                throw new ConfigException.BugOrBroken(
                        "token stream did not begin with START, had " + t);
            }

            t = nextTokenIgnoringNewline(tokens);
            AbstractConfigValue result = null;
            if (t == Tokens.OPEN_CURLY) {
                result = parseObject(tokens);
            } else if (t == Tokens.OPEN_SQUARE) {
                result = parseArray(tokens);
            } else if (t == Tokens.END) {
                throw parseError("Empty document");
            } else {
                throw parseError("Document must have an object or array at root, unexpected token: "
                        + t);
            }

            t = nextTokenIgnoringNewline(tokens);
            if (t == Tokens.END) {
                return result;
            } else {
                throw parseError("Document has trailing tokens after first object or array: "
                        + t);
            }
        }
    }

    private static AbstractConfigValue parse(SyntaxFlavor flavor,
            ConfigOrigin origin,
            Iterator<Token> tokens) {
        ParseContext context = new ParseContext(flavor, origin);
        return context.parse(tokens);
    }
}
