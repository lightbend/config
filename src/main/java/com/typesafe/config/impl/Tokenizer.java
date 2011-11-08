package com.typesafe.config.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;

final class Tokenizer {
    /**
     * Tokenizes a Reader. Does not close the reader; you have to arrange to do
     * that after you're done with the returned iterator.
     */
    static Iterator<Token> tokenize(ConfigOrigin origin, Reader input) {
        return new TokenIterator(origin, input);
    }

    private static class TokenIterator implements Iterator<Token> {

        private ConfigOrigin origin;
        private Reader input;
        private int oneCharBuffer;
        private int lineNumber;
        private Queue<Token> tokens;

        private int nextChar() {
            if (oneCharBuffer >= 0) {
                int c = oneCharBuffer;
                oneCharBuffer = -1;
                return c;
            } else {
                try {
                    return input.read();
                } catch (IOException e) {
                    throw new ConfigException.IO(origin, "read error: "
                            + e.getMessage(), e);
                }
            }
        }

        private void putBack(int c) {
            if (oneCharBuffer >= 0) {
                throw new ConfigException.BugOrBroken(
                        "bug: attempt to putBack() twice in a row");
            }
            oneCharBuffer = c;
        }

        private int nextCharAfterWhitespace() {
            for (;;) {
                int c = nextChar();

                if (c == -1) {
                    return -1;
                } else if (c == '\n') {
                    return c;
                } else if (Character.isWhitespace(c)) {
                    continue;
                } else {
                    return c;
                }
            }
        }

        private ConfigException parseError(String message) {
            return parseError(message, null);
        }

        private ConfigException parseError(String message, Throwable cause) {
            return new ConfigException.Parse(lineOrigin(), message, cause);
        }

        private ConfigOrigin lineOrigin() {
            return new SimpleConfigOrigin(origin.description() + ": line "
                    + lineNumber);
        }

        // chars JSON allows a number to start with
        static final String firstNumberChars = "0123456789-";
        // chars JSON allows to be part of a number
        static final String numberChars = "0123456789eE+-.";
        // chars that stop an unquoted string
        static final String notInUnquotedText = "$\"{}[]:=\n,";

        // The rules here are intended to maximize convenience while
        // avoiding confusion with real valid JSON. Basically anything
        // that parses as JSON is treated the JSON way and otherwise
        // we assume it's a string and let the parser sort it out.
        private Token pullUnquotedText() {
            ConfigOrigin origin = lineOrigin();
            StringBuilder sb = new StringBuilder();
            int c = nextChar();
            while (true) {
                if (c == -1) {
                    break;
                } else if (notInUnquotedText.indexOf(c) >= 0) {
                    break;
                } else {
                    sb.append((char) c);
                }

                // we parse true/false/null tokens as such no matter
                // what is after them.
                if (sb.length() == 4) {
                    String s = sb.toString();
                    if (s.equals("true"))
                        return Tokens.newBoolean(origin, true);
                    else if (s.equals("null"))
                        return Tokens.newNull(origin);
                } else if (sb.length() == 5) {
                    String s = sb.toString();
                    if (s.equals("false"))
                        return Tokens.newBoolean(origin, false);
                }

                c = nextChar();
            }

            // put back the char that ended the unquoted text
            putBack(c);

            // chop trailing whitespace; have to quote to have trailing spaces.
            String s = sb.toString().trim();
            return Tokens.newUnquotedText(origin, s);
        }

        private Token pullNumber(int firstChar) {
            StringBuilder sb = new StringBuilder();
            sb.append((char) firstChar);
            boolean containedDecimalOrE = false;
            int c = nextChar();
            while (c != -1 && numberChars.indexOf(c) >= 0) {
                if (c == '.' || c == 'e' || c == 'E')
                    containedDecimalOrE = true;
                sb.append((char) c);
                c = nextChar();
            }
            // the last character we looked at wasn't part of the number, put it
            // back
            putBack(c);
            String s = sb.toString();
            try {
                if (containedDecimalOrE) {
                    // force floating point representation
                    return Tokens
                            .newDouble(lineOrigin(), Double.parseDouble(s));
                } else {
                    // this should throw if the integer is too large for Long
                    return Tokens.newLong(lineOrigin(), Long.parseLong(s));
                }
            } catch (NumberFormatException e) {
                throw parseError("Invalid number", e);
            }
        }

        private void pullEscapeSequence(StringBuilder sb) {
            int escaped = nextChar();
            if (escaped == -1)
                throw parseError("End of input but backslash in string had nothing after it");

            switch (escaped) {
            case '"':
                sb.append('"');
                break;
            case '\\':
                sb.append('\\');
                break;
            case '/':
                sb.append('/');
                break;
            case 'b':
                sb.append('\b');
                break;
            case 'f':
                sb.append('\f');
                break;
            case 'n':
                sb.append('\n');
                break;
            case 'r':
                sb.append('\r');
                break;
            case 't':
                sb.append('\t');
                break;
            case 'u': {
                // kind of absurdly slow, but screw it for now
                char[] a = new char[4];
                for (int i = 0; i < 4; ++i) {
                    int c = nextChar();
                    if (c == -1)
                        throw parseError("End of input but expecting 4 hex digits for \\uXXXX escape");
                    a[i] = (char) c;
                }
                String digits = new String(a);
                try {
                    sb.appendCodePoint(Integer.parseInt(digits, 16));
                } catch (NumberFormatException e) {
                    throw parseError(
                            String.format(
                                    "Malformed hex digits after \\u escape in string: '%s'",
                                    digits), e);
                }
            }
                break;
            default:
                throw parseError(String
                        .format("backslash followed by '%c', this is not a valid escape sequence",
                                escaped));
            }
        }

        private Token pullQuotedString() {
            // the open quote has already been consumed
            StringBuilder sb = new StringBuilder();
            int c = '\0'; // value doesn't get used
            do {
                c = nextChar();
                if (c == -1)
                    throw parseError("End of input but string quote was still open");

                if (c == '\\') {
                    pullEscapeSequence(sb);
                } else if (c == '"') {
                    // end the loop, done!
                } else {
                    sb.append((char) c);
                }
            } while (c != '"');
            return Tokens.newString(lineOrigin(), sb.toString());
        }

        private void queueNextToken() {
            int c = nextCharAfterWhitespace();
            if (c == -1) {
                tokens.add(Tokens.END);
            } else if (c == '\n') {
                // newline tokens have the just-ended line number
                tokens.add(Tokens.newLine(lineNumber));
                lineNumber += 1;
            } else {
                Token t = null;
                switch (c) {
                case '"':
                    t = pullQuotedString();
                    break;
                case ':':
                    t = Tokens.COLON;
                    break;
                case ',':
                    t = Tokens.COMMA;
                    break;
                case '{':
                    t = Tokens.OPEN_CURLY;
                    break;
                case '}':
                    t = Tokens.CLOSE_CURLY;
                    break;
                case '[':
                    t = Tokens.OPEN_SQUARE;
                    break;
                case ']':
                    t = Tokens.CLOSE_SQUARE;
                    break;
                }

                if (t == null) {
                    if (firstNumberChars.indexOf(c) >= 0) {
                        t = pullNumber(c);
                    } else if (notInUnquotedText.indexOf(c) >= 0) {
                        throw parseError(String
                                .format("Character '%c' is not the start of any valid token",
                                        c));
                    } else {
                        putBack(c);
                        t = pullUnquotedText();
                    }
                }

                if (t == null)
                    throw new ConfigException.BugOrBroken(
                            "bug: failed to generate next token");
                tokens.add(t);
            }
        }

        TokenIterator(ConfigOrigin origin, Reader input) {
            this.origin = origin;
            this.input = input;
            oneCharBuffer = -1;
            lineNumber = 0;
            tokens = new LinkedList<Token>();
            tokens.add(Tokens.START);
        }

        @Override
        public boolean hasNext() {
            return !tokens.isEmpty();
        }

        @Override
        public Token next() {
            Token t = tokens.remove();
            if (t != Tokens.END) {
                queueNextToken();
                if (tokens.isEmpty())
                    throw new ConfigException.BugOrBroken(
                            "bug: tokens queue should not be empty here");
            }
            return t;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException(
                    "Does not make sense to remove items from token stream");
        }
    }
}
