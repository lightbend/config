/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.*;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValueType;

final class ConfigDocumentParser {
    static ConfigNodeComplexValue parse(Iterator<Token> tokens, ConfigParseOptions options) {
        ParseContext context = new ParseContext(options.getSyntax(), tokens);
        return context.parse();
    }

    static ConfigNodeComplexValue parse(Iterator<Token> tokens) {
        ParseContext context = new ParseContext(ConfigSyntax.CONF, tokens);
        return context.parse();
    }

    static AbstractConfigNodeValue parseValue(Iterator<Token> tokens, ConfigParseOptions options) {
        ParseContext context = new ParseContext(options.getSyntax(), tokens);
        return context.parseSingleValue();
    }

    static private final class ParseContext {
        final private Stack<Token> buffer;
        final private Iterator<Token> tokens;
        final private ConfigSyntax flavor;
        final private LinkedList<Path> pathStack;
        // this is the number of "equals" we are inside,
        // used to modify the error message to reflect that
        // someone may think this is .properties format.
        int equalsCount;
        // the number of lists we are inside; this is used to detect the "cannot
        // generate a reference to a list element" problem, and once we fix that
        // problem we should be able to get rid of this variable.
        int arrayCount;

        ParseContext(ConfigSyntax flavor, Iterator<Token> tokens) {
            buffer = new Stack<Token>();
            this.tokens = tokens;
            this.flavor = flavor;
            this.pathStack = new LinkedList<Path>();
            this.equalsCount = 0;
            this.arrayCount = 0;
        }

        private Token popToken() {
            if (buffer.isEmpty()) {
                return tokens.next();
            }
            return buffer.pop();
        }

        private Token nextToken() {
            Token t = popToken();
            if (flavor == ConfigSyntax.JSON) {
                if (Tokens.isUnquotedText(t) && !isUnquotedWhitespace(t)) {
                    throw parseError(addKeyName("Token not allowed in valid JSON: '"
                            + Tokens.getUnquotedText(t) + "'"));
                } else if (Tokens.isSubstitution(t)) {
                    throw parseError(addKeyName("Substitutions (${} syntax) not allowed in JSON"));
                }
            }
            return t;
        }

        private Token nextTokenIgnoringWhitespace(Collection<AbstractConfigNode> nodes) {
            while (true) {
                Token t = nextToken();
                if (Tokens.isIgnoredWhitespace(t) || Tokens.isComment(t) || Tokens.isNewline(t) || isUnquotedWhitespace(t)) {
                    nodes.add(new ConfigNodeSingleToken(t));
                } else {
                    return t;
                }
            }
        }

        private void putBack(Token token) {
            buffer.push(token);
        }

        // In arrays and objects, comma can be omitted
        // as long as there's at least one newline instead.
        // this skips any newlines in front of a comma,
        // skips the comma, and returns true if it found
        // either a newline or a comma. The iterator
        // is left just after the comma or the newline.
        private boolean checkElementSeparator(Collection<AbstractConfigNode> nodes) {
            if (flavor == ConfigSyntax.JSON) {
                Token t = nextTokenIgnoringWhitespace(nodes);
                if (t == Tokens.COMMA) {
                    nodes.add(new ConfigNodeSingleToken(t));
                    return true;
                } else {
                    putBack(t);
                    return false;
                }
            } else {
                boolean sawSeparatorOrNewline = false;
                Token t = nextToken();
                while (true) {
                    if (Tokens.isIgnoredWhitespace(t) || isUnquotedWhitespace(t)) {
                        //do nothing
                    } else if (Tokens.isNewline(t)) {
                        sawSeparatorOrNewline = true;

                        // we want to continue to also eat
                        // a comma if there is one.
                    } else if (t == Tokens.COMMA) {
                        nodes.add(new ConfigNodeSingleToken(t));
                        return true;
                    } else {
                        // non-newline-or-comma
                        putBack(t);
                        return sawSeparatorOrNewline;
                    }
                    nodes.add(new ConfigNodeSingleToken(t));
                    t = nextToken();
                }
            }
        }

        // parse a concatenation. If there is no concatenation, return the next value
        private AbstractConfigNodeValue consolidateValues(Collection<AbstractConfigNode> nodes) {
            // this trick is not done in JSON
            if (flavor == ConfigSyntax.JSON)
                return null;

            // create only if we have value tokens
            ArrayList<AbstractConfigNode> values = new ArrayList<AbstractConfigNode>();
            int valueCount = 0;

            // ignore a newline up front
            Token t = nextTokenIgnoringWhitespace(nodes);
            while (true) {
                AbstractConfigNodeValue v = null;
                if (Tokens.isIgnoredWhitespace(t) || isUnquotedWhitespace(t)) {
                    values.add(new ConfigNodeSingleToken(t));
                    t = nextToken();
                    continue;
                }
                else if (Tokens.isValue(t) || Tokens.isUnquotedText(t)
                        || Tokens.isSubstitution(t) || t == Tokens.OPEN_CURLY
                        || t == Tokens.OPEN_SQUARE) {
                    // there may be newlines _within_ the objects and arrays
                    v = parseValue(t);
                    valueCount++;
                } else {
                    break;
                }

                if (v == null)
                    throw new ConfigException.BugOrBroken("no value");

                values.add(v);

                t = nextToken(); // but don't consolidate across a newline
            }

            putBack(t);

            // No concatenation was seen, but a single value may have been parsed, so return it, and put back
            // all succeeding tokens
            if (valueCount < 2) {
                AbstractConfigNodeValue value = null;
                for (AbstractConfigNode node : values) {
                    if (node instanceof AbstractConfigNodeValue)
                        value = (AbstractConfigNodeValue)node;
                    else if (node == null)
                        nodes.add(node);
                    else
                        putBack((new ArrayList<Token>(node.tokens())).get(0));
                }
                return value;
            }

            return new ConfigNodeConcatenation(values);
        }

        private ConfigException parseError(String message) {
            return parseError(message, null);
        }

        private ConfigException parseError(String message, Throwable cause) {
            return new ConfigException.Parse(SimpleConfigOrigin.newSimple(""), message, cause);
        }

        private String previousFieldName(Path lastPath) {
            if (lastPath != null) {
                return lastPath.render();
            } else if (pathStack.isEmpty())
                return null;
            else
                return pathStack.peek().render();
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

        private AbstractConfigNodeValue parseValue(Token t) {
            AbstractConfigNodeValue v = null;

            int startingArrayCount = arrayCount;
            int startingEqualsCount = equalsCount;

            if (Tokens.isValue(t) || Tokens.isUnquotedText(t) || Tokens.isSubstitution(t)) {
                v = new ConfigNodeSimpleValue(t);
            } else if (t == Tokens.OPEN_CURLY) {
                v = parseObject(true);
            } else if (t== Tokens.OPEN_SQUARE) {
                v = parseArray();
            } else {
                throw parseError(addQuoteSuggestion(t.toString(),
                        "Expecting a value but got wrong token: " + t));
            }

            if (arrayCount != startingArrayCount)
                throw new ConfigException.BugOrBroken("Bug in config parser: unbalanced array count");
            if (equalsCount != startingEqualsCount)
                throw new ConfigException.BugOrBroken("Bug in config parser: unbalanced equals count");

            return v;
        }

        private ConfigNodePath parseKey(Token token) {
            if (flavor == ConfigSyntax.JSON) {
                if (Tokens.isValueWithType(token, ConfigValueType.STRING)) {
                    return PathParser.parsePathNodeExpression(Collections.singletonList(token).iterator(), null);
                } else {
                    throw parseError(addKeyName("Expecting close brace } or a field name here, got "
                            + token));
                }
            } else {
                List<Token> expression = new ArrayList<Token>();
                Token t = token;
                while (Tokens.isValue(t) || Tokens.isUnquotedText(t)) {
                    expression.add(t);
                    t = nextToken(); // note: don't cross a newline
                }

                if (expression.isEmpty()) {
                    throw parseError(addKeyName("expecting a close brace or a field name here, got "
                            + t));
                }

                putBack(t); // put back the token we ended with
                return PathParser.parsePathNodeExpression(expression.iterator(), null);
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

        private boolean isKeyValueSeparatorToken(Token t) {
            if (flavor == ConfigSyntax.JSON) {
                return t == Tokens.COLON;
            } else {
                return t == Tokens.COLON || t == Tokens.EQUALS || t == Tokens.PLUS_EQUALS;
            }
        }

        private void parseInclude(ArrayList<AbstractConfigNode> children) {
            Token t = nextTokenIgnoringWhitespace(children);

            // we either have a quoted string or the "file()" syntax
            if (Tokens.isUnquotedText(t)) {
                // get foo(
                String kind = Tokens.getUnquotedText(t);

                if (kind.equals("url(")) {

                } else if (kind.equals("file(")) {

                } else if (kind.equals("classpath(")) {

                } else {
                    throw parseError("expecting include parameter to be quoted filename, file(), classpath(), or url(). No spaces are allowed before the open paren. Not expecting: "
                            + t);
                }

                children.add(new ConfigNodeSingleToken(t));

                // skip space inside parens
                t = nextTokenIgnoringWhitespace(children);

                // quoted string
                String name;
                if (!Tokens.isValueWithType(t, ConfigValueType.STRING)) {
                    throw parseError("expecting a quoted string inside file(), classpath(), or url(), rather than: "
                            + t);
                }
                children.add(new ConfigNodeSingleToken(t));
                // skip space after string, inside parens
                t = nextTokenIgnoringWhitespace(children);

                if (Tokens.isUnquotedText(t) && Tokens.getUnquotedText(t).equals(")")) {
                    // OK, close paren
                } else {
                    throw parseError("expecting a close parentheses ')' here, not: " + t);
                }

            } else if (Tokens.isValueWithType(t, ConfigValueType.STRING)) {
                children.add(new ConfigNodeSimpleValue(t));
            } else {
                throw parseError("include keyword is not followed by a quoted string, but by: " + t);
            }
        }

        private ConfigNodeComplexValue parseObject(boolean hadOpenCurly) {
            // invoked just after the OPEN_CURLY (or START, if !hadOpenCurly)
            boolean afterComma = false;
            Path lastPath = null;
            boolean lastInsideEquals = false;
            ArrayList<AbstractConfigNode> objectNodes = new ArrayList<AbstractConfigNode>();
            ArrayList<AbstractConfigNode> keyValueNodes;
            HashMap<String, Boolean> keys  = new HashMap();
            if (hadOpenCurly)
                objectNodes.add(new ConfigNodeSingleToken(Tokens.OPEN_CURLY));

            while (true) {
                Token t = nextTokenIgnoringWhitespace(objectNodes);
                if (t == Tokens.CLOSE_CURLY) {
                    if (flavor == ConfigSyntax.JSON && afterComma) {
                        throw parseError(addQuoteSuggestion(t.toString(),
                                "expecting a field name after a comma, got a close brace } instead"));
                    } else if (!hadOpenCurly) {
                        throw parseError(addQuoteSuggestion(t.toString(),
                                "unbalanced close brace '}' with no open brace"));
                    }
                    objectNodes.add(new ConfigNodeSingleToken(Tokens.CLOSE_CURLY));
                    break;
                } else if (t == Tokens.END && !hadOpenCurly) {
                    putBack(t);
                    break;
                } else if (flavor != ConfigSyntax.JSON && isIncludeKeyword(t)) {
                    objectNodes.add(new ConfigNodeSingleToken(t));
                    parseInclude(objectNodes);

                    afterComma = false;
                } else {
                    keyValueNodes = new ArrayList<AbstractConfigNode>();
                    Token keyToken = t;
                    ConfigNodePath path = parseKey(keyToken);
                    keyValueNodes.add(path);
                    Token afterKey = nextTokenIgnoringWhitespace(keyValueNodes);
                    boolean insideEquals = false;

                    Token valueToken;
                    AbstractConfigNodeValue nextValue;
                    if (flavor == ConfigSyntax.CONF && afterKey == Tokens.OPEN_CURLY) {
                        // can omit the ':' or '=' before an object value
                        nextValue = parseValue(afterKey);
                    } else {
                        if (!isKeyValueSeparatorToken(afterKey)) {
                            throw parseError(addQuoteSuggestion(afterKey.toString(),
                                    "Key '" + path.render() + "' may not be followed by token: "
                                            + afterKey));
                        }

                        keyValueNodes.add(new ConfigNodeSingleToken(afterKey));

                        if (afterKey == Tokens.EQUALS) {
                            insideEquals = true;
                            equalsCount += 1;
                        }

                        nextValue = consolidateValues(keyValueNodes);
                        if (nextValue == null) {
                            nextValue = parseValue(nextTokenIgnoringWhitespace(keyValueNodes));
                        }
                    }

                    keyValueNodes.add(nextValue);
                    if (insideEquals) {
                        equalsCount -= 1;
                    }
                    lastInsideEquals = insideEquals;

                    String key = path.value().first();
                    Path remaining = path.value().remainder();

                    if (remaining == null) {
                        Boolean existing = keys.get(key);
                        if (existing != null) {
                            // In strict JSON, dups should be an error; while in
                            // our custom config language, they should be merged
                            // if the value is an object (or substitution that
                            // could become an object).

                            if (flavor == ConfigSyntax.JSON) {
                                throw parseError("JSON does not allow duplicate fields: '"
                                        + key
                                        + "' was already seen");
                            }
                        }
                        keys.put(key, true);
                    } else {
                        if (flavor == ConfigSyntax.JSON) {
                            throw new ConfigException.BugOrBroken(
                                    "somehow got multi-element path in JSON mode");
                        }
                        keys.put(key, true);
                    }

                    afterComma = false;
                    objectNodes.add(new ConfigNodeField(keyValueNodes));
                }

                if (checkElementSeparator(objectNodes)) {
                    // continue looping
                    afterComma = true;
                } else {
                    t = nextTokenIgnoringWhitespace(objectNodes);
                    if (t == Tokens.CLOSE_CURLY) {
                        if (!hadOpenCurly) {
                            throw parseError(addQuoteSuggestion(lastPath, lastInsideEquals,
                                    t.toString(), "unbalanced close brace '}' with no open brace"));
                        }
                        objectNodes.add(new ConfigNodeSingleToken(t));
                        break;
                    } else if (hadOpenCurly) {
                        throw parseError(addQuoteSuggestion(lastPath, lastInsideEquals,
                                t.toString(), "Expecting close brace } or a comma, got " + t));
                    } else {
                        if (t == Tokens.END) {
                            putBack(t);
                            break;
                        } else {
                            throw parseError(addQuoteSuggestion(lastPath, lastInsideEquals,
                                    t.toString(), "Expecting end of input or a comma, got " + t));
                        }
                    }
                }
            }

            return new ConfigNodeObject(objectNodes);
        }

        private ConfigNodeComplexValue parseArray() {
            ArrayList<AbstractConfigNode> children = new ArrayList<AbstractConfigNode>();
            children.add(new ConfigNodeSingleToken(Tokens.OPEN_SQUARE));
            // invoked just after the OPEN_SQUARE
            arrayCount += 1;
            Token t;

            AbstractConfigNodeValue nextValue = consolidateValues(children);
            if (nextValue != null) {
                children.add(nextValue);
                nextValue = null;
            } else {
                t = nextTokenIgnoringWhitespace(children);

                // special-case the first element
                if (t == Tokens.CLOSE_SQUARE) {
                    arrayCount -= 1;
                    children.add(new ConfigNodeSingleToken(t));
                    return new ConfigNodeArray(children);
                } else if (Tokens.isValue(t) || t == Tokens.OPEN_CURLY
                        || t == Tokens.OPEN_SQUARE || Tokens.isUnquotedText(t)
                        || Tokens.isSubstitution(t)) {
                    nextValue = parseValue(t);
                    children.add(nextValue);
                    nextValue = null;
                } else {
                    throw parseError(addKeyName("List should have ] or a first element after the open [, instead had token: "
                            + t
                            + " (if you want "
                            + t
                            + " to be part of a string value, then double-quote it)"));
                }
            }

            // now remaining elements
            while (true) {
                // just after a value
                if (checkElementSeparator(children)) {
                    // comma (or newline equivalent) consumed
                } else {
                    t = nextTokenIgnoringWhitespace(children);
                    if (t == Tokens.CLOSE_SQUARE) {
                        arrayCount -= 1;
                        children.add(new ConfigNodeSingleToken(t));
                        return new ConfigNodeArray(children);
                    } else {
                        throw parseError(addKeyName("List should have ended with ] or had a comma, instead had token: "
                                + t
                                + " (if you want "
                                + t
                                + " to be part of a string value, then double-quote it)"));
                    }
                }

                // now just after a comma
                nextValue = consolidateValues(children);
                if (nextValue != null) {
                    children.add(nextValue);
                    nextValue = null;
                } else {
                    t = nextTokenIgnoringWhitespace(children);
                    if (Tokens.isValue(t) || t == Tokens.OPEN_CURLY
                            || t == Tokens.OPEN_SQUARE || Tokens.isUnquotedText(t)
                            || Tokens.isSubstitution(t)) {
                        nextValue = parseValue(t);
                        children.add(nextValue);
                        nextValue = null;
                    } else if (flavor != ConfigSyntax.JSON && t == Tokens.CLOSE_SQUARE) {
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
        }

        ConfigNodeComplexValue parse() {
            Token t = nextToken();
            if (t == Tokens.START) {
                // OK
            } else {
                throw new ConfigException.BugOrBroken(
                        "token stream did not begin with START, had " + t);
            }

            t = nextToken();
            AbstractConfigNode result = null;
            if (t == Tokens.OPEN_CURLY || t == Tokens.OPEN_SQUARE) {
                result = parseValue(t);
            } else {
                if (flavor == ConfigSyntax.JSON) {
                    if (t == Tokens.END) {
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
                }
            }
            ArrayList<AbstractConfigNode> children = new ArrayList<AbstractConfigNode>(((ConfigNodeComplexValue)result).children());
            t = nextTokenIgnoringWhitespace(children);
            if (t == Tokens.END) {
                if (result instanceof ConfigNodeArray) {
                    return new ConfigNodeArray(children);
                }
                return new ConfigNodeObject(children);
            } else {
                throw parseError("Document has trailing tokens after first object or array: "
                        + t);
            }
        }

        // Parse a given input stream into a single value node. Used when doing a replace inside a ConfigDocument.
        AbstractConfigNodeValue parseSingleValue() {
            Token t = nextToken();
            if (t == Tokens.START) {
                // OK
            } else {
                throw new ConfigException.BugOrBroken(
                        "token stream did not begin with START, had " + t);
            }

            t = nextToken();
            while (Tokens.isIgnoredWhitespace(t) || Tokens.isNewline(t) || isUnquotedWhitespace(t)) {
                t = nextToken();
            }
            if (t == Tokens.END) {
                throw parseError("Empty value");
            }
            if (flavor == ConfigSyntax.JSON) {
                AbstractConfigNodeValue node = parseValue(t);
                t = nextToken();
                while (Tokens.isIgnoredWhitespace(t) || Tokens.isNewline(t) || isUnquotedWhitespace(t)) {
                    t = nextToken();
                }
                if (t == Tokens.END) {
                    return node;
                } else {
                    throw parseError("Tried to parse a concatenation. Concatenations not allowed in valid JSON");
                }
            } else {
                putBack(t);
                ArrayList<AbstractConfigNode> nodes = new ArrayList();
                return consolidateValues(nodes);
            }
        }
    }
}
