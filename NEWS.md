# 1.0.2: July 3, 2013

 - ignore byte-order mark (BOM), treating it as whitespace
 - very minor docs/build improvements

# 1.0.1: May 19, 2013

 - when an array is requested and an object found, try to convert
   the object to an array if the object has numeric keys in it.
   This is intended to support `-Dfoo.0=bar, -Dfoo.1=baz` which
   would create `foo : { "0" : "bar", "1" : "baz" }`; which in
   turn could now be treated as if it were `foo :
   ["bar","baz"]`. This is useful for creating array values on the
   command line using Java system properties.
 - fix a ConcurrentModificationException if an app modified
   system properties while we were trying to parse them.
 - fix line numbering in error messages for newlines within triple
   quotes.

# 1.0.0: October 15, 2012

 - no changes from 0.6.0. ABI now guaranteed for 1.0.x series.

# 0.6.0: October 10, 2012

 - add ConfigRenderOptions.setJson which can be used to enable or
   disable the use of HOCON extensions (other than comments, which
   remain separately-controlled). Right now setJson(false) will
   result in a somewhat prettier rendering using extensions.
 - add ConfigFactory.invalidateCaches() to support reloading
   system properties (mostly this is intended for use in
   unit tests).
 - make ConfigException serializable, in case you have some
   logging system or similar that relies on that. Serialization
   of ConfigException is not guaranteed to be compatible across
   releases.

# 0.5.2: September 6, 2012

 - add versions of ConfigFactory.load() which let you specify
   ConfigParseOptions and ConfigResolveOptions.

# 0.5.0: July 5, 2012

 - triple-quoted strings as in Python or Scala
 - obscure backward incompatibilities:
    - `""""` previously parsed as two empty strings concatenated
      into a single empty string, now it parses as an unterminated
      triple-quoted string.
    - a triple-quoted string like `"""\n"""` previously parsed as
      an empty string, a string with one newline character, and
      another empty string, all concatenated into a single
      string. Now it parses as a string with two characters
      (backslash and lowercase "n").
    - in short you could have two adjacent quoted strings before,
      where one was an empty string, and now you can't.  As far as
      I know, the empty string was always worthless in this case
      and can just be removed.
 - added methods atPath() and atKey() to ConfigValue, to wrap
   the value into a Config
 - added method withValue() to Config and ConfigObject,
   to add a value at a given path or key

# 0.4.1: May 22, 2012

 - publish as OSGi bundle

# 0.4.0: April 12, 2012

 - this is **rolling toward 1.0** and should be pretty much
   feature-complete.
 - this version is published on **Maven central** so you need
   to update your dependency coordinates
 - the **serialization format has changed** to one that's
   extensible and lets the library evolve without breaking
   serialization all the time. The new format is also much more
   compact. However, this change is incompatible with old
   serializations, if you rely on that. The hope is to avoid
   serialization breakage in the future now that the format is not
   the default Java one (which was a direct dump of all the
   implementation details).
 - **serializing an unresolved Config** (one that hasn't had
   resolve() called on it) is no longer supported, you will get
   NotSerializableException if you try.
 - ConfigValue.render() now supports ConfigRenderOptions which
   means you can get a **no-whitespace no-comments plain JSON
   rendering** of a ConfigValue
 - supports **self-referential substitutions**, such as
   `path=${path}":/bin"`, by "looking backward" to the previous
   value of `path`
 - supports **concatenating arrays and merging objects within a
   single value**. So you can do `path=${path} [ "/bin" ]` for
   example. See README and spec for more details.
 - supports **array append** `+=` where `path+="/bin"` expands to
   `path=${?path} [ "/bin" ]`
 - supports **specifying type of include** `include
   url("http://example.com/")`, `include file("/my/file.conf")`,
   and `include classpath("whatever")`.  This syntax forces
   treatment as URL, file, or classpath resource.
 - supports **including URLs** `include
   "http://example.com/whatever.conf"` (if an include is a valid
   URL, it's loaded as such). This is incompatible with prior
   versions, if you have a filename that is also a valid URL, it
   would have loaded previously but now it will not. Use the
   `include file("")` syntax to force treatment as a file.
 - **class loaders are now recursively inherited** through include
   statements; previously, even if you set a custom class loader
   when parsing a file, it would not be used for parsing a
   classpath resource included from the file.
 - parseString() and parseReader() now support include statements
   in the parsed string or reader
 - in -Dconfig.resource=name, name can start with a "/" or not,
   doesn't matter
 - if you implement ConfigIncluder, you should most likely also
   implement ConfigIncluderFile, ConfigIncluderURL, and
   ConfigIncluderClasspath. You should also use
   ConfigIncludeContext.parseOptions() if appropriate.
 - cycles in include statements (self-includes) are now detected
   and result in a nicer error instead of stack overflow
 - since 0.3.0, there is an obscure incompatible semantic change
   in that self-referential substitutions where the cycle could
   be broken by partially resolving the object now "look backward"
   and may fail to resolve. This is not incompatible with the
   version included in Play/Akka 2.0 because in that version this
   obscure case just threw an exception. But in 0.3.0 there
   were cases that worked that now work differently. You are very
   unlikely to be affected by this.
 - Play/Akka 2.0 do not and will not have the new stuff in this
   version due to the serialization break, they will update
   next time they bump their ABI.

# 0.3.1: August 6, 2012

 - 0.3.1 is a backport of the "-Dconfig.resource=name can start with a
   /" fix to 0.3.0
 - 0.3.1 was published on Maven Central while 0.3.0 and earlier
   are only on Typesafe's repository
 - 0.3.1 is mostly intended for use by Akka 2.0.x (and therefore
   Play 2.0.x), everyone else should use a higher version number

# 0.3.0: March 1, 2012

 - ConfigFactory methods now use the thread's context class loader
   by default, and have overloads so you can specify a class
   loader. Because jars may come with "reference.conf" in the jar,
   a config must always be loaded with the same class loader as
   the jar using the config.
 - ConfigValue instances are now serializable
 - new methods ConfigObject.withoutKey, ConfigObject.withOnlyKey,
   Config.withoutPath, Config.withOnlyPath allow subsetting
   configs more easily.
 - better handle complex interdependent substitutions (the
   `${foo}` syntax) without getting confused; just about anything
   that makes conceptual sense should now work. Only inherently
   circular config files should fail.
 - some minor documentation fixes.
