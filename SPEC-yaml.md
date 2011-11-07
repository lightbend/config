# HOCON (Human-Optimized Config Object Notation)

Very informal spec for a HOCON based on YAML.

In this, "application" really could mean "library," it means "a thing
defining a configuration, such as Akka"

Some Java-specific stuff is in here, though a Java-independent version would
also be possible, without the system properties parts.

## Goals

The primary goal is: keep the semantics (tree structure; set of types;
encoding/escaping) from JSON, but make it more convenient as a
human-editable config file format.

The following features are provided by YAML:

 - less noisy / less pedantic syntax
 - ability to write comments

The following features are defined on top of YAML:

 - ability to refer to another part of the configuration (set a value to
   another value)
 - import/include another configuration file into the current file
 - a mapping to a flat properties hierarchy such as Java's System.properties
 - ability to get values from environment variables

The first implementation should have these properties:

 - pure Java with no external dependencies other than YAML parser
 - API easily supports "nesting" (get a subtree then treat it as a root)
 - API supports a "root" name which is used to scope Java properties.
   So if you say your config is for "akka" then your config key "foo"
   would go with `-Dakka.foo=10`, but in the config file people don't
   have to write the root `akka` object.
 - application can define the search path for include statements
   (with a function from string to input stream)
 - API supports treating values as "Durations" and "Memory sizes" in
   addition to basic JSON types (for example "50ms" or "128M")
 - API should attempt to perform reasonable type conversions.
   This might include: treating numbers 0 or 1 as booleans, treating
   strings yes/no/y/n as booleans, integer to and from float,
   any number or boolean to a string. This puts "figure out
   what people mean" in the API rather than in the syntax spec.
   (Note: this is pretty much how YAML works anyway; the types are
   not always apparent from the syntax as they are in JSON.)

## YAML background information

The Wikipedia article about YAML is quite good as a brief introduction:
http://en.wikipedia.org/wiki/YAML

YAML 1.2 is a minor revision of 1.1 which makes JSON a subset of YAML. With
1.1, "most" JSON is valid YAML, while with 1.2 all JSON is valid YAML.

The official YAML site is:
http://yaml.org/

## Java YAML parsers

There is a good discussion at:
http://stackoverflow.com/questions/450399/which-java-yaml-library-should-i-use

There appear to be only two options:

 - SnakeYAML http://code.google.com/p/snakeyaml/
 - YamlBeans http://code.google.com/p/yamlbeans/

The SnakeYAML maintainer expressed some interest in a Scala wrapper:
http://www.scala-lang.org/node/5497
There's also an apparently-unmaintained wrapper HelicalYAML:
http://code.google.com/p/helicalyaml/

According to the .pom, all SnakeYAML deps are in test scope, so no
dependencies at runtime:
http://repo2.maven.org/maven2/org/yaml/snakeyaml/1.9/snakeyaml-1.9.pom

Both SnakeYAML and YamlBeans are YAML 1.1, so some JSON may not parse.

SnakeYAML appears to be more popular and more flexible, while YamlBeans is
the simplest API that could possibly work to serialize and deserialize
beans.

### Java properties mapping

See the Java properties spec here: http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29

There is a mapping from Java properties to HOCON, `foo.bar.baz=10` to `foo={
bar={ baz=10 } }`. If an HOCON key has a `.` in it then there is no way to
refer to it as a Java property; it is not recommended to name HOCON keys
with a `.` in them.

For an application's config, Java System properties _override_ HOCON found
in the configuration file. This supports specifying config options on the
command line.

When loading a configuration, all System properties should be merged in.

Generally an application's configuration should be under a root namespace,
to avoid merging every system property in the whole process.

For example, say your config is for "akka" then your config key "foo" would
go with `-Dakka.foo=10`. When loading your config, any system properties
starting with `akka.` would be merged into the config.

System properties always have string values. No substitution or unescaping
is performed on the value.

## New YAML tags

To achieve HOCON on top of YAML, we assume the JSON schema
(http://www.yaml.org/spec/1.2/spec.html#id2803231) and then add a couple
more tags for includes and substitutions.

### Tags

In YAML, standardized data types are tagged with the `!!` prefix:

    d: !!float 123

The `!!float` is an explicit type tag; without it `123` would be an integer
instead.

Applications can define their own types with a single exclamation point
prefix:

    d: !mytype 123

### Substitution

The substitution type is tagged with `!subst` and will have expressions like
`${some.path}` in the value.

Substitutions are a way of referring to other parts of the configuration
tree.

If the entire substitution value is a `${}` expression, the substitution is
interpreted as the type of the original configuration subtree being
substituted.

If there are any characters outside of a single `${}` the substitution is
interpreted as a string, with substrings of the form `${some.path}`
replaced. There is no way to escape literal `${` in a `!subst` value; you
could do it by having another value containing the `${` and substituting
that in, if you ever needed to.

Substitution processing is performed as the last parsing step, so a
substitution can look forward in the configuration file and even retrieve a
value from a Java System property. This also means that a substitution will
evaluate to the last-assigned value for a given option (since identical keys
later in the stream override those earlier in the stream).

Circular substitutions are an error, implementations might try to detect
them in a nicer way than stack overflow, for bonus points.

A substitution is replaced with any value type (number, object, string,
array, true, false, null). If the substitution is the only part of a value,
then the type is preserved. If the substitution is part of a string (needs
to be concatenated) then it is an error if the substituted value is an
object or array. Otherwise the value is converted to a string value as follows:

 - `null` is converted to an empty string, not the string `null`
 - strings are already strings
 - numbers are converted to a string that would parse as a valid number in
   HOCON
 - booleans are converted to `true` or `false`

Substitutions are looked up in the root object, using the regular key
syntax, so `foo.bar` is split to be "key `bar` inside the object at key
`foo`", but `"foo.bar"` is not split because it's quoted.

Recall that the root object already has system properties merged in as
overrides. So looking up `foo.bar` in root object `akka` would get the
system property `akka.foo.bar` if that system property were present.

Substitutions are looked up in three namespaces, in order:

 - the application's normal root object.
 - System properties directly, without the root namespace.  So
   `${user.home}` would first look for a `user.home` in the root
   configuration (which has a scoped system property like `akka.user.home`
   merged in!) and if that failed, it would look at the system property
   `user.home` without the `akka.` prefix.
      - the intent is to allow using generic system properties like
        `user.home` and also to allow overriding those per-app.
      - the intent is NOT to allow accessing config from other apps,
        only to allow access to global system properties
 - system environment variables

If a substitution is not resolved, it evaluates to JSON value `null`, which
would then be converted to an empty string if the substitution is part of a
string.

If a substitution evaluates to `null` (which may mean it was either unset,
or explicitly set to `null`), then environment variables should be used as a
fallback.

Note that environment variables and global system properties are fallbacks,
while app-scoped system properties are an override.

It's recommended that HOCON keys always use lowercase, because environment
variables generally are capitalized. This avoids naming collisions between
environment variables and configuration properties. (While on Windows
getenv() is generally not case-sensitive, the lookup will be case sensitive
all the way until the env variable fallback lookup is reached.)

An application can explicitly block looking up a substitution in the
environment by setting a non-`null` value in the configuration, with the
same name as the environment variable. But there is no way to set a key to
`null` if a non-empty environment variable is present.

Environment variables are interpreted as follows:

 - present and set to empty string: treated as not present
 - System.getenv throws SecurityException: treated as not present
 - encoding is handled by Java (System.getenv already returns
   a Unicode string)
 - Always result in a string value.

### YAML references vs. substitutions

YAML has a built-in mechanism using node anchors and references to avoid
duplication, see http://en.wikipedia.org/wiki/YAML#References

Substitutions are different because they don't require premeditation, so you
can refer to system properties or environment variables or the default
config, even though those things may not have node anchors.

### Includes

 - The type tag `!include` means to interpret the value as a filename and merge the
   object defined in that file into the current object, overriding
   any keys defined earlier and being overridden by any keys defined later.
   The search path for the file (which may include the classpath or certain
   directories in the filesystem) will be application-defined.
 - An included filename can refer to any of a HOCON file or a
   Java properties file. These are distinguished by extension:
    - `.properties` for Java properties
    - `.yml` for HOCON
   If the included filename has no extension, then any of the above
   extensions are allowed. If the included filename has an extension
   already then it refers to precisely that filename and the format
   is not flexible.

## Duplicate Keys

YAML does not allow duplicate keys, so specifying the same key twice is an
error; it is not allowed to "override" a previous key.

However, the `!include` mechanism allows overriding keys because the
included file may specify keys that appear in the parent document. The
included keys will override those earlier in the parent document, and be
overridden by those later in the parent document.

FIXME: need to see if SnakeYAML actually allows this (is it possible to get
keys in the order they appeared, or is it only possible to get a Map).

## Examples

To get this JSON:

    {
        "foo" : {
            "bar" : 10,
            "baz" : 12
        }
    }

You could write the JSON itself, or condense it to:

    foo:
       bar: 10
       baz: 12

You can also mix the syntaxes, like:

    foo: { bar: 10, baz: 12 }

The colon can't have spaces before it unless you quote the key, so `foo:`
not `foo :`.

To do an include, you write:

    foo:
       bar: !include something.yml

To do a substitution, you write:

    foo:
       bar: !subst ${user.home}/.myconf

