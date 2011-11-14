package com.typesafe.config.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;

final class PropertiesParser {
    static AbstractConfigObject parse(Reader reader,
            ConfigOrigin origin) throws IOException {
        Properties props = new Properties();
        props.load(reader);
        return fromProperties(origin, props);
    }

    static void verifyPath(String path) {
        if (path.startsWith("."))
            throw new ConfigException.BadPath(path, "Path starts with '.'");
        if (path.endsWith("."))
            throw new ConfigException.BadPath(path, "Path ends with '.'");
        if (path.contains(".."))
            throw new ConfigException.BadPath(path,
                    "Path contains '..' (empty element)");
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

    static AbstractConfigObject fromProperties(ConfigOrigin origin,
            Properties props) {
        Map<String, Map<String, AbstractConfigValue>> scopes = new HashMap<String, Map<String, AbstractConfigValue>>();
        Enumeration<?> i = props.propertyNames();
        while (i.hasMoreElements()) {
            Object o = i.nextElement();
            if (o instanceof String) {
                try {
                    String path = (String) o;
                    String last = lastElement(path);
                    String exceptLast = exceptLastElement(path);
                    if (exceptLast == null)
                        exceptLast = "";
                    Map<String, AbstractConfigValue> scope = scopes
                            .get(exceptLast);
                    if (scope == null) {
                        scope = new HashMap<String, AbstractConfigValue>();
                        scopes.put(exceptLast, scope);
                    }
                    String value = props.getProperty(path);
                    scope.put(last, new ConfigString(origin, value));
                } catch (ConfigException.BadPath e) {
                    // just skip this one (log it?)
                }
            }
        }

        // pull out the list of objects that go inside other objects
        List<String> childPaths = new ArrayList<String>();
        for (String path : scopes.keySet()) {
            if (path != "")
                childPaths.add(path);
        }

        // put everything in its parent, ensuring all parents exist
        for (String path : childPaths) {
            String parentPath = exceptLastElement(path);
            if (parentPath == null)
                parentPath = "";

            Map<String, AbstractConfigValue> parent = scopes.get(parentPath);
            if (parent == null) {
                parent = new HashMap<String, AbstractConfigValue>();
                scopes.put(parentPath, parent);
            }
            // NOTE: we are evil and cheating, we mutate the map we
            // provide to SimpleConfigObject, which is not allowed by
            // its contract, but since we know nobody has a ref to this
            // SimpleConfigObject yet we can get away with it.
            // Also we assume here that any info based on the map that
            // SimpleConfigObject computes and caches in its constructor
            // will not change. Basically this is a bad hack.
            AbstractConfigObject o = new SimpleConfigObject(origin,
                    scopes.get(path), ResolveStatus.RESOLVED);
            String basename = lastElement(path);
            parent.put(basename, o);
        }

        Map<String, AbstractConfigValue> root = scopes.get("");
        if (root == null) {
            // this would happen only if you had no properties at all
            // in "props"
            root = Collections.<String, AbstractConfigValue> emptyMap();
        }

        // return root config object
        return new SimpleConfigObject(origin, root, ResolveStatus.RESOLVED);
    }
}
