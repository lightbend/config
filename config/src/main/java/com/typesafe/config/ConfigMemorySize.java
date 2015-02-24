/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

/**
 * An immutable class representing an amount of memory.  Use
 * static factory methods such as {@link
 * ConfigMemorySize#ofBytes(long)} to create instances.
 */
public final class ConfigMemorySize {
    private final long bytes;

    private ConfigMemorySize(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("Attempt to construct ConfigMemorySize with negative number: " + bytes);
        this.bytes = bytes;
    }

    /** Constructs a ConfigMemorySize representing the given
     * number of bytes.
     */
    public static ConfigMemorySize ofBytes(long bytes) {
        return new ConfigMemorySize(bytes);
    }

    /** Gets the size in bytes.
     */
    public long toBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ConfigMemorySize) {
            return ((ConfigMemorySize)other).bytes == this.bytes;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // in Java 8 this can become Long.hashCode(bytes)
        return Long.valueOf(bytes).hashCode();
    }

}

