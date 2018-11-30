/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config

import java.io.{ IOException, ObjectInputStream, ObjectOutputStream, Serializable }
import java.{ lang => jl }
import jl.reflect.Field

import com.typesafe.config.impl.ConfigImplUtil

/**
 * All exceptions thrown by the library are subclasses of
 * <code>ConfigException</code>.
 */
@SerialVersionUID(1L)
abstract class ConfigException(message: String, cause: Throwable)
    extends RuntimeException(message, cause) with Serializable {

    @transient var origin: ConfigOrigin = null

    protected def this(origin: ConfigOrigin, message: String, cause: Throwable) = {
        this(ConfigException.makeMessage(origin, message), cause)
        this.origin = origin
    }

    protected def this(origin: ConfigOrigin, message: String) =
        this(ConfigException.makeMessage(origin, message), null)

    protected def this(message: String) =
        this(message, null)

    // we customize serialization because ConfigOrigin isn't
    // serializable and we don't want it to be (don't want to
    // support it)
    @throws(classOf[IOException])
    private def writeObject(out: java.io.ObjectOutputStream): Unit = {
        out.defaultWriteObject()
        ConfigImplUtil.writeOrigin(out, origin)
    }

    @throws(classOf[IOException])
    @throws(classOf[ClassNotFoundException])
    private def readObject(in: java.io.ObjectInputStream): Unit = {
        in.defaultReadObject()
        val origin = ConfigImplUtil.readOrigin(in)
        ConfigException.setOriginField(this, classOf[ConfigException], origin)
    }

}

@SerialVersionUID(1L)
object ConfigException {
    // For deserialization - uses reflection to set the final origin field on the object
    @throws(classOf[IOException])
    private def setOriginField[T](hasOriginField: T, clazz: Class[_ <: Serializable],
        origin: ConfigOrigin): Unit = {
        // circumvent "final"
        var f: Field = null
        try {
            f = clazz.getDeclaredField("origin");
        } catch {
            case e: NoSuchFieldException =>
                throw new IOException(
                    clazz.getSimpleName() + " has no origin field?",
                    e)
            case e: SecurityException =>
                throw new IOException(
                    "unable to fill out origin field in " + clazz.getSimpleName,
                    e)
        }

        f.setAccessible(true);
        try {
            f.set(hasOriginField, origin);
        } catch {
            case e @ (_: IllegalArgumentException | _: IllegalAccessException) =>
                throw new IOException("unable to set origin field", e)
        }
    }
    /* this in Java would never purposely called with a null Origin but
        because of Scala's primary constructor constraints and the way these
        classes work we need to guard against null Origin */
    private def makeMessage(origin: ConfigOrigin, message: String): String =
        if (origin != null) origin.description + ": " + message else message

    /**
     * Exception indicating that the type of a value does not match the type you
     * requested.
     *
     */
    @SerialVersionUID(1L)
    class WrongType(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {

        def this(origin: ConfigOrigin, path: String, expected: String, actual: String, cause: Throwable) =
            this(origin, path + " has type " + actual + " rather than " + expected, cause)

        def this(origin: ConfigOrigin, path: String, expected: String, actual: String) =
            this(origin, path, expected, actual, null)

        def this(origin: ConfigOrigin, message: String) =
            this(origin, message, null)

    }

    /**
     * Exception indicates that the setting was never set to anything, not even
     * null.
     */
    @SerialVersionUID(1L)
    object Missing {
        private def makeMessage(path: String) =
            "No configuration setting found for key '" + path + "'"
    }

    // primary ctor calls super directly with no special message
    @SerialVersionUID(1L)
    class Missing(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {

        def this(path: String, cause: Throwable) =
            this(null, Missing.makeMessage(path), cause)

        def this(origin: ConfigOrigin, path: String) =
            this(origin, Missing.makeMessage(path), null)

        def this(path: String) = this(path, null)

    }

    /**
     * Exception indicates that the setting was treated as missing because it
     * was set to null.
     */
    @SerialVersionUID(1L)
    object Null {
        private def makeMessage(path: String, expected: String) =
            if (expected != null)
                "Configuration key '" + path + "' is set to null but expected " + expected
            else "Configuration key '" + path + "' is null"
    }

    @SerialVersionUID(1L)
    class Null(
        origin: ConfigOrigin,
        path: String,
        expected: String,
        cause: Throwable)
        extends ConfigException.Missing(origin, Null.makeMessage(path, expected), cause) {

        def this(origin: ConfigOrigin, path: String, expected: String) {
            this(origin, path, expected, null)
        }
    }

    /**
     * Exception indicating that a value was messed up, for example you may have
     * asked for a duration and the value can't be sensibly parsed as a
     * duration.
     *
     */
    @SerialVersionUID(1L)
    class BadValue(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {
        def this(origin: ConfigOrigin, path: String, message: String, cause: Throwable) =
            this(origin, "Invalid value at '" + path + "': " + message, cause)

        def this(origin: ConfigOrigin, path: String, message: String) = this(origin, path, message, null)

        def this(path: String, message: String, cause: Throwable) =
            this(null: ConfigOrigin, "Invalid value at '" + path + "': " + message, cause)

        def this(path: String, message: String) = this(path, message, null)
    }

    /**
     * Exception indicating that a path expression was invalid. Try putting
     * double quotes around path elements that contain "special" characters.
     *
     */
    @SerialVersionUID(1L)
    class BadPath(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {

        def this(origin: ConfigOrigin, path: String, message: String, cause: Throwable) =
            this(origin,
                if (path != null) "Invalid path '" + path + "': " + message else message,
                cause)

        def this(origin: ConfigOrigin, path: String, message: String) = this(origin, path, message, null)

        def this(path: String, message: String, cause: Throwable) =
            this(null: ConfigOrigin,
                if (path != null) "Invalid path '" + path + "': " + message else message,
                cause)

        def this(path: String, message: String) = this(path, message, null)

        def this(origin: ConfigOrigin, message: String) = this(origin, null, message)
    }

    /**
     * Exception indicating that there's a bug in something (possibly the
     * library itself) or the runtime environment is broken. This exception
     * should never be handled; instead, something should be fixed to keep the
     * exception from occurring. This exception can be thrown by any method in
     * the library.
     */
    @SerialVersionUID(1L)
    class BugOrBroken(message: String, cause: Throwable)
        extends ConfigException(message, cause) {

        def this(message: String) = this(message, null)
    }

    /**
     * Exception indicating that there was an IO error.
     *
     */
    @SerialVersionUID(1L)
    class IO(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {

        def this(origin: ConfigOrigin, message: String) = this(origin, message, null)
    }

    /**
     * Exception indicating that there was a parse error.
     *
     */
    @SerialVersionUID(1L)
    class Parse(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {

        def this(origin: ConfigOrigin, message: String) = this(origin, message, null)
    }

    /**
     * Exception indicating that a substitution did not resolve to anything.
     * Thrown by {@link Config#resolve}.
     */
    @SerialVersionUID(1L)
    class UnresolvedSubstitution(origin: ConfigOrigin, detail: String, cause: Throwable)
        extends ConfigException.Parse(
            origin,
            "Could not resolve substitution to a value: " + detail,
            cause) {

        def this(origin: ConfigOrigin, detail: String) = this(origin, detail, null)
    }

    /**
     * Exception indicating that you tried to use a function that requires
     * substitutions to be resolved, but substitutions have not been resolved
     * (that is, {@link Config#resolve} was not called). This is always a bug in
     * either application code or the library; it's wrong to write a handler for
     * this exception because you should be able to fix the code to avoid it by
     * adding calls to {@link Config#resolve}.
     */
    @SerialVersionUID(1L)
    class NotResolved(message: String, cause: Throwable)
        extends ConfigException.BugOrBroken(message, cause) {

        def this(message: String) = this(message, null)
    }

    /**
     * Information about a problem that occurred in {@link Config#checkValid}. A
     * {@link ConfigException.ValidationFailed} exception thrown from
     * <code>checkValid()</code> includes a list of problems encountered.
     */
    @SerialVersionUID(1L)
    class ValidationProblem(
        val path: String, // the path of the problem setting
        @transient val origin: ConfigOrigin = null, // the origin of the problem setting
        val problem: String) // description of the problem
        extends Serializable {

        // We customize serialization because ConfigOrigin isn't
        // serializable and we don't want it to be
        @throws[IOException]
        private def writeObject(out: ObjectOutputStream): Unit = {
            out.defaultWriteObject()
            ConfigImplUtil.writeOrigin(out, origin)
        }

        @throws[IOException]
        @throws[ClassNotFoundException]
        private def readObject(in: ObjectInputStream): Unit = {
            in.defaultReadObject()
            val origin = ConfigImplUtil.readOrigin(in)
            setOriginField(this, classOf[ConfigException.ValidationProblem], origin)
        }

        override def toString: String =
            "ValidationProblem(" + path + "," + origin + "," + problem + ")"
    }

    /**
     * Exception indicating that {@link Config#checkValid} found validity
     * problems. The problems are available via the {@link #problems()} method.
     * The <code>getMessage()</code> of this exception is a potentially very
     * long string listing all the problems found.
     */
    @SerialVersionUID(1L)
    object ValidationFailed {
        private def makeMessage(problems: jl.Iterable[ConfigException.ValidationProblem]): String = {
            val sb = new StringBuilder
            import scala.collection.JavaConverters._
            for (p <- problems.asScala) {
                sb.append(p.origin.description)
                sb.append(": ")
                sb.append(p.path)
                sb.append(": ")
                sb.append(p.problem)
                sb.append(", ")
            }
            if (sb.length == 0)
                throw new ConfigException.BugOrBroken(
                    "ValidationFailed must have a non-empty list of problems")
            sb.setLength(sb.length - 2) // chop comma and space
            sb.toString
        }
    }
    @SerialVersionUID(1L)
    class ValidationFailed(val problems: jl.Iterable[ConfigException.ValidationProblem])
        extends ConfigException(ValidationFailed.makeMessage(problems))

    /**
     * Some problem with a JavaBean we are trying to initialize.
     *
     * @since 1.3.0
     */
    @SerialVersionUID(1L)
    class BadBean(message: String, cause: Throwable)
        extends ConfigException.BugOrBroken(message, cause) {
        def this(message: String) = this(message, null)
    }

    /**
     * Exception that doesn't fall into any other category.
     */
    @SerialVersionUID(1L)
    class Generic(message: String, cause: Throwable)
        extends ConfigException(message, cause) {
        def this(message: String) = this(message, null)
    }

}
