/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/**
 * An immutable class representing an amount of memory.  Use
 * static factory methods such as {@link
 * ConfigMemorySize#ofBytes(BigInteger)} to create instances.
 *
 * @since 1.3.0
 */
public final class ConfigMemorySize {

    private BigInteger bytes;

    private ConfigMemorySize(BigInteger bytes) {
        if (bytes.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Attempt to construct ConfigMemorySize with negative number: " + bytes);
        }
        this.bytes = bytes;
    }

    /**
     * Constructs a ConfigMemorySize representing the given
     * number of bytes.
     * @since 1.3.0
     * @param bytes a number of bytes
     * @return an instance representing the number of bytes
     */
    public static ConfigMemorySize ofBytes(BigInteger bytes) {
        return new ConfigMemorySize(bytes);
    }

    public static ConfigMemorySize ofBytes(long bytes) {
        return new ConfigMemorySize(BigInteger.valueOf(bytes));
    }

    /**
     *
     * Gets the size in bytes.
     *
     * @deprecated use {@link #getBytes()} to handle to bytes conversion of huge memory units.
     * @since 1.3.0
     * @return how many bytes
     */
    public long toBytes() {
        return bytes.longValueExact();
    }

    public BigInteger getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return "ConfigMemorySize(" + bytes + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ConfigMemorySize) {
            return ((ConfigMemorySize) other).bytes.equals(this.bytes);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // in Java 8 this can become Long.hashCode(bytes)
        return bytes.hashCode();
    }

}

