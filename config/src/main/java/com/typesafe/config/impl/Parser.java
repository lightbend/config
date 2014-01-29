/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.File;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValueType;

final class Parser {

    static AbstractConfigValue parse(Iterator<Token> tokens,
            ConfigOrigin origin, ConfigParseOptions options,
            ConfigIncludeContext includeContext) {
        ParseContext context = new ParseContext(options.getSyntax(), origin, tokens,
                SimpleIncluder.makeFull(options.getIncluder()), includeContext);
        return context.parse();
    }

    static private final class TokenWithComments {
        final Token token;
        final List<Token> comments;

        TokenWithComments(Token token, List<Token> comments) {
            this.token = token;
            this.comments = comments;

            if (Tokens.isComment(token))
                throw new ConfigException.BugOrBroken("tried to annotate a comment with a comment");
        }

        TokenWithComments(Token token) {
            this(token, Collections.<Token> emptyList());
        }

        TokenWithComments removeAll() {
            if (comments.isEmpty())
                return this;
            else
                return new TokenWithComments(token);
        }

        TokenWithComments prepend(List<Token> earlier) {
            if (earlier.isEmpty()) {
                return this;
            } else if (this.comments.isEmpty()) {
                return new TokenWithComments(token, earlier);
            } else {
                List<Token> merged = new ArrayList<Token>();
                merged.addAll(earlier);
                merged.addAll(comments);
                return new TokenWithComments(token, merged);
            }
        }

        TokenWithComments add(Token after) {
            if (this.comments.isEmpty()) {
                return new TokenWithComments(token, Collections.<Token> singletonList(after));
            } else {
                List<Token> merged = new ArrayList<Token>();
                merged.addAll(comments);
                merged.add(after);
                return new TokenWithComments(token, merged);
            }
        }

        SimpleConfigOrigin prependComments(SimpleConfigOrigin origin) {
            if (comments.isEmpty()) {
                return origin;
            } else {
                List<String> newComments = new ArrayList<String>();
                for (Token c : comments) {
                    newComments.add(Tokens.getCommentText(c));
                }
                return origin.prependComments(newComments);
            }
        }

        SimpleConfigOrigin appendComments(SimpleConfigOrigin origin) {
            if (comments.isEmpty()) {
                return origin;
            } else {
                List<String> newComments = new ArrayList<String>();
                for (Token c : comments) {
                    newComments.add(Tokens.getCommentText(c));
                }
                return origin.appendComments(newComments);
            }
        }

        @Override
        public String toString() {
            // this ends up in user-visible error messages, so we don't want the
            // comments
            return token.toString();
        }
    }

    static private final class ParseContext {
        private int lineNumber;
        final private Stack<TokenWithComments> buffer;
        final private Iterator<Token> tokens;
        final private FullIncluder includer;
        final private ConfigIncludeContext includeContext;
        final private ConfigSyntax flavor;
        final private ConfigOrigin baseOrigin;
        final private LinkedList<Path> pathStack;
        // this is the number of "equals" we are inside,
        // used to modify the error message to reflect that
        // someone may think this is .properties format.
        int equalsCount;

        ParseContext(ConfigSyntax flavor, ConfigOrigin origin, Iterator<Token> tokens,
                FullIncluder includer, ConfigIncludeContext includeContext) {
            lineNumber = 1;
            buffer = new Stack<TokenWithComments>();
            this.tokens = tokens;
            this.flavor = flavor;
            this.baseOrigin = origin;
            this.includer = includer;
            this.includeContext = includeContext;
            this.pathStack = new LinkedList<Path>();
            this.equalsCount = 0;
        }

        static private boolean attractsTrailingComments(Token token) {
            // END can't have a trailing comment; START, OPEN_CURLY, and
            // OPEN_SQUARE followed by a comment should behave as if the comment
            // went with the following field or element. Associating a comment
            // with a newline would mess up all the logic for comment tracking,
            // so don't do that either.
            if (Tokens.isNewline(token) || token == Tokens.START || token == Tokens.OPEN_CURLY
                    || token == Tokens.OPEN_SQUARE || token == Tokens.END)
                return false;
            else
                return true;
        }

        static private boolean attractsLeadingComments(Token token) {
            // a comment just before a close } generally doesn't go with the
            // value before it, unless it's on the same line as that value
            if (Tokens.isNewline(token) || token == Tokens.START || token == Tokens.CLOSE_CURLY
                    || token == Tokens.CLOSE_SQUARE || token == Tokens.END)
                return false;
            else
                return true;
        }

        private void consolidateCommentBlock(Token commentToken) {
            // a comment block "goes with" the following token
            // unless it's separated from it by a blank line.
            // we want to build a list of newline tokens followed
            // by a non-newline non-comment token; with all comments
            // associated with that final non-newline non-comment token.
            // a comment AFTER a token, without an intervening newline,
            // also goes with that token, but isn't handled in this method,
            // instead we handle it later by peeking ahead.
            List<Token> newlines = new ArrayList<Token>();
            List<Token> comments = new ArrayList<Token>();

            Token previous = null;
            Token next = commentToken;
            while (true) {
                if (Tokens.isNewline(next)) {
                    if (previous != null && Tokens.isNewline(previous)) {
                        // blank line; drop all comments to this point and
                        // start a new comment block
                        comments.clear();
                    }
                    newlines.add(next);
                } else if (Tokens.isComment(next)) {
                    comments.add(next);
                } else {
                    // a non-newline non-comment token

                    // comments before a close brace or bracket just get dumped
                    if (!attractsLeadingComments(next))
                        comments.clear();

                    break;
                }

                previous = next;
                next = tokens.next();
            }

            // put our concluding token in the queue with all the comments
            // attached
            buffer.push(new TokenWithComments(next, comments));

            // now put all the newlines back in front of it
            ListIterator<Token> li = newlines.listIterator(newlines.size());
            while (li.hasPrevious()) {
                buffer.push(new TokenWithComments(li.previous()));
            }
        }

        private TokenWithComments popTokenWithoutTrailingComment() {
            if (buffer.isEmpty()) {
                Token t = tokens.next();
                if (Tokens.isComment(t)) {
                    consolidateCommentBlock(t);
                    return buffer.pop();
                } else {
                    return new TokenWithComments(t);
                }
            } else {
                return buffer.pop();
            }
        }

        private TokenWithComments popToken() {
            TokenWithComments withPrecedingComments = popTokenWithoutTrailingComment();
            // handle a comment AFTER the other token,
            // but before a newline. If the next token is not
            // a comment, then any comment later on the line is irrelevant
            // since it would end up going with that later token, not
            // this token. Comments are supposed to be processed prior
            // to adding stuff to the buffer, so they can only be found
            // in "tokens" not in "buffer" in theory.
            if (!attractsTrailingComments(withPrecedingComments.token)) {
                return withPrecedingComments;
            } else if (buffer.isEmpty()) {
                Token after = tokens.next();
                if (Tokens.isComment(after)) {
                    return withPrecedingComments.add(after);
                } else {
                    buffer.push(new TokenWithComments(after));
                    return withPrecedingComments;
                }
            } else {
                // comments are supposed to get attached to a token,
                // not put back in the buffer. Assert this as an invariant.
                if (Tokens.isComment(buffer.peek().token))
                    throw new ConfigException.BugOrBroken(
                            "comment token should not have been in buffer: " + buffer);
                return withPrecedingComments;
            }
        }

        private TokenWithComments nextToken() {
            TokenWithComments withComments = null;

            withComments = popToken();
            Token t = withComments.token;

            if (Tokens.isProblem(t)) {
                ConfigOrigin origin = t.origin();
                String message = Tokens.getProblemMessage(t);
                Throwable cause = Tokens.getProblemCause(t);
                boolean suggestQuotes = Tokens.getProblemSuggestQuotes(t);
                if (suggestQuotes) {
                    message = addQuoteSuggestion(t.toString(), message);
                } else {
                    message = addKeyName(message);
                }
                throw new ConfigException.Parse(origin, message, cause);
            } else {
                if (flavor == ConfigSyntax.JSON) {
                    if (Tokens.isUnquotedText(t)) {
                        throw parseError(addKeyName("Token not allowed in valid JSON: '"
                                + Tokens.getUnquotedText(t) + "'"));
                    } else if (Tokens.isSubstitution(t)) {
                        throw parseError(addKeyName("Substitutions (${} syntax) not allowed in JSON"));
                    }
                }

                return withComments;
            }
        }

        private void putBack(TokenWithComments token) {
            if (Tokens.isComment(token.token))
                throw new ConfigException.BugOrBroken(
                        "comment token should have been stripped before it was available to put back");
            buffer.push(token);
        }

        private TokenWithComments nextTokenIgnoringNewline() {
            TokenWithComments t = nextToken();

            while (Tokens.isNewline(t.token)) {
                // line number tokens have the line that was _ended_ by the
                // newline, so we have to add one. We have to update lineNumber
                // here and also below, because not all tokens store a line
                // number, but newline tokens always do.
                lineNumber = t.token.lineNumber() + 1;

                t = nextToken();
            }

            // update line number again, iff we have one
            int newNumber = t.token.lineNumber();
            if (newNumber >= 0)
                lineNumber = newNumber;

            return t;
        }

        private AbstractConfigValue addAnyCommentsAfterAnyComma(AbstractConfigValue v) {
            TokenWithComments t = nextToken(); // do NOT skip newlines, we only
                                               // want same-line comments
            if (t.token == Tokens.COMMA) {
                // steal the comments from after the comma
                putBack(t.removeAll());
                return v.withOrigin(t.appendComments(v.origin()));
            } else {
                putBack(t);
                return v;
            }
        }

        // In arrays and objects, comma can be omitted
        // as long as there's at least one newline instead.
        // this skips any newlines in front of a comma,
        // skips the comma, and returns true if it found
        // either a newline or a comma. The iterator
        // is left just after the comma or the newline.
        private boolean checkElementSeparator() {
            if (flavor == ConfigSyntax.JSON) {
                TokenWithComments t = nextTokenIgnoringNewline();
                if (t.token == Tokens.COMMA) {
                    return true;
                } else {
                    putBack(t);
                    return false;
                }
            } else {
                boolean sawSeparatorOrNewline = false;
                TokenWithComments t = nextToken();
                while (true) {
                    if (Tokens.isNewline(t.token)) {
                        // newline number is the line just ended, so add one
                        lineNumber = t.token.lineNumber() + 1;
                        sawSeparatorOrNewline = true;

                        // we want to continue to also eat
                        // a comma if there is one.
                    } else if (t.token == Tokens.COMMA) {
                        return true;
                    } else {
                        // non-newline-or-comma
                        putBack(t);
                        return sawSeparatorOrNewline;
                    }
                    t = nextToken();
                }
            }
        }

        private static SubstitutionExpression tokenToSubstitutionExpression(Token valueToken) {
            List<Token> expression = Tokens.getSubstitutionPathExpression(valueToken);
            Path path = parsePathExpression(expression.iterator(), valueToken.origin());
            boolean optional = Tokens.getSubstitutionOptional(valueToken);

            return new SubstitutionExpression(path, optional);
        }

        // merge a bunch of adjacent values into one
        // value; change unquoted text into a string
        // value.
        private void consolidateValueTokens() {
            // this trick is not done in JSON
            if (flavor == ConfigSyntax.JSON)
                return;

            // create only if we have value tokens
            List<AbstractConfigValue> values = null;

            // ignore a newline up front
            TokenWithComments t = nextTokenIgnoringNewline();
            while (true) {
                AbstractConfigValue v = null;
                if (Tokens.isValue(t.token) || Tokens.isUnquotedText(t.token)
                        || Tokens.isSubstitution(t.token) || t.token == Tokens.OPEN_CURLY
                        || t.token == Tokens.OPEN_SQUARE) {
                    // there may be newlines _within_ the objects and arrays
                    v = parseValue(t);
                } else {
                    break;
                }

                if (v == null)
                    throw new ConfigException.BugOrBroken("no value");

                if (values == null) {
                    values = new ArrayList<AbstractConfigValue>();
                }
                values.add(v);

                t = nextToken(); // but don't consolidate across a newline
            }
            // the last one wasn't a value token
            putBack(t);

            if (values == null)
                return;

            AbstractConfigValue consolidated = ConfigConcatenation.concatenate(values);

            putBack(new TokenWithComments(Tokens.newValue(consolidated)));
        }

        private SimpleConfigOrigin lineOrigin() {
            return ((SimpleConfigOrigin) baseOrigin).setLineNumber(lineNumber);
        }

        private ConfigException parseError(String message) {
            return parseError(message, null);
        }

        private ConfigException parseError(String message, Throwable cause) {
            return new ConfigException.Parse(lineOrigin(), message, cause);
        }

        private String previousFieldName(Path lastPath) {
            if (lastPath != null) {
                return lastPath.render();
            } else if (pathStack.isEmpty())
                return null;
            else
                return pathStack.peek().render();
        }

        private Path fullCurrentPath() {
            Path full = null;
            // pathStack has top of stack at front
            for (Path p : pathStack) {
                if (full == null)
                    full = p;
                else
                    full = full.prepend(p);
            }
            return full;
        }

        private String previousFieldName() {
            return previousFieldName(null);
        }

        private String addKeyName(String message) {
            String previousFieldName = previousFieldName();
            if (previousFieldName != null) {
                return "in value for key '" + previousFieldName + "': " + message;
            } else {
                return message;
            }
        }

        private String addQuoteSuggestion(String badToken, String message) {
            return addQuoteSuggestion(null, equalsCount > 0, badToken, message);
        }

        private String addQuoteSuggestion(Path lastPath, boolean insideEquals, String badToken,
                String message) {
            String previousFieldName = previousFieldName(lastPath);

            String part;
            if (badToken.equals(Tokens.END.toString())) {
                // EOF requires special handling for the error to make sense.
                if (previousFieldName != null)
                    part = message + " (if you intended '" + previousFieldName
                            + "' to be part of a value, instead of a key, "
                            + "try adding double quotes around the whole value";
                else
                    return message;
            } else {
                if (previousFieldName != null) {
                    part = message + " (if you intended " + badToken
                            + " to be part of the value for '" + previousFieldName + "', "
                            + "try enclosing the value in double quotes";
                } else {
                    part = message + " (if you intended " + badToken
                            + " to be part of a key or string value, "
                            + "try enclosing the key or value in double quotes";
                }
            }

            if (insideEquals)
                return part
                        + ", or you may be able to rename the file .properties rather than .conf)";
            else
                return part + ")";
        }

        private AbstractConfigValue parseValue(TokenWithComments t) {
            AbstractConfigValue v;

            if (Tokens.isValue(t.token)) {
                // if we consolidateValueTokens() multiple times then
                // this value could be a concatenation, object, array,
                // or substitution already.
                v = Tokens.getValue(t.token);
            } else if (Tokens.isUnquotedText(t.token)) {
                v = new ConfigString(t.token.origin(), Tokens.getUnquotedText(t.token));
            } else if (Tokens.isSubstitution(t.token)) {
                v = new ConfigReference(t.token.origin(), tokenToSubstitutionExpression(t.token));
            } else if (t.token == Tokens.OPEN_CURLY) {
                v = parseObject(true);
            } else if (t.token == Tokens.OPEN_SQUARE) {
                v = parseArray();
            } else {
                throw parseError(addQuoteSuggestion(t.token.toString(),
                        "Expecting a value but got wrong token: " + t.token));
            }

            v = v.withOrigin(t.prependComments(v.origin()));

            return v;
        }

        private static AbstractConfigObject createValueUnderPath(Path path,
                AbstractConfigValue value) {
            // for path foo.bar, we are creating
            // { "foo" : { "bar" : value } }
            List<String> keys = new ArrayList<String>();

            String key = path.first();
            Path remaining = path.remainder();
            while (key != null) {
                keys.add(key);
                if (remaining == null) {
                    break;
                } else {
                    key = remaining.first();
                    remaining = remaining.remainder();
                }
            }

            // the setComments(null) is to ensure comments are only
            // on the exact leaf node they apply to.
            // a comment before "foo.bar" applies to the full setting
            // "foo.bar" not also to "foo"
            ListIterator<String> i = keys.listIterator(keys.size());
            String deepest = i.previous();
            AbstractConfigObject o = new SimpleConfigObject(value.origin().setComments(null),
                    Collections.<String, AbstractConfigValue> singletonMap(
                            deepest, value));
            while (i.hasPrevious()) {
                Map<String, AbstractConfigValue> m = Collections.<String, AbstractConfigValue> singletonMap(
                        i.previous(), o);
                o = new SimpleConfigObject(value.origin().setComments(null), m);
            }

            return o;
        }

        private Path parseKey(TokenWithComments token) {
            if (flavor == ConfigSyntax.JSON) {
                if (Tokens.isValueWithType(token.token, ConfigValueType.STRING)) {
                    String key = (String) Tokens.getValue(token.token).unwrapped();
                    return Path.newKey(key);
                } else {
                    throw parseError(addKeyName("Expecting close brace } or a field name here, got "
                            + token));
                }
            } else {
                List<Token> expression = new ArrayList<Token>();
                TokenWithComments t = token;
                while (Tokens.isValue(t.token) || Tokens.isUnquotedText(t.token)) {
                    expression.add(t.token);
                    t = nextToken(); // note: don't cross a newline
                }

                if (expression.isEmpty()) {
                    throw parseError(addKeyName("expecting a close brace or a field name here, got "
                            + t));
                }

                putBack(t); // put back the token we ended with
                return parsePathExpression(expression.iterator(), lineOrigin());
            }
        }

        private static boolean isIncludeKeyword(Token t) {
            return Tokens.isUnquotedText(t)
                    && Tokens.getUnquotedText(t).equals("include");
        }

        private static boolean isUnquotedWhitespace(Token t) {
            if (!Tokens.isUnquotedText(t))
                return false;

            String s = Tokens.getUnquotedText(t);

            for (int i = 0; i < s.length(); ++i) {
                char c = s.charAt(i);
                if (!ConfigImplUtil.isWhitespace(c))
                    return false;
            }
            return true;
        }

        private void parseInclude(Map<String, AbstractConfigValue> values) {
            TokenWithComments t = nextTokenIgnoringNewline();
            while (isUnquotedWhitespace(t.token)) {
                t = nextTokenIgnoringNewline();
            }

            AbstractConfigObject obj;

            // we either have a quoted string or the "file()" syntax
            if (Tokens.isUnquotedText(t.token)) {
                // get foo(
                String kind = Tokens.getUnquotedText(t.token);

                if (kind.equals("url(")) {

                } else if (kind.equals("file(")) {

                } else if (kind.equals("classpath(")) {

                } else {
                    throw parseError("expecting include parameter to be quoted filename, file(), classpath(), or url(). No spaces are allowed before the open paren. Not expecting: "
                            + t);
                }

                // skip space inside parens
                t = nextTokenIgnoringNewline();
                while (isUnquotedWhitespace(t.token)) {
                    t = nextTokenIgnoringNewline();
                }

                // quoted string
                String name;
                if (Tokens.isValueWithType(t.token, ConfigValueType.STRING)) {
                    name = (String) Tokens.getValue(t.token).unwrapped();
                } else {
                    throw parseError("expecting a quoted string inside file(), classpath(), or url(), rather than: "
                            + t);
                }
                // skip space after string, inside parens
                t = nextTokenIgnoringNewline();
                while (isUnquotedWhitespace(t.token)) {
                    t = nextTokenIgnoringNewline();
                }

                if (Tokens.isUnquotedText(t.token) && Tokens.getUnquotedText(t.token).equals(")")) {
                    // OK, close paren
                } else {
                    throw parseError("expecting a close parentheses ')' here, not: " + t);
                }

                if (kind.equals("url(")) {
                    URL url;
                    try {
                        url = new URL(name);
                    } catch (MalformedURLException e) {
                        throw parseError("include url() specifies an invalid URL: " + name, e);
                    }
                    obj = (AbstractConfigObject) includer.includeURL(includeContext, url);
                } else if (kind.equals("file(")) {
                    obj = (AbstractConfigObject) includer.includeFile(includeContext,
                            new File(name));
                } else if (kind.equals("classpath(")) {
                    obj = (AbstractConfigObject) includer.includeResources(includeContext, name);
                } else {
                    throw new ConfigException.BugOrBroken("should not be reached");
                }
            } else if (Tokens.isValueWithType(t.token, ConfigValueType.STRING)) {
                String name = (String) Tokens.getValue(t.token).unwrapped();
                obj = (AbstractConfigObject) includer
                        .include(includeContext, name);
            } else {
                throw parseError("include keyword is not followed by a quoted string, but by: " + t);
            }

            if (!pathStack.isEmpty()) {
                // The stack is in reverse order (most recent first on the
                // iterator), so build the path from the reversed iterator.
                Path prefix = new Path(pathStack.descendingIterator());
                obj = obj.relativized(prefix);
            }

            for (String key : obj.keySet()) {
                AbstractConfigValue v = obj.get(key);
                AbstractConfigValue existing = values.get(key);
                if (existing != null) {
                    values.put(key, v.withFallback(existing));
                } else {
                    values.put(key, v);
                }
            }
        }

        private boolean isKeyValueSeparatorToken(Token t) {
            if (flavor == ConfigSyntax.JSON) {
                return t == Tokens.COLON;
            } else {
                return t == Tokens.COLON || t == Tokens.EQUALS || t == Tokens.PLUS_EQUALS;
            }
        }

        private AbstractConfigObject parseObject(boolean hadOpenCurly) {
            // invoked just after the OPEN_CURLY (or START, if !hadOpenCurly)
            Map<String, AbstractConfigValue> values = new HashMap<String, AbstractConfigValue>();
            SimpleConfigOrigin objectOrigin = lineOrigin();
            boolean afterComma = false;
            Path lastPath = null;
            boolean lastInsideEquals = false;

            while (true) {
                TokenWithComments t = nextTokenIgnoringNewline();
                if (t.token == Tokens.CLOSE_CURLY) {
                    if (flavor == ConfigSyntax.JSON && afterComma) {
                        throw parseError(addQuoteSuggestion(t.toString(),
                                "expecting a field name after a comma, got a close brace } instead"));
                    } else if (!hadOpenCurly) {
                        throw parseError(addQuoteSuggestion(t.toString(),
                                "unbalanced close brace '}' with no open brace"));
                    }

                    objectOrigin = t.appendComments(objectOrigin);

                    break;
                } else if (t.token == Tokens.END && !hadOpenCurly) {
                    putBack(t);
                    break;
                } else if (flavor != ConfigSyntax.JSON && isIncludeKeyword(t.token)) {
                    parseInclude(values);

                    afterComma = false;
                } else {
                    TokenWithComments keyToken = t;
                    Path path = parseKey(keyToken);
                    TokenWithComments afterKey = nextTokenIgnoringNewline();
                    boolean insideEquals = false;

                    // path must be on-stack while we parse the value
                    pathStack.push(path);

                    TokenWithComments valueToken;
                    AbstractConfigValue newValue;
                    if (flavor == ConfigSyntax.CONF && afterKey.token == Tokens.OPEN_CURLY) {
                        // can omit the ':' or '=' before an object value
                        valueToken = afterKey;
                    } else {
                        if (!isKeyValueSeparatorToken(afterKey.token)) {
                            throw parseError(addQuoteSuggestion(afterKey.toString(),
                                    "Key '" + path.render() + "' may not be followed by token: "
                                            + afterKey));
                        }

                        if (afterKey.token == Tokens.EQUALS) {
                            insideEquals = true;
                            equalsCount += 1;
                        }

                        consolidateValueTokens();
                        valueToken = nextTokenIgnoringNewline();
                        // put comments from separator token on the value token
                        valueToken = valueToken.prepend(afterKey.comments);
                    }

                    // comments from the key token go to the value token
                    newValue = parseValue(valueToken.prepend(keyToken.comments));

                    if (afterKey.token == Tokens.PLUS_EQUALS) {
                        List<AbstractConfigValue> concat = new ArrayList<AbstractConfigValue>(2);
                        AbstractConfigValue previousRef = new ConfigReference(newValue.origin(),
                                new SubstitutionExpression(fullCurrentPath(), true /* optional */));
                        AbstractConfigValue list = new SimpleConfigList(newValue.origin(),
                                Collections.singletonList(newValue));
                        concat.add(previousRef);
                        concat.add(list);
                        newValue = ConfigConcatenation.concatenate(concat);
                    }

                    newValue = addAnyCommentsAfterAnyComma(newValue);

                    lastPath = pathStack.pop();
                    if (insideEquals) {
                        equalsCount -= 1;
                    }
                    lastInsideEquals = insideEquals;

                    String key = path.first();
                    Path remaining = path.remainder();

                    if (remaining == null) {
                        AbstractConfigValue existing = values.get(key);
                        if (existing != null) {
                            // In strict JSON, dups should be an error; while in
                            // our custom config language, they should be merged
                            // if the value is an object (or substitution that
                            // could become an object).

                            if (flavor == ConfigSyntax.JSON) {
                                throw parseError("JSON does not allow duplicate fields: '"
                                    + key
                                    + "' was already seen at "
                                    + existing.origin().description());
                            } else {
                                newValue = newValue.withFallback(existing);
                            }
                        }
                        values.put(key, newValue);
                    } else {
                        if (flavor == ConfigSyntax.JSON) {
                            throw new ConfigException.BugOrBroken(
                                    "somehow got multi-element path in JSON mode");
                        }

                        AbstractConfigObject obj = createValueUnderPath(
                                remaining, newValue);
                        AbstractConfigValue existing = values.get(key);
                        if (existing != null) {
                            obj = obj.withFallback(existing);
                        }
                        values.put(key, obj);
                    }

                    afterComma = false;
                }

                if (checkElementSeparator()) {
                    // continue looping
                    afterComma = true;
                } else {
                    t = nextTokenIgnoringNewline();
                    if (t.token == Tokens.CLOSE_CURLY) {
                        if (!hadOpenCurly) {
                            throw parseError(addQuoteSuggestion(lastPath, lastInsideEquals,
                                    t.toString(), "unbalanced close brace '}' with no open brace"));
                        }

                        objectOrigin = t.appendComments(objectOrigin);

                        break;
                    } else if (hadOpenCurly) {
                        throw parseError(addQuoteSuggestion(lastPath, lastInsideEquals,
                                t.toString(), "Expecting close brace } or a comma, got " + t));
                    } else {
                        if (t.token == Tokens.END) {
                            putBack(t);
                            break;
                        } else {
                            throw parseError(addQuoteSuggestion(lastPath, lastInsideEquals,
                                    t.toString(), "Expecting end of input or a comma, got " + t));
                        }
                    }
                }
            }

            return new SimpleConfigObject(objectOrigin, values);
        }

        private SimpleConfigList parseArray() {
            // invoked just after the OPEN_SQUARE
            SimpleConfigOrigin arrayOrigin = lineOrigin();
            List<AbstractConfigValue> values = new ArrayList<AbstractConfigValue>();

            consolidateValueTokens();

            TokenWithComments t = nextTokenIgnoringNewline();

            // special-case the first element
            if (t.token == Tokens.CLOSE_SQUARE) {
                return new SimpleConfigList(t.appendComments(arrayOrigin),
                        Collections.<AbstractConfigValue> emptyList());
            } else if (Tokens.isValue(t.token) || t.token == Tokens.OPEN_CURLY
                    || t.token == Tokens.OPEN_SQUARE) {
                AbstractConfigValue v = parseValue(t);
                v = addAnyCommentsAfterAnyComma(v);
                values.add(v);
            } else {
                throw parseError(addKeyName("List should have ] or a first element after the open [, instead had token: "
                        + t
                        + " (if you want "
                        + t
                        + " to be part of a string value, then double-quote it)"));
            }

            // now remaining elements
            while (true) {
                // just after a value
                if (checkElementSeparator()) {
                    // comma (or newline equivalent) consumed
                } else {
                    t = nextTokenIgnoringNewline();
                    if (t.token == Tokens.CLOSE_SQUARE) {
                        return new SimpleConfigList(t.appendComments(arrayOrigin), values);
                    } else {
                        throw parseError(addKeyName("List should have ended with ] or had a comma, instead had token: "
                                + t
                                + " (if you want "
                                + t
                                + " to be part of a string value, then double-quote it)"));
                    }
                }

                // now just after a comma
                consolidateValueTokens();

                t = nextTokenIgnoringNewline();
                if (Tokens.isValue(t.token) || t.token == Tokens.OPEN_CURLY
                        || t.token == Tokens.OPEN_SQUARE) {
                    AbstractConfigValue v = parseValue(t);
                    v = addAnyCommentsAfterAnyComma(v);
                    values.add(v);
                } else if (flavor != ConfigSyntax.JSON && t.token == Tokens.CLOSE_SQUARE) {
                    // we allow one trailing comma
                    putBack(t);
                } else {
                    throw parseError(addKeyName("List should have had new element after a comma, instead had token: "
                            + t
                            + " (if you want the comma or "
                            + t
                            + " to be part of a string value, then double-quote it)"));
                }
            }
        }

        AbstractConfigValue parse() {
            TokenWithComments t = nextTokenIgnoringNewline();
            if (t.token == Tokens.START) {
                // OK
            } else {
                throw new ConfigException.BugOrBroken(
                        "token stream did not begin with START, had " + t);
            }

            t = nextTokenIgnoringNewline();
            AbstractConfigValue result = null;
            if (t.token == Tokens.OPEN_CURLY || t.token == Tokens.OPEN_SQUARE) {
                result = parseValue(t);
            } else {
                if (flavor == ConfigSyntax.JSON) {
                    if (t.token == Tokens.END) {
                        throw parseError("Empty document");
                    } else {
                        throw parseError("Document must have an object or array at root, unexpected token: "
                                + t);
                    }
                } else {
                    // the root object can omit the surrounding braces.
                    // this token should be the first field's key, or part
                    // of it, so put it back.
                    putBack(t);
                    result = parseObject(false);
                    // in this case we don't try to use commentsStack comments
                    // since they would all presumably apply to fields not the
                    // root object
                }
            }

            t = nextTokenIgnoringNewline();
            if (t.token == Tokens.END) {
                return result;
            } else {
                throw parseError("Document has trailing tokens after first object or array: "
                        + t);
            }
        }
    }

    static class Element {
        StringBuilder sb;
        // an element can be empty if it has a quoted empty string "" in it
        boolean canBeEmpty;

        Element(String initial, boolean canBeEmpty) {
            this.canBeEmpty = canBeEmpty;
            this.sb = new StringBuilder(initial);
        }

        @Override
        public String toString() {
            return "Element(" + sb.toString() + "," + canBeEmpty + ")";
        }
    }

    private static void addPathText(List<Element> buf, boolean wasQuoted,
            String newText) {
        int i = wasQuoted ? -1 : newText.indexOf('.');
        Element current = buf.get(buf.size() - 1);
        if (i < 0) {
            // add to current path element
            current.sb.append(newText);
            // any empty quoted string means this element can
            // now be empty.
            if (wasQuoted && current.sb.length() == 0)
                current.canBeEmpty = true;
        } else {
            // "buf" plus up to the period is an element
            current.sb.append(newText.substring(0, i));
            // then start a new element
            buf.add(new Element("", false));
            // recurse to consume remainder of newText
            addPathText(buf, false, newText.substring(i + 1));
        }
    }

    private static Path parsePathExpression(Iterator<Token> expression,
            ConfigOrigin origin) {
        return parsePathExpression(expression, origin, null);
    }

    // originalText may be null if not available
    private static Path parsePathExpression(Iterator<Token> expression,
            ConfigOrigin origin, String originalText) {
        // each builder in "buf" is an element in the path.
        List<Element> buf = new ArrayList<Element>();
        buf.add(new Element("", false));

        if (!expression.hasNext()) {
            throw new ConfigException.BadPath(origin, originalText,
                    "Expecting a field name or path here, but got nothing");
        }

        while (expression.hasNext()) {
            Token t = expression.next();
            if (Tokens.isValueWithType(t, ConfigValueType.STRING)) {
                AbstractConfigValue v = Tokens.getValue(t);
                // this is a quoted string; so any periods
                // in here don't count as path separators
                String s = v.transformToString();

                addPathText(buf, true, s);
            } else if (t == Tokens.END) {
                // ignore this; when parsing a file, it should not happen
                // since we're parsing a token list rather than the main
                // token iterator, and when parsing a path expression from the
                // API, it's expected to have an END.
            } else {
                // any periods outside of a quoted string count as
                // separators
                String text;
                if (Tokens.isValue(t)) {
                    // appending a number here may add
                    // a period, but we _do_ count those as path
                    // separators, because we basically want
                    // "foo 3.0bar" to parse as a string even
                    // though there's a number in it. The fact that
                    // we tokenize non-string values is largely an
                    // implementation detail.
                    AbstractConfigValue v = Tokens.getValue(t);
                    text = v.transformToString();
                } else if (Tokens.isUnquotedText(t)) {
                    text = Tokens.getUnquotedText(t);
                } else {
                    throw new ConfigException.BadPath(
                            origin,
                            originalText,
                            "Token not allowed in path expression: "
                                    + t
                                    + " (you can double-quote this token if you really want it here)");
                }

                addPathText(buf, false, text);
            }
        }

        PathBuilder pb = new PathBuilder();
        for (Element e : buf) {
            if (e.sb.length() == 0 && !e.canBeEmpty) {
                throw new ConfigException.BadPath(
                        origin,
                        originalText,
                        "path has a leading, trailing, or two adjacent period '.' (use quoted \"\" empty string if you want an empty element)");
            } else {
                pb.appendKey(e.sb.toString());
            }
        }

        return pb.result();
    }

    static ConfigOrigin apiOrigin = SimpleConfigOrigin.newSimple("path parameter");

    static Path parsePath(String path) {
        Path speculated = speculativeFastParsePath(path);
        if (speculated != null)
            return speculated;

        StringReader reader = new StringReader(path);

        try {
            Iterator<Token> tokens = Tokenizer.tokenize(apiOrigin, reader,
                    ConfigSyntax.CONF);
            tokens.next(); // drop START
            return parsePathExpression(tokens, apiOrigin, path);
        } finally {
            reader.close();
        }
    }

    // the idea is to see if the string has any chars that might require the
    // full parser to deal with.
    private static boolean hasUnsafeChars(String s) {
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (Character.isLetter(c) || c == '.')
                continue;
            else
                return true;
        }
        return false;
    }

    private static void appendPathString(PathBuilder pb, String s) {
        int splitAt = s.indexOf('.');
        if (splitAt < 0) {
            pb.appendKey(s);
        } else {
            pb.appendKey(s.substring(0, splitAt));
            appendPathString(pb, s.substring(splitAt + 1));
        }
    }

    // do something much faster than the full parser if
    // we just have something like "foo" or "foo.bar"
    private static Path speculativeFastParsePath(String path) {
        String s = ConfigImplUtil.unicodeTrim(path);
        if (s.isEmpty())
            return null;
        if (hasUnsafeChars(s))
            return null;
        if (s.startsWith(".") || s.endsWith(".") || s.contains(".."))
            return null; // let the full parser throw the error

        PathBuilder pb = new PathBuilder();
        appendPathString(pb, s);
        return pb.result();
    }
}
