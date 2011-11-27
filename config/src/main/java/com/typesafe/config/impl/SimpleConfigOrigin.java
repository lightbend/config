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

// it would be cleaner to have a class hierarchy for various origin types,
// but was hoping this would be enough simpler to be a little messy. eh.
final class SimpleConfigOrigin implements ConfigOrigin {
    final private String description;
    final private int lineNumber;
    final private OriginType originType;
    final private String urlOrNull;

    protected SimpleConfigOrigin(String description, int lineNumber, OriginType originType,
            String urlOrNull) {
        this.lineNumber = lineNumber;
        this.originType = originType;
        this.description = description;
        this.urlOrNull = urlOrNull;
    }

    static SimpleConfigOrigin newSimple(String description) {
        return new SimpleConfigOrigin(description, -1, OriginType.GENERIC, null);
    }

    static SimpleConfigOrigin newFile(String filename) {
        String url;
        try {
            url = (new File(filename)).toURI().toURL().toExternalForm();
        } catch (MalformedURLException e) {
            url = null;
        }
        return new SimpleConfigOrigin(filename, -1, OriginType.FILE, url);
    }

    static SimpleConfigOrigin newURL(URL url) {
        String u = url.toExternalForm();
        return new SimpleConfigOrigin(u, -1, OriginType.URL, u);
    }

    static SimpleConfigOrigin newResource(String resource, URL url) {
        return new SimpleConfigOrigin(resource, -1, OriginType.RESOURCE,
                url != null ? url.toExternalForm() : null);
    }

    static SimpleConfigOrigin newResource(String resource) {
        return newResource(resource, null);
    }

    // important, this should also be able to _change_ an existing line
    // number
    SimpleConfigOrigin addLineNumber(int lineNumber) {
        return new SimpleConfigOrigin(this.description, lineNumber, this.originType, this.urlOrNull);
    }

    SimpleConfigOrigin addURL(URL url) {
        return new SimpleConfigOrigin(this.description, this.lineNumber, this.originType,
                url != null ? url.toExternalForm() : null);
    }

    @Override
    public String description() {
        // not putting the URL in here for files and resources, because people
        // parsing "file: line" syntax would hit the ":" in the URL.
        if (lineNumber < 0) {
            return description;
        } else {
            return description + ": " + lineNumber;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SimpleConfigOrigin) {
            SimpleConfigOrigin otherOrigin = (SimpleConfigOrigin) other;

            return this.description.equals(otherOrigin.description)
                    && this.lineNumber == otherOrigin.lineNumber
                    && this.originType == otherOrigin.originType
                    && ConfigUtil.equalsHandlingNull(this.urlOrNull, otherOrigin.urlOrNull);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int h = 41 * (41 + description.hashCode());
        h = 41 * (h + lineNumber);
        h = 41 * (h + originType.hashCode());
        if (urlOrNull != null)
            h = 41 * (h + urlOrNull.hashCode());
        return h;
    }

    @Override
    public String toString() {
        // the url is only really useful on top of description for resources
        if (originType == OriginType.RESOURCE && urlOrNull != null) {
            return "ConfigOrigin(" + description + "," + urlOrNull + ")";
        } else {
            return "ConfigOrigin(" + description + ")";
        }
    }

    @Override
    public String filename() {
        if (originType == OriginType.FILE) {
            return description;
        } else if (urlOrNull != null) {
            URL url;
            try {
                url = new URL(urlOrNull);
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
        if (urlOrNull == null) {
            return null;
        } else {
            try {
                return new URL(urlOrNull);
            } catch (MalformedURLException e) {
                return null;
            }
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
