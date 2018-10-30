/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config

/**
 * An immutable class representing an amount of memory.  Use
 * static factory methods such as {@link
 * ConfigMemorySize#ofBytes(long)} to create instances.
 *
 * @since 1.3.0
 */
object ConfigMemorySize {

    /**
     * Constructs a ConfigMemorySize representing the given
     * number of bytes.
     *
     * @since 1.3.0
     * @param bytes a number of bytes
     * @return an instance representing the number of bytes
     */
    def ofBytes(bytes: Long) = new ConfigMemorySize(bytes)
}

final class ConfigMemorySize private (val bytes: Long) {
    if (bytes < 0)
        throw new IllegalArgumentException(
            "Attempt to construct ConfigMemorySize with negative number: " + bytes)

    /**
     * Gets the size in bytes.
     *
     * @since 1.3.0
     * @return how many bytes
     */
    def toBytes(): Long = bytes

    override def toString(): String = "ConfigMemorySize(" + bytes + ")"

    override def equals(other: Any): Boolean =
        if (other.isInstanceOf[ConfigMemorySize])
            other.asInstanceOf[ConfigMemorySize].bytes == this.bytes
        else false

    override def hashCode(): Int =
        // in Java 8 this can become Long.hashCode(bytes)
        //Long.valueOf(bytes).hashCode
        bytes.hashCode()
}
