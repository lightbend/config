/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

import java.math.BigInteger;

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
        if (bytes.signum() < 0)
            throw new IllegalArgumentException("Attempt to construct ConfigMemorySize with negative number: " + bytes);
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

    /**
     * Constructs a ConfigMemorySize representing the given
     * number of bytes.
     * @param bytes a number of bytes
     * @return an instance representing the number of bytes
     */
    public static ConfigMemorySize ofBytes(long bytes) {
        return new ConfigMemorySize(BigInteger.valueOf(bytes));
    }

    /**
     * Gets the size in bytes.
     *
     * @since 1.3.0
     * @return how many bytes
     * @exception IllegalArgumentException when memory value
     * in bytes doesn't fit in a long value. Consider using
     * {@link #toBytesBigInteger} in this case.
     */
    public long toBytes() {
        if (bytes.bitLength() < 64)
            return bytes.longValue();
        else
            throw new IllegalArgumentException(
                "size-in-bytes value is out of range for a 64-bit long: '" + bytes + "'");
    }

    /**
     * Gets the size in bytes. The behavior of this method
     * is the same as that of the {@link #toBytes()} method,
     * except that the number of bytes returned as a
     * BigInteger value. Use it when memory value in bytes
     * doesn't fit in a long value.
     * @return how many bytes
     */
    public BigInteger toBytesBigInteger() {
        return bytes;
    }

    @Override
    public String toString() {
        return "ConfigMemorySize(" + bytes + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ConfigMemorySize) {
            return ((ConfigMemorySize)other).bytes.equals(this.bytes);
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

