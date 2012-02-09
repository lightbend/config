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
    - substitute environment variables

This library limits itself to config files. If you want to load
config from a database or something, you would need to write some
custom code. The library has nice support for merging
configurations so if you build one from a custom source it's easy
to merge it in.

## License

The license is Apache 2.0, see LICENSE-2.0.txt.

## Binary Releases

You can find published releases here:

 - http://repo.typesafe.com/typesafe/releases/com/typesafe/config/config/

Alternately, unofficial binary releases are also in [maven central](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22typesafe-config%22) as:

    <dependency>
        <groupId>org.skife.com.typesafe.config</groupId>
        <artifactId>typesafe-config</artifactId>
        <version>0.2.1</version>
    </dependency>


## API docs

 - Online: http://typesafehub.github.com/config/latest/api/
 - also published in jar form at http://repo.typesafe.com/typesafe/releases/com/typesafe/config/config/
 - consider reading this README first for an intro
 - for questions about the `.conf` file format, read HOCON.md in
   this directory

## Bugs and Patches

Report bugs to the GitHub issue tracker. Send patches as pull
requests on GitHub.

Along with any pull requests (or other means of contributing),
please state that the contribution is your original work (or that
you have the authority to license it) and that you license the
work under the Apache 2.0 license.

Whether or not you state this explicitly, by submitting any
copyrighted material via pull request, email, or other means you
agree to license your the material under the Apache 2.0 license
and warrant that you have the legal authority to do so.

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
simple-app` and then `run`.

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

Libraries and frameworks should default to `ConfigFactory.load()`
if the application does not provide a custom `Config`
object. Libraries and frameworks should also allow the application
to provide a custom `Config` object to be used instead of the
default, in case the application needs multiple configurations in
one JVM or wants to load extra config files from somewhere.

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

## JSON Superset

Tentatively called "Human-Optimized Config Object Notation" or
HOCON, also called `.conf`, see HOCON.md in this directory for more
detail.

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

## Future Directions

Here are some features that might be nice to add.

 - "myapp.d directory": allow parsing a directory. All `.json`,
   `.properties` and `.conf` files should be loaded in a
   deterministic order based on their filename.
   If you include a file and it turns out to be a directory then
   it would be processed in this way.
 - some way to merge array types. One approach could be:
   `searchPath=${searchPath} ["/usr/local/foo"]`, which involves
   two features: 1) substitutions referring to the key being
   assigned would have to look at that key's value later in the
   merge stack (rather than complaining about circularity); 2)
   arrays would have to be merged if a series of them appear after
   a key, similar to how strings are concatenated already. A
   simpler but much more limited approach would add `+=` as an
   alternative to `:`/`=`, where `+=` would append an array value
   to the array's previous value.  (Note that regular `=` already
   merges object values, to avoid object merge you have to first
   set the object to a non-object such as null, then set a new
   object. For consistency, if there's "array concatenation"
   within one value, maybe objects should also be able to merge
   within one value.)

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

