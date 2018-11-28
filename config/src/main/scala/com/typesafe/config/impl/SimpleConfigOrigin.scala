/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.File
import java.io.IOException
import java.{ lang => jl }
import java.net.MalformedURLException
import java.net.URL
import java.{ util => ju }
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigOrigin

// it would be cleaner to have a class hierarchy for various origin types,
// but was hoping this would be enough simpler to be a little messy. eh.
object SimpleConfigOrigin {
    // Needed for ConfigImpl.java
    /*private[impl]*/ def newSimple(description: String) = new SimpleConfigOrigin(
        description,
        -1,
        -1,
        OriginType.GENERIC,
        null,
        null,
        null)
    /*private[impl]*/ def newFile(filename: String): SimpleConfigOrigin = {
        var url: String = null
        try url = new File(filename).toURI.toURL.toExternalForm
        catch {
            case e: MalformedURLException =>
                url = null
        }
        new SimpleConfigOrigin(
            filename,
            -1,
            -1,
            OriginType.FILE,
            url,
            null,
            null)
    }
    /*private[impl]*/ def newURL(url: URL): SimpleConfigOrigin = {
        val u = url.toExternalForm
        new SimpleConfigOrigin(u, -1, -1, OriginType.URL, u, null, null)
    }
    private[impl] def newResource(
        resource: String,
        url: URL): SimpleConfigOrigin = {
        val desc: String = if (url != null) resource + " @ " + url.toExternalForm else resource
        new SimpleConfigOrigin(
            desc,
            -1,
            -1,
            OriginType.RESOURCE,
            if (url != null) url.toExternalForm else null,
            resource,
            null)
    }
    private[impl] def newResource(resource: String): SimpleConfigOrigin = newResource(resource, null)
    private[impl] val MERGE_OF_PREFIX = "merge of "
    private def mergeTwo(
        a: SimpleConfigOrigin,
        b: SimpleConfigOrigin): SimpleConfigOrigin = {
        var mergedDesc: String = null
        var mergedStartLine = 0
        var mergedEndLine = 0
        var mergedComments: ju.List[String] = null
        val mergedType = if (a.originType eq b.originType) a.originType else OriginType.GENERIC
        // first use the "description" field which has no line numbers
        // cluttering it.
        var aDesc = a._description
        var bDesc = b._description
        if (aDesc.startsWith(MERGE_OF_PREFIX)) aDesc = aDesc.substring(MERGE_OF_PREFIX.length)
        if (bDesc.startsWith(MERGE_OF_PREFIX)) bDesc = bDesc.substring(MERGE_OF_PREFIX.length)
        if (aDesc == bDesc) {
            mergedDesc = aDesc
            if (a.lineNumber < 0)
                mergedStartLine = b.lineNumber
            else if (b.lineNumber < 0)
                mergedStartLine = a.lineNumber
            else
                mergedStartLine = Math.min(a.lineNumber, b.lineNumber)

            mergedEndLine = Math.max(a.endLineNumber, b.endLineNumber)
        } else {
            // this whole merge song-and-dance was intended to avoid this case
            // whenever possible, but we've lost. Now we have to lose some
            // structured information and cram into a string.
            // description() method includes line numbers, so use it instead
            // of description field.
            var aFull = a.description
            var bFull = b.description
            if (aFull.startsWith(MERGE_OF_PREFIX)) aFull = aFull.substring(MERGE_OF_PREFIX.length)
            if (bFull.startsWith(MERGE_OF_PREFIX)) bFull = bFull.substring(MERGE_OF_PREFIX.length)
            mergedDesc = MERGE_OF_PREFIX + aFull + "," + bFull
            mergedStartLine = -1
            mergedEndLine = -1
        }
        val mergedURL =
            if (ConfigImplUtil.equalsHandlingNull(a.urlOrNull, b.urlOrNull)) a.urlOrNull
            else null
        val mergedResource =
            if (ConfigImplUtil.equalsHandlingNull(a.resourceOrNull, b.resourceOrNull)) a.resourceOrNull
            else null

        if (ConfigImplUtil.equalsHandlingNull(a.commentsOrNull, b.commentsOrNull))
            mergedComments = a.commentsOrNull
        else {
            mergedComments = new ju.ArrayList[String]
            if (a.commentsOrNull != null) mergedComments.addAll(a.commentsOrNull)
            if (b.commentsOrNull != null) mergedComments.addAll(b.commentsOrNull)
        }

        new SimpleConfigOrigin(
            mergedDesc,
            mergedStartLine,
            mergedEndLine,
            mergedType,
            mergedURL,
            mergedResource,
            mergedComments)
    }
    private def similarity(a: SimpleConfigOrigin, b: SimpleConfigOrigin): Int = {
        var count = 0
        if (a.originType eq b.originType)
            count += 1
        if (a._description == b._description) {
            count += 1
            // only count these if the description field (which is the file
            // or resource name) also matches.
            if (a.lineNumber == b.lineNumber) count += 1
            if (a.endLineNumber == b.endLineNumber) count += 1
            if (ConfigImplUtil.equalsHandlingNull(a.urlOrNull, b.urlOrNull)) count += 1
            if (ConfigImplUtil.equalsHandlingNull(a.resourceOrNull, b.resourceOrNull)) count += 1
        }
        count
    }
    // this picks the best pair to merge, because the pair has the most in
    // common. we want to merge two lines in the same file rather than something
    // else with one of the lines; because two lines in the same file can be
    // better consolidated.
    private def mergeThree(
        a: SimpleConfigOrigin,
        b: SimpleConfigOrigin,
        c: SimpleConfigOrigin): SimpleConfigOrigin =
        if (similarity(a, b) >= similarity(b, c))
            mergeTwo(mergeTwo(a, b), c)
        else
            mergeTwo(a, mergeTwo(b, c))

    /*private[impl]*/ def mergeOrigins(a: ConfigOrigin, b: ConfigOrigin): SimpleConfigOrigin =
        mergeTwo(
            a.asInstanceOf[SimpleConfigOrigin],
            b.asInstanceOf[SimpleConfigOrigin])

    private[impl] def mergeOrigins(stack: ju.List[_ <: AbstractConfigValue]): ConfigOrigin = {
        val origins = new ju.ArrayList[ConfigOrigin](stack.size)
        for (v <- stack.asScala) {
            origins.add(v.origin)
        }
        mergeOrigins(origins)
    }
    private[impl] def mergeOrigins(stack: ju.Collection[_ <: ConfigOrigin]): ConfigOrigin =
        if (stack.isEmpty)
            throw new ConfigException.BugOrBroken("can't merge empty list of origins")
        else if (stack.size == 1)
            stack.iterator.next
        else if (stack.size == 2) {
            val i = stack.iterator
            mergeTwo(
                i.next.asInstanceOf[SimpleConfigOrigin],
                i.next.asInstanceOf[SimpleConfigOrigin])
        } else {
            val remaining =
                new ju.ArrayList[SimpleConfigOrigin]
            for (o <- stack.asScala) {
                remaining.add(o.asInstanceOf[SimpleConfigOrigin])
            }
            while (remaining.size > 2) {
                val c = remaining.get(remaining.size - 1)
                remaining.remove(remaining.size - 1)
                val b = remaining.get(remaining.size - 1)
                remaining.remove(remaining.size - 1)
                val a = remaining.get(remaining.size - 1)
                remaining.remove(remaining.size - 1)
                val merged = mergeThree(a, b, c)
                remaining.add(merged)
            }
            // should be down to either 1 or 2
            mergeOrigins(remaining)
        }

    // Here we're trying to avoid serializing the same info over and over
    // in the common case that child objects have the same origin fields
    // as their parent objects. e.g. we don't need to store the source
    // filename with every single value.
    private[impl] def fieldsDelta(
        base: ju.Map[SerializedField, AnyRef],
        child: ju.Map[SerializedField, AnyRef]): ju.Map[SerializedField, AnyRef] = {
        val m = new ju.TreeMap[SerializedField, AnyRef](child) // was EnumMap
        for (baseEntry <- base.entrySet.asScala) {
            val f = baseEntry.getKey
            if (m.containsKey(f) && ConfigImplUtil.equalsHandlingNull(baseEntry.getValue, m.get(f))) {
                // if field is unchanged, just remove it so we inherit
                m.remove(f)
            } else if (!m.containsKey(f)) {
                // if field has been removed, we have to add a deletion entry
                f match {
                    case SerializedField.ORIGIN_DESCRIPTION =>
                        throw new ConfigException.BugOrBroken(
                            "origin missing description field? " + child)
                    case SerializedField.ORIGIN_LINE_NUMBER =>
                        m.put(SerializedField.ORIGIN_LINE_NUMBER, -1: jl.Integer)
                    case SerializedField.ORIGIN_END_LINE_NUMBER =>
                        m.put(SerializedField.ORIGIN_END_LINE_NUMBER, -1: jl.Integer)
                    case SerializedField.ORIGIN_TYPE =>
                        throw new ConfigException.BugOrBroken(
                            "should always be an ORIGIN_TYPE field")
                    case SerializedField.ORIGIN_URL =>
                        m.put(SerializedField.ORIGIN_NULL_URL, "")
                    case SerializedField.ORIGIN_RESOURCE =>
                        m.put(SerializedField.ORIGIN_NULL_RESOURCE, "")
                    case SerializedField.ORIGIN_COMMENTS =>
                        m.put(SerializedField.ORIGIN_NULL_COMMENTS, "")
                    case SerializedField.ORIGIN_NULL_URL |
                        SerializedField.ORIGIN_NULL_RESOURCE |
                        SerializedField.ORIGIN_NULL_COMMENTS =>
                        throw new ConfigException.BugOrBroken(
                            "computing delta, base object should not contain " + f + " " + base)
                    case SerializedField.END_MARKER |
                        SerializedField.ROOT_VALUE |
                        SerializedField.ROOT_WAS_CONFIG |
                        SerializedField.UNKNOWN |
                        SerializedField.VALUE_DATA |
                        SerializedField.VALUE_ORIGIN =>
                        throw new ConfigException.BugOrBroken(
                            "should not appear here: " + f)
                }
            } else {
                // field is in base and child, but differs, so leave it
            }
        }
        m
    }
    @throws[IOException]
    private[impl] def fromFields(m: ju.Map[SerializedField, AnyRef]): SimpleConfigOrigin = {
        // we represent a null origin as one with no fields at all
        if (m.isEmpty) return null
        val description = m.get(SerializedField.ORIGIN_DESCRIPTION).asInstanceOf[String]
        val lineNumber = m.get(SerializedField.ORIGIN_LINE_NUMBER).asInstanceOf[jl.Integer]
        val endLineNumber = m.get(SerializedField.ORIGIN_END_LINE_NUMBER).asInstanceOf[jl.Integer]
        val originTypeOrdinal = m.get(SerializedField.ORIGIN_TYPE).asInstanceOf[Number]
        if (originTypeOrdinal == null) throw new IOException("Missing ORIGIN_TYPE field")
        val originType: OriginType = OriginType.values()(originTypeOrdinal.byteValue)
        val urlOrNull =
            m.get(SerializedField.ORIGIN_URL).asInstanceOf[String]
        var resourceOrNull =
            m.get(SerializedField.ORIGIN_RESOURCE).asInstanceOf[String]
        val commentsOrNull = m.get(SerializedField.ORIGIN_COMMENTS).asInstanceOf[ju.List[String]]
        // Older versions did not have a resource field, they stuffed it into the description.
        if ((originType eq OriginType.RESOURCE) && resourceOrNull == null)
            resourceOrNull = description
        new SimpleConfigOrigin(
            description,
            if (lineNumber != null) lineNumber else -1,
            if (endLineNumber != null) endLineNumber else -1,
            originType,
            urlOrNull,
            resourceOrNull,
            commentsOrNull)
    }
    @throws[IOException]
    private[impl] def applyFieldsDelta(
        base: ju.Map[SerializedField, AnyRef],
        delta: ju.Map[SerializedField, AnyRef]): ju.Map[SerializedField, AnyRef] = {
        val m = new ju.TreeMap[SerializedField, AnyRef](delta) // was EnumMap
        for (baseEntry <- base.entrySet.asScala) {
            val f = baseEntry.getKey
            if (delta.containsKey(f)) {
                // delta overrides when keys are in both
                // "m" should already contain the right thing
            } else {
                // base has the key and delta does not.
                // we inherit from base unless a "NULL" key blocks.
                f match {
                    case SerializedField.ORIGIN_DESCRIPTION =>
                        m.put(f, base.get(f))
                    case SerializedField.ORIGIN_URL =>
                        if (delta.containsKey(SerializedField.ORIGIN_NULL_URL))
                            m.remove(SerializedField.ORIGIN_NULL_URL)
                        else m.put(f, base.get(f))
                    case SerializedField.ORIGIN_RESOURCE =>
                        if (delta.containsKey(SerializedField.ORIGIN_NULL_RESOURCE))
                            m.remove(SerializedField.ORIGIN_NULL_RESOURCE)
                        else m.put(f, base.get(f))
                    case SerializedField.ORIGIN_COMMENTS =>
                        if (delta.containsKey(SerializedField.ORIGIN_NULL_COMMENTS))
                            m.remove(SerializedField.ORIGIN_NULL_COMMENTS)
                        else m.put(f, base.get(f))
                    case SerializedField.ORIGIN_NULL_URL |
                        SerializedField.ORIGIN_NULL_RESOURCE |
                        SerializedField.ORIGIN_NULL_COMMENTS =>
                        // base objects shouldn't contain these, should just
                        // lack the field. these are only in deltas.
                        throw new ConfigException.BugOrBroken(
                            "applying fields, base object should not contain " + f + " " + base)
                    case SerializedField.ORIGIN_END_LINE_NUMBER |
                        SerializedField.ORIGIN_LINE_NUMBER |
                        SerializedField.ORIGIN_TYPE =>
                        m.put(f, base.get(f))
                    case SerializedField.END_MARKER |
                        SerializedField.ROOT_VALUE |
                        SerializedField.ROOT_WAS_CONFIG |
                        SerializedField.UNKNOWN |
                        SerializedField.VALUE_DATA |
                        SerializedField.VALUE_ORIGIN =>
                        throw new ConfigException.BugOrBroken(
                            "should not appear here: " + f)
                }
            }
        }
        m
    }
    @throws[IOException]
    private[impl] def fromBase(
        baseOrigin: SimpleConfigOrigin,
        delta: ju.Map[SerializedField, AnyRef]): SimpleConfigOrigin = {
        var baseFields =
            if (baseOrigin != null) baseOrigin.toFields
            else ju.Collections.emptyMap[SerializedField, AnyRef]
        val fields = applyFieldsDelta(baseFields, delta)
        fromFields(fields)
    }
}
final class SimpleConfigOrigin protected (
    val _description: String,
    val _lineNumber: Int,
    val endLineNumber: Int,
    val originType: OriginType,
    val urlOrNull: String,
    val resourceOrNull: String,
    val commentsOrNull: ju.List[String])
    extends ConfigOrigin {
    if (_description == null) throw new ConfigException.BugOrBroken("description may not be null")

    override def withLineNumber(lineNumber: Int): SimpleConfigOrigin =
        if (lineNumber == this.lineNumber && lineNumber == this.endLineNumber) this
        else
            new SimpleConfigOrigin(
                this._description,
                lineNumber,
                lineNumber,
                this.originType,
                this.urlOrNull,
                this.resourceOrNull,
                this.commentsOrNull)
    private[impl] def addURL(url: URL) = new SimpleConfigOrigin(
        this._description,
        this.lineNumber,
        this.endLineNumber,
        this.originType,
        if (url != null) url.toExternalForm else null,
        this.resourceOrNull,
        this.commentsOrNull)
    override def withComments(comments: ju.List[String]): SimpleConfigOrigin =
        if (ConfigImplUtil.equalsHandlingNull(comments, this.commentsOrNull)) this
        else
            new SimpleConfigOrigin(
                this._description,
                this.lineNumber,
                this.endLineNumber,
                this.originType,
                this.urlOrNull,
                this.resourceOrNull,
                comments)
    private[impl] def prependComments(
        comments: ju.List[String]): SimpleConfigOrigin =
        if (ConfigImplUtil.equalsHandlingNull(comments, this.commentsOrNull) || comments == null) this
        else if (this.commentsOrNull == null) withComments(comments)
        else {
            val merged =
                new ju.ArrayList[String](comments.size + this.commentsOrNull.size)
            merged.addAll(comments)
            merged.addAll(this.commentsOrNull)
            withComments(merged)
        }
    private[impl] def appendComments(
        comments: ju.List[String]): SimpleConfigOrigin =
        if (ConfigImplUtil.equalsHandlingNull(comments, this.commentsOrNull) || comments == null) this
        else if (this.commentsOrNull == null) withComments(comments)
        else {
            val merged =
                new ju.ArrayList[String](comments.size + this.commentsOrNull.size)
            merged.addAll(this.commentsOrNull)
            merged.addAll(comments)
            withComments(merged)
        }
    override def description: String =
        if (lineNumber < 0) _description
        else if (endLineNumber == lineNumber) _description + ": " + lineNumber
        else _description + ": " + lineNumber + "-" + endLineNumber

    override def equals(other: Any): Boolean =
        if (other.isInstanceOf[SimpleConfigOrigin]) {
            val otherOrigin = other.asInstanceOf[SimpleConfigOrigin]
            this._description == otherOrigin._description && this.lineNumber == otherOrigin.lineNumber &&
                this.endLineNumber == otherOrigin.endLineNumber && (this.originType eq otherOrigin.originType) &&
                ConfigImplUtil.equalsHandlingNull(this.urlOrNull, otherOrigin.urlOrNull) &&
                ConfigImplUtil.equalsHandlingNull(this.resourceOrNull, otherOrigin.resourceOrNull)
        } else false

    override def hashCode: Int = {
        var h = 41 * (41 + _description.hashCode)
        h = 41 * (h + lineNumber)
        h = 41 * (h + endLineNumber)
        h = 41 * (h + originType.hashCode)
        if (urlOrNull != null) h = 41 * (h + urlOrNull.hashCode)
        if (resourceOrNull != null) h = 41 * (h + resourceOrNull.hashCode)
        h
    }
    override def toString: String = "ConfigOrigin(" + _description + ")"
    override def filename: String =
        if (originType eq OriginType.FILE)
            _description
        else if (urlOrNull != null) {
            var url: URL = null
            try url = new URL(urlOrNull)
            catch {
                case e: MalformedURLException =>
                    return null
            }
            if (url.getProtocol == "file") url.getFile else null
        } else null

    override def url: URL =
        if (urlOrNull == null) null
        else
            try new URL(urlOrNull)
            catch {
                case e: MalformedURLException =>
                    null
            }
    override def resource: String = resourceOrNull
    override def lineNumber: Int = _lineNumber
    override def comments: ju.List[String] =
        if (commentsOrNull != null) ju.Collections.unmodifiableList(commentsOrNull)
        else ju.Collections.emptyList[String]

    private[impl] def toFields: ju.Map[SerializedField, AnyRef] = {
        val m = new ju.TreeMap[SerializedField, AnyRef] // was EnumMap
        m.put(SerializedField.ORIGIN_DESCRIPTION, _description)
        if (lineNumber >= 0) m.put(SerializedField.ORIGIN_LINE_NUMBER, lineNumber: jl.Integer)
        if (endLineNumber >= 0) m.put(SerializedField.ORIGIN_END_LINE_NUMBER, endLineNumber: jl.Integer)

        m.put(SerializedField.ORIGIN_TYPE, originType.ordinal: jl.Integer)

        if (urlOrNull != null) m.put(SerializedField.ORIGIN_URL, urlOrNull)
        if (resourceOrNull != null) m.put(SerializedField.ORIGIN_RESOURCE, resourceOrNull)
        if (commentsOrNull != null) m.put(SerializedField.ORIGIN_COMMENTS, commentsOrNull)
        m
    }
    private[impl] def toFieldsDelta(baseOrigin: SimpleConfigOrigin): ju.Map[SerializedField, AnyRef] = {
        var baseFields = if (baseOrigin != null) baseOrigin.toFields else ju.Collections.emptyMap[SerializedField, AnyRef]
        SimpleConfigOrigin.fieldsDelta(baseFields, toFields)
    }
}
