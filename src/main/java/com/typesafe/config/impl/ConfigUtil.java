package com.typesafe.config.impl;


final class ConfigUtil {
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
