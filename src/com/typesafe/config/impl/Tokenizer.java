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

        private void checkNextOrThrow(String expectedBefore, String expectedNow) {
            int i = 0;
            while (i < expectedNow.length()) {
                int expected = expectedNow.charAt(i);
                int actual = nextChar();

                if (actual == -1)
                    throw parseError(String.format(
                            "Expecting '%s%s' but input data ended",
                            expectedBefore, expectedNow));

                if (actual != expected)
                    throw parseError(String
                            .format("Expecting '%s%s' but got char '%c' rather than '%c'",
                                    expectedBefore, expectedNow, actual,
                                    expected));

                ++i;
            }
        }

        private ConfigOrigin lineOrigin() {
            return new SimpleConfigOrigin(origin.description() + ": line "
                    + lineNumber);
        }

        private Token pullTrue() {
            // "t" has been already seen
            checkNextOrThrow("t", "rue");
            return Tokens.newBoolean(lineOrigin(), true);
        }

        private Token pullFalse() {
            // "f" has been already seen
            checkNextOrThrow("f", "alse");
            return Tokens.newBoolean(lineOrigin(), false);
        }

        private Token pullNull() {
            // "n" has been already seen
            checkNextOrThrow("n", "ull");
            return Tokens.newNull(lineOrigin());
        }

        private Token pullNumber(int firstChar) {
            StringBuilder sb = new StringBuilder();
            sb.append((char) firstChar);
            boolean containedDecimalOrE = false;
            int c = nextChar();
            while (c != -1 && "0123456789eE+-.".indexOf(c) >= 0) {
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
                case 't':
                    t = pullTrue();
                    break;
                case 'f':
                    t = pullFalse();
                    break;
                case 'n':
                    t = pullNull();
                    break;
                }
                if (t == null) {
                    if ("-0123456789".indexOf(c) >= 0) {
                        t = pullNumber(c);
                    } else {
                        throw parseError(String
                                .format("Character '%c' is not the start of any valid token",
                                        c));
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
