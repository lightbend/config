Configuration library for JVM languages.

## Overview

 - implemented in plain Java with no dependencies
 - extensive test coverage
 - supports files in three formats: Java properties, JSON, and a
   human-friendly JSON superset
 - merges multiple files across all formats
 - can load from files, URLs, or classpath
 - good support for "nesting" (treat any subtree of the config the
   same as the whole config)
 - users can override the config with Java system properties,
    `java -Dmyapp.foo.bar=10`
 - supports configuring an app, with its framework and libraries,
   all from a single file such as `application.conf`
 - parses duration and size settings, "512k" or "10 seconds"
 - converts types, so if you ask for a boolean and the value
   is the string "yes", or you ask for a float and the value is
   an int, it will figure it out.
 - JSON superset features:
    - comments
    - includes
    - substitutions (`"foo" : ${bar}`, `"foo" : Hello ${who}`)
    - properties-like notation (`a.b=c`)
    - less noisy, more lenient syntax
    - substitute environment variables (`logdir=${HOME}/logs`)

This library limits itself to config files. If you want to load
config from a database or something, you would need to write some
custom code. The library has nice support for merging
configurations so if you build one from a custom source it's easy
to merge it in.

## License

The license is Apache 2.0, see LICENSE-2.0.txt.

## Binary Releases

You can find published releases (compiled for Java 6 and above) on
Maven Central.

    <dependency>
        <groupId>com.typesafe</groupId>
        <artifactId>config</artifactId>
        <version>1.2.0</version>
    </dependency>

Obsolete releases are here, but you probably don't want these:

 - http://repo.typesafe.com/typesafe/releases/com/typesafe/config/config/

## Release Notes

Please see NEWS.md in this directory,
https://github.com/typesafehub/config/blob/master/NEWS.md

## API docs

 - Online: http://typesafehub.github.com/config/latest/api/
 - also published in jar form
 - consider reading this README first for an intro
 - for questions about the `.conf` file format, read HOCON.md in
   this directory

## Bugs and Patches

Report bugs to the GitHub issue tracker. Send patches as pull
requests on GitHub.

Before we can accept pull requests, you will need to agree to the
Typesafe Contributor License Agreement online, using your GitHub
account - it takes 30 seconds.  You can do this at
http://www.typesafe.com/contribute/cla

## Build

The build uses sbt and the tests are written in Scala; however,
the library itself is plain Java and the published jar has no
Scala dependency.

## API Example

    Config conf = ConfigFactory.load();
    int bar1 = conf.getInt("foo.bar");
    Config foo = conf.getConfig("foo");
    int bar2 = foo.getInt("bar");

## Longer Examples

See the examples in the `examples/` directory.

You can run these from the sbt console with the commands `project
config-simple-app-java` and then `run`.

In brief, as shown in the examples:

 - libraries should use a `Config` instance provided by the app,
   if any, and use `ConfigFactory.load()` if no special `Config`
   is provided. Libraries should put their defaults in a
   `reference.conf` on the classpath.
 - apps can create a `Config` however they want
   (`ConfigFactory.load()` is easiest and least-surprising), then
   provide it to their libraries. A `Config` can be created with
   the parser methods in `ConfigFactory` or built up from any file
   format or data source you like with the methods in
   `ConfigValueFactory`.

## Schemas and Validation

There isn't a schema language or anything like that. However, two
suggested tools are:

 - use the
   [checkValid() method](http://typesafehub.github.com/config/latest/api/com/typesafe/config/Config.html#checkValid%28com.typesafe.config.Config,%20java.lang.String...%29)
 - access your config through a Settings class with a field for
   each setting, and instantiate it on startup (immediately
   throwing an exception if any settings are missing)

In Scala, a Settings class might look like:

    class Settings(config: Config) {

        // validate vs. reference.conf
        config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

        // non-lazy fields, we want all exceptions at construct time
        val foo = config.getString("simple-lib.foo")
        val bar = config.getInt("simple-lib.bar")
    }

See the examples/ directory for a full compilable program.

## Standard behavior

The convenience method `ConfigFactory.load()` loads the following
(first-listed are higher priority):

  - system properties
  - `application.conf` (all resources on classpath with this name)
  - `application.json` (all resources on classpath with this name)
  - `application.properties` (all resources on classpath with this
    name)
  - `reference.conf` (all resources on classpath with this name)

The idea is that libraries and frameworks should ship with a
`reference.conf` in their jar. Applications should provide an
`application.conf`, or if they want to create multiple
configurations in a single JVM, they could use
`ConfigFactory.load("myapp")` to load their own `myapp.conf`.
(Applications _can_ provide a `reference.conf` also if they want,
but you may not find it necessary to separate it from
`application.conf`.)

Libraries and frameworks should default to `ConfigFactory.load()`
if the application does not provide a custom `Config` object. This
way, libraries will see configuration from `application.conf` and
users can configure the whole app, with its libraries, in a single
`application.conf` file.

Libraries and frameworks should also allow the application to
provide a custom `Config` object to be used instead of the
default, in case the application needs multiple configurations in
one JVM or wants to load extra config files from somewhere.  The
library examples in `examples/` show how to accept a custom config
while defaulting to `ConfigFactory.load()`.

For applications using `application.{conf,json,properties}`,
system properties can be used to force a different config source:

 - `config.resource` specifies a resource name - not a
   basename, i.e. `application.conf` not `application`
 - `config.file` specifies a filesystem path, again
   it should include the extension, not be a basename
 - `config.url` specifies a URL

These system properties specify a _replacement_ for
`application.{conf,json,properties}`, not an addition. They only
affect apps using the default `ConfigFactory.load()`
configuration. In the replacement config file, you can use
`include "application"` to include the original default config
file; after the include statement you could go on to override
certain settings.

## Merging config trees

Any two Config objects can be merged with an associative operation
called `withFallback`, like `merged = firstConfig.withFallback(secondConfig)`.

The `withFallback` operation is used inside the library to merge
duplicate keys in the same file and to merge multiple files.
`ConfigFactory.load()` uses it to stack system properties over
`application.conf` over `reference.conf`.

You can also use `withFallback` to merge in some hardcoded values,
or to "lift" a subtree up to the root of the configuration; say
you have something like:

    foo=42
    dev.foo=57
    prod.foo=10

Then you could code something like:

    Config devConfig = originalConfig
                         .getConfig("dev")
                         .withFallback(originalConfig)

There are lots of ways to use `withFallback`.

## How to handle defaults

Many other configuration APIs allow you to provide a default to
the getter methods, like this:

    boolean getBoolean(String path, boolean fallback)

Here, if the path has no setting, the fallback would be
returned. An API could also return `null` for unset values, so you
would check for `null`:

    // returns null on unset, check for null and fall back
    Boolean getBoolean(String path)

The methods on the `Config` interface do NOT do this, for two
major reasons:

 1. If you use a config setting in two places, the default
 fallback value gets cut-and-pasted and typically out of
 sync. This can result in Very Evil Bugs.
 2. If the getter returns `null` (or `None`, in Scala) then every
 time you get a setting you have to write handling code for
 `null`/`None` and that code will almost always just throw an
 exception. Perhaps more commonly, people forget to check for
 `null` at all, so missing settings result in
 `NullPointerException`.

For most apps, failure to have a setting is simply a bug to fix
(in either code or the deployment environment). Therefore, if a
setting is unset, by default the getters on the `Config` interface
throw an exception.

If you *want* to allow a setting to be missing from
`application.conf` then here are some options:

 1. Set it in a `reference.conf` included in your library or
 application jar, so there's a default value.
 2. Catch and handle `ConfigException.Missing`.
 3. Use the `Config.hasPath()` method to check in advance whether
 the path exists (rather than checking for `null`/`None` after as
 you might in other APIs).
 4. In your initialization code, generate a `Config` with your
 defaults in it (using something like `ConfigFactory.parseMap()`)
 then fold that default config into your loaded config using
 `withFallback()`, and use the combined config in your
 program. "Inlining" your reference config in the code like this
 is probably less convenient than using a `reference.conf` file,
 but there may be reasons to do it.
 5. Use `Config.root()` to get the `ConfigObject` for the
 `Config`; `ConfigObject` implements `java.util.Map<String,?>` and
 the `get()` method on `Map` returns null for missing keys. See
 the API docs for more detail on `Config` vs. `ConfigObject`.

The *recommended* path (for most cases, in most apps) is that you
require all settings to be present in either `reference.conf` or
`application.conf` and allow `ConfigException.Missing` to be
thrown if they are not. That's the design intent of the `Config`
API design.

If you do need a setting to be optional, checking `hasPath()` in
advance should be the same amount of code (in Java) as checking
for `null` afterward, without the risk of `NullPointerException`
when you forget. In Scala, you could write an enrichment class
like this to use the idiomatic `Option` syntax:

```scala
implicit class RichConfig(val underlying: Config) extends AnyVal {
  def getOptionalBoolean(path: String): Option[Boolean] = try {
     Some(underlying.getBoolean(path))
  } catch {
     case e: ConfigException.Missing =>
         None
  }
}
```

Since this library is a Java library it doesn't come with that out
of the box, of course.

Whatever you do, please remember not to cut-and-paste default
values into multiple places in your code. You have been warned!
:-)

## JSON Superset

Tentatively called "Human-Optimized Config Object Notation" or
HOCON, also called `.conf`, see HOCON.md in this directory for more
detail.

After processing a `.conf` file, the result is always just a JSON
tree that you could have written (less conveniently) in JSON.

### Features of HOCON

  - Comments, with `#` or `//`
  - Allow omitting the `{}` around a root object
  - Allow `=` as a synonym for `:`
  - Allow omitting the `=` or `:` before a `{` so
    `foo { a : 42 }`
  - Allow omitting commas as long as there's a newline
  - Allow trailing commas after last element in objects and arrays
  - Allow unquoted strings for keys and values
  - Unquoted keys can use dot-notation for nested objects,
    `foo.bar=42` means `foo { bar : 42 }`
  - Duplicate keys are allowed; later values override earlier,
    except for object-valued keys where the two objects are merged
    recursively
  - `include` feature merges root object in another file into
    current object, so `foo { include "bar.json" }` merges keys in
    `bar.json` into the object `foo`
  - include with no file extension includes any of `.conf`,
    `.json`, `.properties`
  - you can include files, URLs, or classpath resources; use
    `include url("http://example.com")` or `file()` or
    `classpath()` syntax to force the type, or use just `include
    "whatever"` to have the library do what you probably mean
    (Note: `url()`/`file()`/`classpath()` syntax is not supported
    in Play/Akka 2.0.)
  - substitutions `foo : ${a.b}` sets key `foo` to the same value
    as the `b` field in the `a` object
  - substitutions concatenate into unquoted strings, `foo : the
    quick ${colors.fox} jumped`
  - substitutions fall back to environment variables if they don't
    resolve in the config itself, so `${HOME}` would work as you
    expect. Also, most configs have system properties merged in so
    you could use `${user.home}`.
  - substitutions normally cause an error if unresolved, but
    there is a syntax `${?a.b}` to permit them to be missing.
  - `+=` syntax to append elements to arrays, `path += "/bin"`
  - multi-line strings with triple quotes as in Python or Scala

### Examples of HOCON

All of these are valid HOCON.

Start with valid JSON:

    {
        "foo" : {
            "bar" : 10,
            "baz" : 12
        }
    }

Drop root braces:

    "foo" : {
        "bar" : 10,
        "baz" : 12
    }

Drop quotes:

    foo : {
        bar : 10,
        baz : 12
    }

Use `=` and omit it before `{`:

    foo {
        bar = 10,
        baz = 12
    }

Remove commas:

    foo {
        bar = 10
        baz = 12
    }

Use dotted notation for unquoted keys:

    foo.bar=10
    foo.baz=12

Put the dotted-notation fields on a single line:

    foo.bar=10, foo.baz=12

The syntax is well-defined (including handling of whitespace and
escaping). But it handles many reasonable ways you might want to
format the file.

Note that while you can write HOCON that looks a lot like a Java
properties file (and many properties files will parse as HOCON),
the details of escaping, whitespace handling, comments, and so
forth are more like JSON. The spec (see HOCON.md in this
directory) has some more detailed notes on this topic.

### Uses of Substitutions

The `${foo.bar}` substitution feature lets you avoid cut-and-paste
in some nice ways.

#### Factor out common values

This is the obvious use,

    standard-timeout = 10ms
    foo.timeout = ${standard-timeout}
    bar.timeout = ${standard-timeout}

#### Inheritance

If you duplicate a field with an object value, then the objects
are merged with last-one-wins. So:

    foo = { a : 42, c : 5 }
    foo = { b : 43, c : 6 }

means the same as:

    foo = { a : 42, b : 43, c : 6 }

You can take advantage of this for "inheritance":

    data-center-generic = { cluster-size = 6 }
    data-center-east = ${data-center-generic}
    data-center-east = { name = "east" }
    data-center-west = ${data-center-generic}
    data-center-west = { name = "west", cluster-size = 8 }

Using `include` statements you could split this across multiple
files, too.

#### Optional system or env variable overrides

In default uses of the library, exact-match system properties
already override the corresponding config properties.  However,
you can add your own overrides, or allow environment variables to
override, using the `${?foo}` substitution syntax.

    basedir = "/whatever/whatever"
    basedir = ${?FORCED_BASEDIR}

Here, the override field `basedir = ${?FORCED_BASEDIR}` simply
vanishes if there's no value for `FORCED_BASEDIR`, but if you set
an environment variable `FORCED_BASEDIR` for example, it would be
used.

A natural extension of this idea is to support several different
environment variable names or system property names, if you aren't
sure which one will exist in the target environment.

Object fields and array elements with a `${?foo}` substitution
value just disappear if the substitution is not found:

    // this array could have one or two elements
    path = [ "a", ${?OPTIONAL_A} ]

### Concatenation

Values _on the same line_ are concatenated (for strings and
arrays) or merged (for objects).

This is why unquoted strings work, here the number `42` and the
string `foo` are concatenated into a string `42 foo`:

    key : 42 foo

When concatenating values into a string, leading and trailing
whitespace is stripped but whitespace between values is kept.

Unquoted strings also support substitutions of course:

    tasks-url : ${base-url}/tasks

A concatenation can refer to earlier values of the same field:

    path : "/bin"
    path : ${path}":/usr/bin"

Arrays can be concatenated as well:

    path : [ "/bin" ]
    path : ${path} [ "/usr/bin" ]

There is a shorthand for appending to arrays:

    // equivalent to: path = ${?path} [ "/usr/bin" ]
    path += "/usr/bin"

To prepend or insert into an array, there is no shorthand.

When objects are "concatenated," they are merged, so object
concatenation is just a shorthand for defining the same object
twice. The long way (mentioned earlier) is:

    data-center-generic = { cluster-size = 6 }
    data-center-east = ${data-center-generic}
    data-center-east = { name = "east" }

The concatenation-style shortcut is:

    data-center-generic = { cluster-size = 6 }
    data-center-east = ${data-center-generic} { name = "east" }

When concatenating objects and arrays, newlines are allowed
_inside_ each object or array, but not between them.

Non-newline whitespace is never a field or element separator. So
`[ 1 2 3 4 ]` is an array with one unquoted string element
`"1 2 3 4"`. To get an array of four numbers you need either commas or
newlines separating the numbers.

See the spec for full details on concatenation.

Note: Play/Akka 2.0 have an earlier version that supports string
concatenation, but not object/array concatenation. `+=` does not
work in Play/Akka 2.0 either.

## Debugging

If you have trouble with your configuration, some useful tips.

 - Set the Java system property `-Dconfig.trace=loads` to get
   output on stderr describing each file that is loaded.
   Note: this feature is not included in the older version in
   Play/Akka 2.0.
 - Use `myConfig.root().render()` to get a `Config` printed out as a
   string with comments showing where each value came from.

## Java version

Currently the library is maintained against Java 6. It does not
build with Java 5.

## Rationale

(For the curious.)

The three file formats each have advantages.

 - Java `.properties`:
   - Java standard, built in to JVM
   - Supported by many tools such as IDEs
 - JSON:
   - easy to generate programmatically
   - well-defined and standard
   - bad for human maintenance, with no way to write comments,
     and no mechanisms to avoid duplication of similar config
     sections
 - HOCON/`.conf`:
   - nice for humans to read, type, and maintain, with more
     lenient syntax
   - built-in tools to avoid cut-and-paste
   - ways to refer to the system environment, such as system
     properties and environment variables

The idea would be to use JSON if you're writing a script to spit
out config, and use HOCON if you're maintaining config by hand.
If you're doing both, then mix the two.

Two alternatives to HOCON syntax could be:

  - YAML is also a JSON superset and has a mechanism for adding
    custom types, so the include statements in HOCON could become
    a custom type tag like `!include`, and substitutions in HOCON
    could become a custom tag such as `!subst`, for example. The
    result is somewhat clunky to write, but would have the same
    in-memory representation as the HOCON approach.
  - Put a syntax inside JSON strings, so you might write something
    like `"$include" : "filename"` or allow `"foo" : "${bar}"`.
    This is a way to tunnel new syntax through a JSON parser, but
    other than the implementation benefit (using a standard JSON
    parser), it doesn't really work. It's a bad syntax for human
    maintenance, and it's not valid JSON anymore because properly
    interpreting it requires treating some valid JSON strings as
    something other than plain strings. A better approach is to
    allow mixing true JSON files into the config but also support
    a nicer format.

## Other APIs

This may not be comprehensive - if you'd like to add mention of
your wrapper, just send a pull request for this README. We would
love to know what you're doing with this library or with the HOCON
format.

 * Scala wrappers for the Java library
   * Ficus https://github.com/ceedubs/ficus
   * configz https://github.com/arosien/configz
   * configs https://github.com/kxbmap/configs
