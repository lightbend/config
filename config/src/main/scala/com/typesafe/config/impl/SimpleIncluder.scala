/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.{ util => ju }
import scala.collection.JavaConverters._

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigIncludeContext
import com.typesafe.config.ConfigIncluder
import com.typesafe.config.ConfigIncluderClasspath
import com.typesafe.config.ConfigIncluderFile
import com.typesafe.config.ConfigIncluderURL
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigParseable
import com.typesafe.config.ConfigSyntax

object SimpleIncluder { // ConfigIncludeContext does this for us on its options

    private[impl] def clearForInclude(
        options: ConfigParseOptions) = { // the class loader and includer are inherited, but not this other
        // stuff.
        options
            .setSyntax(null)
            .setOriginDescription(null)
            .setAllowMissing(true)
    }

    // the heuristic includer in static form
    private[impl] def includeWithoutFallback(
        context: ConfigIncludeContext,
        name: String) = { // the heuristic is valid URL then URL, else relative to including file;
        // relativeTo in a file falls back to classpath inside relativeTo().
        var url: URL = null
        try url = new URL(name)
        catch {
            case e: MalformedURLException =>
                url = null
        }
        if (url != null) includeURLWithoutFallback(context, url) else {
            val source =
                new RelativeNameSource(context)
            fromBasename(source, name, context.parseOptions)
        }
    }

    private[impl] def includeURLWithoutFallback(
        context: ConfigIncludeContext,
        url: URL) = ConfigFactory.parseURL(url, context.parseOptions).root

    private[impl] def includeFileWithoutFallback(
        context: ConfigIncludeContext,
        file: File) = ConfigFactory.parseFileAnySyntax(file, context.parseOptions).root

    private[impl] def includeResourceWithoutFallback(
        context: ConfigIncludeContext,
        resource: String) = ConfigFactory
        .parseResourcesAnySyntax(resource, context.parseOptions)
        .root

    private[impl] trait NameSource {
        def nameToParseable(
            name: String,
            parseOptions: ConfigParseOptions): ConfigParseable
    }

    private class RelativeNameSource private[impl] (
        val context: ConfigIncludeContext) extends SimpleIncluder.NameSource {

        override def nameToParseable(
            name: String,
            options: ConfigParseOptions): ConfigParseable = {
            val p = context.relativeTo(name)
            if (p == null) { // avoid returning null
                Parseable.newNotFound(
                    name,
                    "include was not found: '" + name + "'",
                    options)
            } else p
        }
    }

    // this function is a little tricky because there are three places we're
    // trying to use it; for 'include "basename"' in a .conf file, for
    // loading app.{conf,json,properties} from classpath, and for
    // loading app.{conf,json,properties} from the filesystem.
    /*private[impl]*/ def fromBasename(
        source: SimpleIncluder.NameSource,
        name: String,
        options: ConfigParseOptions) = {

        var obj: ConfigObject = null
        if (name.endsWith(".conf") || name.endsWith(".json") || name.endsWith(
            ".properties")) {
            val p = source.nameToParseable(name, options)
            obj = p.parse(p.options.setAllowMissing(options.getAllowMissing))
        } else {
            val confHandle =
                source.nameToParseable(name + ".conf", options)
            val jsonHandle =
                source.nameToParseable(name + ".json", options)
            val propsHandle =
                source.nameToParseable(name + ".properties", options)
            var gotSomething = false
            val fails = new ju.ArrayList[ConfigException.IO]
            val syntax = options.getSyntax
            obj = SimpleConfigObject.empty(SimpleConfigOrigin.newSimple(name))
            if (syntax == null || (syntax eq ConfigSyntax.CONF)) try {
                obj = confHandle.parse(
                    confHandle.options
                        .setAllowMissing(false)
                        .setSyntax(ConfigSyntax.CONF))
                gotSomething = true
            } catch {
                case e: ConfigException.IO =>
                    fails.add(e)
            }
            if (syntax == null || (syntax eq ConfigSyntax.JSON)) try {
                val parsed = jsonHandle.parse(
                    jsonHandle.options
                        .setAllowMissing(false)
                        .setSyntax(ConfigSyntax.JSON))
                obj = obj.withFallback(parsed)
                gotSomething = true
            } catch {
                case e: ConfigException.IO =>
                    fails.add(e)
            }
            if (syntax == null || (syntax eq ConfigSyntax.PROPERTIES)) try {
                val parsed = propsHandle.parse(
                    propsHandle.options
                        .setAllowMissing(false)
                        .setSyntax(ConfigSyntax.PROPERTIES))
                obj = obj.withFallback(parsed)
                gotSomething = true
            } catch {
                case e: ConfigException.IO =>
                    fails.add(e)
            }
            if (!options.getAllowMissing && !gotSomething) {
                if (ConfigImpl.traceLoadsEnabled) { // the individual exceptions should have been logged already
                    // with tracing enabled
                    ConfigImpl.trace(
                        "Did not find '" + name + "' with any extension (.conf, .json, .properties); " + "exceptions should have been logged above.")
                }
                if (fails.isEmpty) { // this should not happen
                    throw new ConfigException.BugOrBroken(
                        "should not be reached: nothing found but no exceptions thrown")
                } else {
                    val sb = new StringBuilder
                    for (t <- fails.asScala) {
                        sb.append(t.getMessage)
                        sb.append(", ")
                    }
                    sb.setLength(sb.length - 2)
                    throw new ConfigException.IO(
                        SimpleConfigOrigin.newSimple(name),
                        sb.toString,
                        fails.get(0))
                }
            } else if (!gotSomething)
                if (ConfigImpl.traceLoadsEnabled)
                    ConfigImpl.trace(
                        "Did not find '" + name + "' with any extension (.conf, .json, .properties); but '" + name + "' is allowed to be missing. Exceptions from load attempts should have been logged above.")
        }
        obj
    }

    // the Proxy is a proxy for an application-provided includer that uses our
    // default implementations when the application-provided includer doesn't
    // have an implementation.
    private class Proxy private[impl] (val delegate: ConfigIncluder)
        extends FullIncluder {

        override def withFallback(fallback: ConfigIncluder): ConfigIncluder = { // we never fall back
            this
        }

        override def include(
            context: ConfigIncludeContext,
            what: String): ConfigObject = delegate.include(context, what)

        override def includeResources(
            context: ConfigIncludeContext,
            what: String): ConfigObject = if (delegate.isInstanceOf[ConfigIncluderClasspath])
            delegate
                .asInstanceOf[ConfigIncluderClasspath]
                .includeResources(context, what)
        else includeResourceWithoutFallback(context, what)

        override def includeURL(
            context: ConfigIncludeContext,
            what: URL): ConfigObject = if (delegate.isInstanceOf[ConfigIncluderURL])
            delegate
                .asInstanceOf[ConfigIncluderURL]
                .includeURL(context, what)
        else includeURLWithoutFallback(context, what)

        override def includeFile(
            context: ConfigIncludeContext,
            what: File): ConfigObject = if (delegate.isInstanceOf[ConfigIncluderFile])
            delegate
                .asInstanceOf[ConfigIncluderFile]
                .includeFile(context, what)
        else includeFileWithoutFallback(context, what)
    }

    /*private[impl]*/ def makeFull(includer: ConfigIncluder) = if (includer.isInstanceOf[FullIncluder]) includer.asInstanceOf[FullIncluder]
    else new SimpleIncluder.Proxy(includer)
}

class SimpleIncluder private[impl] (var fallback: ConfigIncluder)
    extends FullIncluder {

    // this is the heuristic includer
    override def include(
        context: ConfigIncludeContext,
        name: String): ConfigObject = {
        val obj = SimpleIncluder.includeWithoutFallback(context, name)
        // now use the fallback includer if any and merge
        // its result.
        if (fallback != null) obj.withFallback(fallback.include(context, name)) else obj
    }

    override def includeURL(
        context: ConfigIncludeContext,
        url: URL): ConfigObject = {
        val obj =
            SimpleIncluder.includeURLWithoutFallback(context, url)
        if (fallback != null && fallback.isInstanceOf[ConfigIncluderURL]) obj.withFallback(
            fallback.asInstanceOf[ConfigIncluderURL].includeURL(context, url))
        else obj
    }

    override def includeFile(
        context: ConfigIncludeContext,
        file: File): ConfigObject = {
        val obj =
            SimpleIncluder.includeFileWithoutFallback(context, file)
        if (fallback != null && fallback.isInstanceOf[ConfigIncluderFile]) obj.withFallback(
            fallback.asInstanceOf[ConfigIncluderFile].includeFile(context, file))
        else obj
    }

    override def includeResources(
        context: ConfigIncludeContext,
        resource: String): ConfigObject = {
        val obj =
            SimpleIncluder.includeResourceWithoutFallback(context, resource)
        if (fallback != null && fallback.isInstanceOf[ConfigIncluderClasspath]) obj.withFallback(
            fallback
                .asInstanceOf[ConfigIncluderClasspath]
                .includeResources(context, resource))
        else obj
    }

    override def withFallback(fallback: ConfigIncluder): ConfigIncluder = if (this eq fallback)
        throw new ConfigException.BugOrBroken("trying to create includer cycle")
    else if (this.fallback eq fallback) this
    else if (this.fallback != null)
        new SimpleIncluder(this.fallback.withFallback(fallback))
    else new SimpleIncluder(fallback)
}
