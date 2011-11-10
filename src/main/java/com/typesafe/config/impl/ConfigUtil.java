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

    static boolean equalsHandlingNull(Object a, Object b) {
        if (a == null && b != null)
            return false;
        else if (a != null && b == null)
            return false;
        else if (a == b) // catches null == null plus optimizes identity case
            return true;
        else
            return a.equals(b);
    }

    static String renderJsonString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (Character.isISOControl(c))
                    sb.append(String.format("\\u%04x", (int) c));
                else
                    sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
