package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

final class Path {

    private String first;
    private Path remainder;

    Path(String first, Path remainder) {
        this.first = first;
        this.remainder = remainder;
    }

    Path(String... elements) {
        if (elements.length == 0)
            throw new ConfigException.BugOrBroken("empty path");
        this.first = elements[0];
        if (elements.length > 1) {
            PathBuilder pb = new PathBuilder();
            for (int i = 1; i < elements.length; ++i) {
                pb.appendKey(elements[i]);
            }
            this.remainder = pb.result();
        } else {
            this.remainder = null;
        }
    }

    String first() {
        return first;
    }

    Path remainder() {
        return remainder;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Path) {
            Path that = (Path) other;
            return this.first.equals(that.first)
                    && ConfigUtil.equalsHandlingNull(this.remainder,
                            that.remainder);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 41 * (41 + first.hashCode())
                + (remainder == null ? 0 : remainder.hashCode());
    }

    // this doesn't have a very precise meaning, just to reduce
    // noise from quotes in the rendered path
    static boolean hasFunkyChars(String s) {
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ')
                continue;
            else
                return true;
        }
        return false;
    }

    private void appendToStringBuilder(StringBuilder sb) {
        if (hasFunkyChars(first))
            sb.append(ConfigUtil.renderJsonString(first));
        else
            sb.append(first);
        if (remainder != null) {
            sb.append(".");
            remainder.appendToStringBuilder(sb);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Path(");
        appendToStringBuilder(sb);
        sb.append(")");
        return sb.toString();
    }

    /**
     * toString() is a debugging-oriented version while this is an
     * error-message-oriented human-readable one.
     */
    String render() {
        StringBuilder sb = new StringBuilder();
        appendToStringBuilder(sb);
        return sb.toString();
    }

    static Path newKey(String key) {
        return new Path(key, null);
    }

    static Path newPath(String path) {
        PathBuilder pb = new PathBuilder();
        pb.appendPath(path);
        return pb.result();
    }
}
