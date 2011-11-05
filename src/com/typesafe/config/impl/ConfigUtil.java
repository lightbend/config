package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

final class ConfigUtil {
    static void verifyPath(String path) {
        if (path.startsWith("."))
            throw new ConfigException.BadPath(path, "Path starts with '.'");
        if (path.endsWith("."))
            throw new ConfigException.BadPath(path, "Path ends with '.'");
        if (path.contains(".."))
            throw new ConfigException.BadPath(path,
                    "Path contains '..' (empty element)");
    }

    static String firstElement(String path) {
        verifyPath(path);
        int i = path.indexOf('.');
        if (i < 0)
            return path;
        else
            return path.substring(0, i);
    }

    static String otherElements(String path) {
        verifyPath(path);
        int i = path.indexOf('.');
        if (i < 0)
            return null;
        else
            return path.substring(i + 1);
    }

    static String lastElement(String path) {
        verifyPath(path);
        int i = path.lastIndexOf('.');
        if (i < 0)
            return path;
        else
            return path.substring(i + 1);
    }

    static String exceptLastElement(String path) {
        verifyPath(path);
        int i = path.lastIndexOf('.');
        if (i < 0)
            return null;
        else
            return path.substring(0, i);
    }
}
