package com.typesafe.config.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.typesafe.config.ConfigOrigin;

final class PropertiesParser {
    static AbstractConfigObject parse(Reader reader,
            ConfigOrigin origin) throws IOException {
        Properties props = new Properties();
        props.load(reader);
        return fromProperties(origin, props);
    }

    static String lastElement(String path) {
        int i = path.lastIndexOf('.');
        if (i < 0)
            return path;
        else
            return path.substring(i + 1);
    }

    static String exceptLastElement(String path) {
        int i = path.lastIndexOf('.');
        if (i < 0)
            return null;
        else
            return path.substring(0, i);
    }

    static AbstractConfigObject fromProperties(ConfigOrigin origin,
            Properties props) {
        /*
         * First, build a list of paths that will have values, either strings or
         * objects.
         */
        Set<String> scopePaths = new HashSet<String>();
        Set<String> valuePaths = new HashSet<String>();
        Enumeration<?> i = props.propertyNames();
        while (i.hasMoreElements()) {
            Object o = i.nextElement();
            if (o instanceof String) {
                // add value's path
                String path = (String) o;
                valuePaths.add(path);

                // all parent paths are objects
                String next = exceptLastElement(path);
                while (next != null) {
                    scopePaths.add(next);
                    next = exceptLastElement(next);
                }
            }
        }

        /*
         * If any string values are also objects containing other values, drop
         * those string values - objects "win".
         */
        valuePaths.removeAll(scopePaths);

        /*
         * Create maps for the object-valued values.
         */
        Map<String, AbstractConfigValue> root = new HashMap<String, AbstractConfigValue>();
        Map<String, Map<String, AbstractConfigValue>> scopes = new HashMap<String, Map<String, AbstractConfigValue>>();

        for (String path : scopePaths) {
            Map<String, AbstractConfigValue> scope = new HashMap<String, AbstractConfigValue>();
            scopes.put(path, scope);
        }

        /* Store string values in the associated scope maps */
        for (String path : valuePaths) {
            String parentPath = exceptLastElement(path);
            Map<String, AbstractConfigValue> parent = parentPath != null ? scopes
                    .get(parentPath) : root;

            String last = lastElement(path);
            String value = props.getProperty(path);
            parent.put(last, new ConfigString(origin, value));
        }

        /*
         * Make a list of scope paths from longest to shortest, so children go
         * before parents.
         */
        List<String> sortedScopePaths = new ArrayList<String>();
        sortedScopePaths.addAll(scopePaths);
        // sort descending by length
        Collections.sort(sortedScopePaths, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });

        /*
         * Create ConfigObject for each scope map, working from children to
         * parents to avoid modifying any already-created ConfigObject. This is
         * where we need the sorted list.
         */
        for (String scopePath : sortedScopePaths) {
            Map<String, AbstractConfigValue> scope = scopes.get(scopePath);

            String parentPath = exceptLastElement(scopePath);
            Map<String, AbstractConfigValue> parent = parentPath != null ? scopes
                    .get(parentPath) : root;

            AbstractConfigObject o = new SimpleConfigObject(origin, scope,
                    ResolveStatus.RESOLVED);
            parent.put(lastElement(scopePath), o);
        }

        // return root config object
        return new SimpleConfigObject(origin, root, ResolveStatus.RESOLVED);
    }
}
