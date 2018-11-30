/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FilterReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLConnection
import java.{ util => ju }
import com.typesafe.config._
import com.typesafe.config.parser._

/**
 * Internal implementation detail, not ABI stable, do not touch.
 * For use only by the {@link com.typesafe.config} package.
 * The point of this class is to avoid "propagating" each
 * overload on "thing which can be parsed" through multiple
 * interfaces. Most interfaces can have just one overload that
 * takes a Parseable. Also it's used as an abstract "resource
 * handle" in the ConfigIncluder interface.
 */
object Parseable {

    /**
     * Internal implementation detail, not ABI stable, do not touch.
     */
    protected trait Relativizer {
        def relativeTo(filename: String): ConfigParseable
    }
    private val parseStack =
        new ThreadLocal[ju.LinkedList[Parseable]]() {
            override protected def initialValue = new ju.LinkedList[Parseable]
        }
    private val MAX_INCLUDE_DEPTH = 50
    protected def trace(message: String): Unit = {
        if (ConfigImpl.traceLoadsEnabled) ConfigImpl.trace(message)
    }
    private[impl] def forceParsedToObject(
        value: ConfigValue) =
        if (value.isInstanceOf[AbstractConfigObject])
            value.asInstanceOf[AbstractConfigObject]
        else
            throw new ConfigException.WrongType(
                value.origin,
                "",
                "object at file root",
                value.valueType.name)
    private def readerFromStream(input: InputStream): Reader =
        readerFromStream(input, "UTF-8")
    private def readerFromStream(input: InputStream, encoding: String): Reader =
        try { // well, this is messed up. If we aren't going to close
            // the passed-in InputStream then we have no way to
            // close these readers. So maybe we should not have an
            // InputStream version, only a Reader version.
            val reader = new InputStreamReader(input, encoding)
            new BufferedReader(reader)
        } catch {
            case e: UnsupportedEncodingException =>
                throw new ConfigException.BugOrBroken(
                    "Java runtime does not support UTF-8",
                    e)
        }
    private def doNotClose(input: Reader) = new FilterReader(input) {
        override def close(): Unit = {
            // NOTHING.
        }
    }
    private[impl] def relativeTo(url: URL, filename: String): URL = { // I'm guessing this completely fails on Windows, help wanted
        if (new File(filename).isAbsolute) return null
        try {
            val siblingURI = url.toURI
            val relative = new URI(filename)
            // this seems wrong, but it's documented that the last
            // element of the path in siblingURI gets stripped out,
            // so to get something in the same directory as
            // siblingURI we just call resolve().
            val resolved = siblingURI.resolve(relative).toURL
            resolved
        } catch {
            case e: MalformedURLException =>
                null
            case e: URISyntaxException =>
                null
            case e: IllegalArgumentException =>
                null
        }
    }
    private[impl] def relativeTo(file: File, filename: String): File = {
        val child = new File(filename)
        if (child.isAbsolute) return null
        val parent = file.getParentFile
        if (parent == null) null else new File(parent, filename)
    }
    // this is a parseable that doesn't exist and just throws when you try to
    // parse it
    final private[impl] class ParseableNotFound private[impl] (
        val what: String,
        val message: String,
        options: ConfigParseOptions) extends Parseable(options) {
        postConstruct(options)
        @throws[IOException]
        override protected def reader() = throw new FileNotFoundException(message)
        override protected def createOrigin(): ConfigOrigin =
            SimpleConfigOrigin.newSimple(what)
    }

    def newNotFound(
        whatNotFound: String,
        message: String,
        options: ConfigParseOptions) =
        new ParseableNotFound(whatNotFound, message, options)

    final private[impl] class ParseableReader private[impl] (
        reader: Reader,
        options: ConfigParseOptions) extends Parseable(options) {
        postConstruct(options)
        override protected def reader(): Reader = {
            if (ConfigImpl.traceLoadsEnabled)
                trace("Loading config from reader " + reader)
            reader
        }
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newSimple("Reader")
    }
    // note that we will never close this reader; you have to do it when parsing
    // is complete.
    def newReader(reader: Reader, options: ConfigParseOptions) =
        new ParseableReader(doNotClose(reader), options)

    final private[impl] class ParseableString private[impl] (
        val input: String,
        options: ConfigParseOptions) extends Parseable(options) {
        postConstruct(options)
        override protected def reader: Reader = {
            if (ConfigImpl.traceLoadsEnabled)
                trace("Loading config from a String " + input)
            new StringReader(input)
        }
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newSimple("String")
        override def toString: String = getClass.getSimpleName + "(" + input + ")"
    }
    def newString(input: String, options: ConfigParseOptions) =
        new ParseableString(input, options)
    private val jsonContentType = "application/json"
    private val propertiesContentType = "text/x-java-properties"
    private val hoconContentType = "application/hocon"
    private object ParseableURL {
        private def acceptContentType(options: ConfigParseOptions): String = {
            if (options.getSyntax == null) return null
            options.getSyntax match {
                case ConfigSyntax.JSON =>
                    return jsonContentType
                case ConfigSyntax.CONF =>
                    return hoconContentType
                case ConfigSyntax.PROPERTIES =>
                    return propertiesContentType
            }
            // not sure this is reachable but javac thinks it is
            null
        }
    }

    private[impl] class ParseableURL protected (val input: URL) // does not postConstruct (subclass does it)
        extends Parseable {
        private var contentTypeStr: String = null // shadowing with a different type(ConfigSyntax) doesn't work in Scala
        def this(input: URL, options: ConfigParseOptions) {
            this(input)
            postConstruct(options)
        }
        @throws[IOException]
        override protected def reader = throw new ConfigException.BugOrBroken(
            "reader() without options should not be called on ParseableURL")
        @throws[IOException]
        override protected def reader(options: ConfigParseOptions): Reader =
            try {
                if (ConfigImpl.traceLoadsEnabled)
                    trace("Loading config from a URL: " + input.toExternalForm)
                val connection = input.openConnection
                // allow server to serve multiple types from one URL
                val acceptContent = ParseableURL.acceptContentType(options)
                if (acceptContent != null)
                    connection.setRequestProperty("Accept", acceptContent)
                connection.connect()
                // save content type for later
                contentTypeStr = connection.getContentType
                if (contentTypeStr != null) {
                    if (ConfigImpl.traceLoadsEnabled)
                        trace("URL sets Content-Type: '" + contentTypeStr + "'")
                    contentTypeStr = contentTypeStr.trim
                    val semi = contentTypeStr.indexOf(';')
                    if (semi >= 0) contentTypeStr = contentTypeStr.substring(0, semi)
                }
                val stream = connection.getInputStream
                readerFromStream(stream)
            } catch {
                case fnf: FileNotFoundException =>
                    // If the resource is not found (HTTP response
                    // code 404 or something alike), then it's fine to
                    // treat it according to the allowMissing setting
                    // and "include" spec.  But if we have something
                    // like HTTP 503 it seems to be better to fail
                    // early, because this may be a sign of broken
                    // environment. Java throws FileNotFoundException
                    // if it sees 404 or 410.
                    throw fnf
                case e: IOException =>
                    throw new ConfigException.BugOrBroken(
                        "Cannot load config from URL: " + input.toExternalForm,
                        e)
            }
        override private[impl] def guessSyntax: ConfigSyntax =
            ConfigImplUtil.syntaxFromExtension(input.getPath)
        override private[impl] def contentType(): ConfigSyntax =
            if (contentTypeStr != null)
                if (contentTypeStr == jsonContentType) ConfigSyntax.JSON
                else if (contentTypeStr == propertiesContentType) ConfigSyntax.PROPERTIES
                else if (contentTypeStr == hoconContentType) ConfigSyntax.CONF
                else {
                    if (ConfigImpl.traceLoadsEnabled)
                        trace("'" + contentTypeStr + "' isn't a known content type")
                    null
                }
            else null
        override private[impl] def relativeTo(filename: String): ConfigParseable = {
            val url = Parseable.relativeTo(input, filename)
            if (url == null) return null
            newURL(url, options.setOriginDescription(null))
        }
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newURL(input)
        override def toString: String =
            getClass.getSimpleName + "(" + input.toExternalForm + ")"
    }
    def newURL(input: URL, options: ConfigParseOptions): Parseable = { // we want file: URLs and files to always behave the same, so switch
        // to a file if it's a file: URL
        if (input.getProtocol == "file")
            newFile(ConfigImplUtil.urlToFile(input), options)
        else new ParseableURL(input, options)
    }
    final private[impl] class ParseableFile private[impl] (
        val input: File,
        options: ConfigParseOptions) extends Parseable(options) {
        postConstruct(options)
        @throws[IOException]
        override protected def reader: Reader = {
            if (ConfigImpl.traceLoadsEnabled)
                trace("Loading config from a file: " + input)
            val stream = new FileInputStream(input)
            readerFromStream(stream)
        }
        override private[impl] def guessSyntax =
            ConfigImplUtil.syntaxFromExtension(input.getName)
        override private[impl] def relativeTo(filename: String): ConfigParseable = {
            val sibling: File =
                if (new File(filename).isAbsolute) new File(filename)
                else { // this may return null
                    Parseable.relativeTo(input, filename)
                }
            if (sibling == null) return null
            if (sibling.exists) {
                trace(sibling + " exists, so loading it as a file")
                newFile(sibling, options.setOriginDescription(null))
            } else {
                trace(sibling + " does not exist, so trying it as a classpath resource")
                super.relativeTo(filename)
            }
        }
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newFile(input.getPath)
        override def toString: String =
            getClass.getSimpleName + "(" + input.getPath + ")"
    }
    def newFile(input: File, options: ConfigParseOptions) =
        new ParseableFile(input, options)
    final private class ParseableResourceURL private[impl] (
        input: URL,
        options: ConfigParseOptions,
        val resource: String,
        val relativizer: Relativizer) extends ParseableURL(input, options) {
        postConstruct(options)
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newResource(resource, input)
        override private[impl] def relativeTo(filename: String) =
            relativizer.relativeTo(filename)
    }
    private def newResourceURL(
        input: URL,
        options: ConfigParseOptions,
        resource: String,
        relativizer: Relativizer) =
        new ParseableResourceURL(
            input,
            options,
            resource,
            relativizer)
    private object ParseableResources {
        private[impl] def parent(resource: String) = { // the "resource" is not supposed to begin with a "/"
            // because it's supposed to be the raw resource
            // (ClassLoader#getResource), not the
            // resource "syntax" (Class#getResource)
            val i = resource.lastIndexOf('/')
            if (i < 0) null else resource.substring(0, i)
        }
    }
    final private class ParseableResources private[impl] (
        val resource: String,
        options: ConfigParseOptions) extends Parseable(options) with Relativizer {
        postConstruct(options)
        @throws[IOException]
        override protected def reader = throw new ConfigException.BugOrBroken(
            "reader() should not be called on resources")
        @throws[IOException]
        override protected def rawParseValue(
            origin: ConfigOrigin,
            finalOptions: ConfigParseOptions): AbstractConfigObject = {
            val loader = finalOptions.getClassLoader
            if (loader == null)
                throw new ConfigException.BugOrBroken(
                    "null class loader; pass in a class loader or use Thread.currentThread().setContextClassLoader()")
            val e = loader.getResources(resource)
            if (!e.hasMoreElements) {
                if (ConfigImpl.traceLoadsEnabled)
                    trace(
                        "Loading config from class loader " + loader + " but there were no resources called " + resource)
                throw new IOException("resource not found on classpath: " + resource)
            }
            var merged: AbstractConfigObject = SimpleConfigObject.empty(origin)
            while ({ e.hasMoreElements }) {
                val url = e.nextElement
                if (ConfigImpl.traceLoadsEnabled)
                    trace(
                        "Loading config from resource '" + resource + "' URL " + url.toExternalForm + " from class loader " + loader)
                val element =
                    newResourceURL(url, finalOptions, resource, this)
                val v = element.parseValue
                merged = merged.withFallback(v)
            }
            merged
        }
        override private[impl] def guessSyntax =
            ConfigImplUtil.syntaxFromExtension(resource)
        override def relativeTo(sibling: String): ConfigParseable =
            if (sibling.startsWith("/")) { // if it starts with "/" then don't make it relative to
                // the including resource
                newResources(
                    sibling.substring(1),
                    options.setOriginDescription(null))
            } else { // here we want to build a new resource name and let
                // the class loader have it, rather than getting the
                // url with getResource() and relativizing to that url.
                // This is needed in case the class loader is going to
                // search a classpath.
                val parent = ParseableResources.parent(resource)
                if (parent == null) newResources(sibling, options.setOriginDescription(null))
                else newResources(
                    parent + "/" + sibling,
                    options.setOriginDescription(null))
            }
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newResource(resource)
        override def toString: String = getClass.getSimpleName + "(" + resource + ")"
    }
    def newResources(
        klass: Class[_],
        resource: String,
        options: ConfigParseOptions): Parseable = newResources(
        convertResourceName(klass, resource),
        options.setClassLoader(klass.getClassLoader))
    // this function is supposed to emulate the difference
    // between Class.getResource and ClassLoader.getResource
    // (unfortunately there doesn't seem to be public API for it).
    // We're using it because the Class API is more limited,
    // for example it lacks getResources(). So we want to be able to
    // use ClassLoader directly.
    private def convertResourceName(klass: Class[_], resource: String) =
        if (resource.startsWith("/")) { // "absolute" resource, chop the slash
            resource.substring(1)
        } else {
            val className = klass.getName
            val i = className.lastIndexOf('.')
            if (i < 0) { // no package
                resource
            } else { // need to be relative to the package
                val packageName = className.substring(0, i)
                val packagePath = packageName.replace('.', '/')
                packagePath + "/" + resource
            }
        }
    def newResources(resource: String, options: ConfigParseOptions): Parseable = {
        if (options.getClassLoader == null)
            throw new ConfigException.BugOrBroken(
                "null class loader; pass in a class loader or use Thread.currentThread().setContextClassLoader()")
        new ParseableResources(resource, options)
    }
    final private[impl] class ParseableProperties private[impl] (
        val props: ju.Properties,
        options: ConfigParseOptions) extends Parseable(options) {
        postConstruct(options)
        @throws[IOException]
        override protected def reader = throw new ConfigException.BugOrBroken(
            "reader() should not be called on props")
        override protected def rawParseValue(
            origin: ConfigOrigin,
            finalOptions: ConfigParseOptions): AbstractConfigObject = {
            if (ConfigImpl.traceLoadsEnabled)
                trace("Loading config from properties " + props)
            PropertiesParser.fromProperties(origin, props)
        }
        override private[impl] def guessSyntax = ConfigSyntax.PROPERTIES
        override protected def createOrigin: ConfigOrigin =
            SimpleConfigOrigin.newSimple("properties")
        override def toString: String =
            getClass.getSimpleName + "(" + props.size + " props)"
    }
    def newProperties(
        properties: ju.Properties,
        options: ConfigParseOptions) =
        new ParseableProperties(properties, options)
}

abstract class Parseable protected (private var initialOptions: ConfigParseOptions) extends ConfigParseable {
    private var includeContext: ConfigIncludeContext = null
    //private var initialOptions = null
    private var initialOrigin: ConfigOrigin = null
    def this() = this(null)
    private def fixupOptions(
        baseOptions: ConfigParseOptions) = {
        var syntax = baseOptions.getSyntax
        if (syntax == null) syntax = guessSyntax
        if (syntax == null) syntax = ConfigSyntax.CONF
        var modified = baseOptions.setSyntax(syntax)
        // make sure the app-provided includer falls back to default
        modified = modified.appendIncluder(ConfigImpl.defaultIncluder)
        // make sure the app-provided includer is complete
        modified =
            modified.setIncluder(SimpleIncluder.makeFull(modified.getIncluder))
        modified
    }
    protected def postConstruct(baseOptions: ConfigParseOptions): Unit = {
        this.initialOptions = fixupOptions(baseOptions)
        this.includeContext = new SimpleIncludeContext(this)
        if (initialOptions.getOriginDescription != null)
            initialOrigin =
                SimpleConfigOrigin.newSimple(initialOptions.getOriginDescription)
        else initialOrigin = createOrigin()
    }
    // the general idea is that any work should be in here, not in the
    // constructor, so that exceptions are thrown from the public parse()
    // function and not from the creation of the Parseable.
    // Essentially this is a lazy field. The parser should close the
    // reader when it's done with it.
    // ALSO, IMPORTANT: if the file or URL is not found, this must throw.
    // to support the "allow missing" feature.
    @throws[IOException]
    protected def reader(): Reader
    @throws[IOException]
    protected def reader(options: ConfigParseOptions): Reader = reader()
    private[impl] def guessSyntax(): ConfigSyntax = null
    private[impl] def contentType(): ConfigSyntax = null
    private[impl] def relativeTo(filename: String): ConfigParseable = {
        // fall back to classpath; we treat the "filename" as absolute
        // (don't add a package name in front),
        // if it starts with "/" then remove the "/", for consistency
        // with ParseableResources.relativeTo
        var resource = filename
        if (filename.startsWith("/")) resource = filename.substring(1)
        Parseable.newResources(resource, options.setOriginDescription(null))
    }
    //private[impl] def includeContext(): ConfigIncludeContext = includeContext
    override def parse(baseOptions: ConfigParseOptions): ConfigObject = {
        val stack = Parseable.parseStack.get
        if (stack.size >= Parseable.MAX_INCLUDE_DEPTH)
            throw new ConfigException.Parse(
                initialOrigin,
                "include statements nested more than " + Parseable.MAX_INCLUDE_DEPTH + " times, you probably have a cycle in your includes. Trace: " + stack)
        stack.addFirst(this)
        try Parseable.forceParsedToObject(parseValue(baseOptions))
        finally {
            stack.removeFirst
            if (stack.isEmpty) Parseable.parseStack.remove()
        }
    }
    final private[impl] def parseValue(
        baseOptions: ConfigParseOptions): AbstractConfigValue = { // note that we are NOT using our "initialOptions",
        // but using the ones from the passed-in options. The idea is that
        // callers can get our original options and then parse with different
        // ones if they want.
        val options = fixupOptions(baseOptions)
        // passed-in options can override origin
        val origin =
            if (options.getOriginDescription != null) SimpleConfigOrigin.newSimple(options.getOriginDescription)
            else initialOrigin
        parseValue(origin, options)
    }
    final private def parseValue(
        origin: ConfigOrigin,
        finalOptions: ConfigParseOptions) = try rawParseValue(origin, finalOptions)
    catch {
        case e: IOException =>
            if (finalOptions.getAllowMissing) {
                Parseable.trace(
                    e.getMessage + ". Allowing Missing File, this can be turned off by setting" + " ConfigParseOptions.allowMissing = false")
                SimpleConfigObject.emptyMissing(origin)
            } else {
                Parseable.trace(
                    "exception loading " + origin.description + ": " + e.getClass.getName + ": " + e.getMessage)
                throw new ConfigException.IO(
                    origin,
                    e.getClass.getName + ": " + e.getMessage,
                    e)
            }
    }
    final private[impl] def parseDocument(
        baseOptions: ConfigParseOptions): ConfigDocument = {
        val options = fixupOptions(baseOptions)
        var origin =
            if (options.getOriginDescription != null) SimpleConfigOrigin.newSimple(options.getOriginDescription) else initialOrigin
        parseDocument(origin, options)
    }
    final private def parseDocument(
        origin: ConfigOrigin,
        finalOptions: ConfigParseOptions): ConfigDocument =
        try rawParseDocument(origin, finalOptions) catch {
            case e: IOException =>
                if (finalOptions.getAllowMissing) {
                    val children =
                        new ju.ArrayList[AbstractConfigNode]
                    children.add(
                        new ConfigNodeObject(new ju.ArrayList[AbstractConfigNode]))
                    new SimpleConfigDocument(
                        new ConfigNodeRoot(children, origin),
                        finalOptions)
                } else {
                    Parseable.trace(
                        "exception loading " + origin.description + ": " + e.getClass.getName + ": " + e.getMessage)
                    throw new ConfigException.IO(
                        origin,
                        e.getClass.getName + ": " + e.getMessage,
                        e)
                }
        }
    // this is parseValue without post-processing the IOException or handling
    // options.getAllowMissing()
    @throws[IOException]
    protected def rawParseValue(
        origin: ConfigOrigin,
        finalOptions: ConfigParseOptions): AbstractConfigValue = {
        val readerVal: Reader = reader(finalOptions)
        // after reader() we will have loaded the Content-Type.
        val contentTypeVal: ConfigSyntax = contentType()
        val optionsWithContentType =
            if (contentType != null) {
                if (ConfigImpl.traceLoadsEnabled && finalOptions.getSyntax != null) Parseable.trace(
                    "Overriding syntax " + finalOptions.getSyntax + " with Content-Type which specified " + contentType)
                finalOptions.setSyntax(contentTypeVal)
            } else finalOptions
        try rawParseValue(readerVal, origin, optionsWithContentType)
        finally readerVal.close()
    }
    @throws[IOException]
    private def rawParseValue(
        reader: Reader,
        origin: ConfigOrigin,
        finalOptions: ConfigParseOptions) = if (finalOptions.getSyntax eq ConfigSyntax.PROPERTIES)
        PropertiesParser.parse(reader, origin)
    else {
        val tokens =
            Tokenizer.tokenize(origin, reader, finalOptions.getSyntax)
        val document =
            ConfigDocumentParser.parse(tokens, origin, finalOptions)
        ConfigParser.parse(document, origin, finalOptions, includeContext)
    }
    // this is parseDocument without post-processing the IOException or handling
    @throws[IOException]
    protected def rawParseDocument(
        origin: ConfigOrigin,
        finalOptions: ConfigParseOptions): ConfigDocument = {
        val readerVal: Reader = reader(finalOptions)
        val contentTypeVal: ConfigSyntax = contentType()
        val optionsWithContentType =
            if (contentType != null) {
                if (ConfigImpl.traceLoadsEnabled && finalOptions.getSyntax != null) Parseable.trace(
                    "Overriding syntax " + finalOptions.getSyntax + " with Content-Type which specified " + contentType)
                finalOptions.setSyntax(contentTypeVal)
            } else finalOptions
        try rawParseDocument(readerVal, origin, optionsWithContentType)
        finally readerVal.close()
    }
    @throws[IOException]
    private def rawParseDocument(
        reader: Reader,
        origin: ConfigOrigin,
        finalOptions: ConfigParseOptions) = {
        val tokens =
            Tokenizer.tokenize(origin, reader, finalOptions.getSyntax)
        new SimpleConfigDocument(
            ConfigDocumentParser.parse(tokens, origin, finalOptions),
            finalOptions)
    }
    def parse(): ConfigObject = Parseable.forceParsedToObject(parseValue(options))
    def parseConfigDocument(): ConfigDocument = parseDocument(options)
    private[impl] def parseValue(): AbstractConfigValue = parseValue(options)
    override final def origin(): ConfigOrigin = initialOrigin
    protected def createOrigin(): ConfigOrigin
    override def options(): ConfigParseOptions = initialOptions
    override def toString(): String = getClass.getSimpleName
}
