package com.typesafe.config.impl;

import java.util.Stack;

import com.typesafe.config.ConfigException;

final class PathBuilder {
    // the keys are kept "backward" (top of stack is end of path)
    private Stack<String> keys;
    private Path result;

    PathBuilder() {
        keys = new Stack<String>();
    }

    private void checkCanAppend() {
        if (result != null)
            throw new ConfigException.BugOrBroken(
                    "Adding to PathBuilder after getting result");
    }

    void appendPath(String path) {
        checkCanAppend();
        ConfigUtil.verifyPath(path);

        String next = ConfigUtil.firstElement(path);
        String remainder = ConfigUtil.otherElements(path);

        while (next != null) {
            keys.push(next);
            if (remainder != null) {
                next = ConfigUtil.firstElement(remainder);
                remainder = ConfigUtil.otherElements(remainder);
            } else {
                next = null;
            }
        }
    }

    void appendKey(String key) {
        checkCanAppend();

        keys.push(key);
    }

    Path result() {
        if (result == null) {
            Path remainder = null;
            while (!keys.isEmpty()) {
                String key = keys.pop();
                remainder = new Path(key, remainder);
            }
            result = remainder;
        }
        return result;
    }

    static Path newPath(String path) {
        PathBuilder pb = new PathBuilder();
        pb.appendPath(path);
        return pb.result();
    }

    static Path newKey(String key) {
        return new Path(key, null);
    }
}
