# 1.3.0-M3: April 21, 2015

- this is an ABI-not-guaranteed beta release in advance
  of 1.3.0. Please see the notes for 1.3.0-M1 below for warnings,
  caveats, and the bulk of what's changed since 1.2.1.

API changes (since 1.3.0-M2):

- renamed some methods in the new ConfigDocument for consistency
  with Config (breaks ABI vs. 1.3.0-M2, but not vs. any stable
  release)

Fixes:

- couple of bugfixes in ConfigDocument

Thank you to contributors with commits since v1.3.0-M2 tag:

- Preben Ingvaldsen

# 1.3.0-M2: April 1, 2015

- not in fact an April Fool's joke. Unless it's broken. Then it
  was.
- this is an ABI-not-guaranteed beta release in advance
  of 1.3.0. Please see the notes for 1.3.0-M1 below for warnings,
  caveats, and the bulk of what's changed since 1.2.1.
- this release churns the internals a good bit since 1.3.0-M1,
  so would benefit from your testing efforts.

New API (since 1.3.0-M1):

- added Config.hasPathOrNull
- added Config.getIsNull
- added parser.ConfigDocument which supports simple load/edit/save
  on a config file. For now, the only allowed edits are
  removing/replacing values. This was a major effort (redoing the
  whole parser), implemented by Preben Ingvaldsen.

Fixes:

- added missing @since tags to javadoc
- fixed obscure bug in converting to camel case when instantiating
  beans

Thank you to contributors with commits since v1.3.0-M1 tag:

- Glen Ford
- Jay McCure
- Preben Ingvaldsen

# 1.3.0-M1: March 6, 2015

- this is an ABI-not-guaranteed beta release in advance
  of 1.3.0. The public, documented ABI should not be broken
  vs. 1.2.0; however, there are enough changes that something
  certainly could break, and some obscure corner cases have
  changed semantics and that could bite some people.

Changes that are most likely to break something:

- now built with Java 8 and requires Java 8
- if you were relying on the order of key iteration in a config,
  note that Java 8 changed the iteration order for hashes
  and that includes `Config` and `ConfigObject`
- several correctness fixes to resolving substitutions (the
  `${foo}` syntax). These should only matter in weird corner
  cases, but some people did encounter the problems such as in
  #177.
- throw an exception if a size-in-bytes values are out of Long
  range #170
- two adjacent undefined concatenation elements (like
  `${?foo}${?bar}`) now become undefined instead of throwing an
  exception
- when rendering a path that starts with a digit, don't put
  quotes around it
- set the Accept header on http requests when loading
  config from a URL
- when getting a 404 from a URL, treat it as a missing file
  (silently ignore) instead of throwing exception.
  Other error codes will still throw an exception.
- ConfigParseOptions.prependIncluder/appendIncluder always
  throw when given a null includer, formerly they only
  threw sometimes

New API:

- `ConfigBeanFactory` will auto-fill a JavaBean from
  a `Config`
- it is now possible to create a `ConfigOrigin` using
  `ConfigOriginFactory` and to modify origins on values
  using `ConfigValue.withOrigin`
- `Config.getMemorySize` returns a `ConfigMemorySize`
- `Config.getDuration` returns a `java.time.Duration`
- the existing `ConfigValueFactory.fromAnyRef` and related
  methods now pass through a `ConfigValue` instead of throwing
  an exception
- `ConfigFactory.defaultApplication()` returns the default
  `Config` used by `ConfigFactory.load()` in between
  `defaultReference()` and `defaultOverrides()`, leaving
  `ConfigFactory.load()` as a trivial convenience API
  that uses no internal magic.

Improvements:

- allow duration abbreviations "nanos", "millis", "micros"
- Config.hasPath is now _much_ faster, so if you were caching to
  avoid this you may be able to stop
- new debug option -Dconfig.trace=substitutions explains
  how `${foo}` references are being resolved
- sort numeric keys in numeric order when rendering
- allow whitespace in between two substitutions referring to
  objects or lists when concatenating them, so `${foo} ${bar}`
  and `${foo}${bar}` are now the same if foo and bar are objects
  or lists.
- better error messages for problems loading resources from
  classpath, now we show the jar URL that failed
- even more test coverage!
- lots of minor javadoc fixes
- method names in javadoc now link to github source

Bug fixes:

- fix "allow unresolved" behavior for unresolved list elements
- class loaders are cached with a WeakReference to avoid leaks
  #171
- create valid output for values with multiline descriptions
  #239
- `-Dsun.io.serialization.extendedDebugInfo=true` no longer
  explodes due to calling toString on an internal object,
  #176

Thank you to contributors with commits since v1.2.1 tag:

- Alex Wei
- Andrey Zaytsev
- Ben Jackman
- Ben McCann
- Chris Martin
- Dale Wijnand
- Francois Dang Ngoc
- ian
- KAWACHI Takashi
- Kornel Kielczewski
- Lunfu Zhong
- Michel Daviot
- Paul Phillips
- Pavel Yakunin
- Preben Ingvaldsen
- verbeto
- Wu Zhenwei

# 1.2.1: May 2, 2014

- bugfix release, no API additions or changes
- fix resolving substitutions in include statements nested inside
  objects
- when rendering an object to a string, sort the fields
- handle unresolved substitutions in value concatenations
- make ConfigOrigin.comments unmodifiable
- when using '+=' or 'include' inside a list, throw an exception
  instead of generating a wrong result
- when context class loader is unset throw a more helpful
  exception than NullPointerException
- ignore non-string values in a Properties object

# 1.2.0: January 15, 2014

 - new stable ABI release (binary compatible with 1.0.x; a few new APIs)
 - new API ConfigResolveOptions.setAllowUnresolved lets you
   partially-resolve a Config
 - new API Config.isResolved lets you check on resolution status
 - new API Config.resolveWith lets you source substitutions from
   somewhere other than the Config itself
 - new API Config.getDuration() replaces getMilliseconds and
   getNanoseconds
 - if -Dconfig.file, -Dconfig.resource, -Dconfig.url refer to
   a nonexistent file, resource, or url it is now an error rather
   than silently loading an empty configuration.
 - quite a few bugfixes

# 1.1.0-4dd6c85cab1ef1a4415abb74704d60e57497b7b8: January 8, 2014

 - remove junk in POM caused by broken local configuration
 - build jar using Java 1.6 (and enforce this in build)
 - change getDuration to return unboxed long instead of boxed
 - API documentation improvements
   http://typesafehub.github.io/config/latest/api/

# 1.1.0-9f31d6308e7ebbc3d7904b64ebb9f61f7e22a968: January 6, 2014

 - this is a snapshot/preview with API/ABI additions. *New* API
   since 1.0.x is NOT guaranteed to remain compatible for now
   since the purpose of this release is to test it.
   This release is supposed to be ABI-compatible with 1.0.x
   however.
 - snapshots now use the git hash they are based on in the
   version, instead of SNAPSHOT, so they are a stable reference
   if you want to test them out.
 - if -Dconfig.file, -Dconfig.resource, -Dconfig.url refer to
   a nonexistent file, resource, or url it is now an error rather
   than silently loading an empty configuration.
 - new API Config.getDuration() replaces getMilliseconds and
   getNanoseconds. (should it return `long` instead of `Long` even
   though it's been in git for a while? weigh in at
   https://github.com/typesafehub/config/issues/119 )
 - new API ConfigResolveOptions.setAllowUnresolved lets you
   partially-resolve a Config
 - new API Config.isResolved lets you check on resolution status
 - new API Config.resolveWith lets you source substitutions from
   somewhere other than the Config itself
 - compiled with debug symbols
 - add -Dconfig.trace=loads feature to trace loaded files and
   failures
 - improvements to ConfigObject render() formatting so you can
   print out a config in a prettier way
 - attempt to honor Content-Type when loading from a URL
 - a fair list of corner case bugfixes

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
