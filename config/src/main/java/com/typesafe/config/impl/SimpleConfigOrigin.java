/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;

final class SimpleConfigOrigin implements ConfigOrigin {
    final private String description;
    final private int lineNumber;
    final private OriginType originType;

    private SimpleConfigOrigin(String description, int lineNumber, OriginType originType) {
        this.lineNumber = lineNumber;
        this.originType = originType;
        this.description = description;
    }

    static SimpleConfigOrigin newSimple(String description) {
        return new SimpleConfigOrigin(description, -1, OriginType.GENERIC);
    }

    static SimpleConfigOrigin newFile(String filename) {
        return new SimpleConfigOrigin(filename, -1, OriginType.FILE);
    }

    static SimpleConfigOrigin newURL(URL url) {
        return new SimpleConfigOrigin(url.toExternalForm(), -1, OriginType.URL);
    }

    static SimpleConfigOrigin newResource(String resource) {
        return new SimpleConfigOrigin(resource, -1, OriginType.RESOURCE);
    }

    // important, this should also be able to _change_ an existing line
    // number
    SimpleConfigOrigin addLineNumber(int lineNumber) {
        return new SimpleConfigOrigin(this.description, lineNumber, this.originType);
    }

    @Override
    public String description() {
        if (lineNumber < 0) {
            return description;
        } else {
            return description + ": " + lineNumber;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SimpleConfigOrigin) {
            // two origins are equal if they are described to the user in the
            // same way, for now at least this seems fine
            return this.description.equals(((SimpleConfigOrigin) other).description);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return description.hashCode();
    }

    @Override
    public String toString() {
        return "ConfigOrigin(" + description + ")";
    }

    @Override
    public String filename() {
        if (originType == OriginType.FILE) {
            return description;
        } else if (originType == OriginType.URL) {
            URL url;
            try {
                url = new URL(description);
            } catch (MalformedURLException e) {
                return null;
            }
            if (url.getProtocol().equals("file")) {
                return url.getFile();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public URL url() {
        if (originType == OriginType.URL) {
            try {
                return new URL(description);
            } catch (MalformedURLException e) {
                return null;
            }
        } else if (originType == OriginType.FILE) {
            try {
                return (new File(description)).toURI().toURL();
            } catch (MalformedURLException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public String resource() {
        if (originType == OriginType.RESOURCE) {
            return description;
        } else {
            return null;
        }
    }

    @Override
    public int lineNumber() {
        return lineNumber;
    }

    static final String MERGE_OF_PREFIX = "merge of ";

    static ConfigOrigin mergeOrigins(Collection<? extends ConfigOrigin> stack) {
        if (stack.isEmpty()) {
            throw new ConfigException.BugOrBroken("can't merge empty list of origins");
        } else if (stack.size() == 1) {
            return stack.iterator().next();
        } else {
            StringBuilder sb = new StringBuilder();
            for (ConfigOrigin o : stack) {
                String desc = o.description();
                if (desc.startsWith(MERGE_OF_PREFIX))
                    desc = desc.substring(MERGE_OF_PREFIX.length());

                sb.append(desc);
                sb.append(",");
            }

            sb.setLength(sb.length() - 1); // chop comma
            return newSimple(MERGE_OF_PREFIX + sb.toString());
        }
    }
}
