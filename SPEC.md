# HOCON (Human-Optimized Config Object Notation)

Very informal spec.

In this, "application" really could mean "library," it means "a thing
defining a configuration, such as Akka"

Some Java-specific stuff is in here, though a Java-independent version would
also be possible, without the system properties parts.

## Goals

The primary goal is: keep the semantics (tree structure; set of types;
encoding/escaping) from JSON, but make it more convenient as a
human-editable config file format.

The following features are desirable, to support human usage:

 - less noisy / less pedantic syntax
 - ability to refer to another part of the configuration (set a value to
   another value)
 - import/include another configuration file into the current file
 - a mapping to a flat properties hierarchy such as Java's System.properties
 - ability to get values from environment variables
 - ability to write comments

The first implementation should have these properties:

 - pure Java with no external dependencies
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

## Syntax

### Basic syntax

This describes a delta between HOCON and JSON.

The same as JSON:

 - files must be valid UTF-8
 - quoted strings are in the same format as JSON strings
 - values have possible types: string, number, object, array, boolean, null
   (more types can be introduced by an API but the syntax just has those)
 - allowed number formats matches JSON

Different from JSON:

 - anything between "//" or "#" and the next newline is considered a comment
   and ignored (unless the "//" or "#" is inside a quoted string)
 - a _key_ is a string JSON would have to the left of `:` and a _value_ is
   anything JSON would have to the right of `:`
 - a stream may begin with a key, rather than the opening brace of a root
   object. In this case a root object is implied.
 - `=` is used in place of `:`
 - the comma after a value may be omitted as long as there is a newline
   instead
 - keys with an object as their value may omit `=`, so `foo { }` means
   `foo = { }`
 - keys which contain no whitespace need not be quoted; the string
   is then used literally with no unescaping
 - if a key is not quoted, the `.` character has a special meaning and
   creates a new object. So `foo.bar = 10` means to create an object at key
   `foo`, then inside that object, create a key `bar` with value `10`
 - quoted keys _should not_ contain the `.` character because it's
   confusing, but it is permitted (to preserve the ability to use any string
   as a key and thus convert an arbitrary map or JavaScript object into HOCON)
 - if a key is defined twice, it is not an error; the later definition
   wins for all value types except object. For objects, the two objects
   are merged with the later object's keys winning.
    - because later objects are merged, you can do `foo.bar=10,foo.baz=12` and
      get `foo { bar=10,baz=12 }` rather than overwriting `bar` with `baz`
    - also this means you can include a file that defines all keys and then
      a later file can override only some of those keys
    - to replace an object entirely you can first set it to `null` or other
      non-object value, and then set it to an object again
 - arrays may be merged by using `++=` or `+=` rather than `=` to separate key
   from value.
    - `++=` must have an array value on the right, and will concatenate it
      with any previous array value (a previously-undefined value
      is treated as `[]`)
    - `+=` can have any value on the right and will append it to a
      previously-defined array or if none was defined, make it
      an array of one element
    - FIXME prepend operator?
 - a new type of value exists, substitution, which looks like `${some.path}`
   (details below)
 - to support substitutions, a value may consist of multiple strings which
   are concatenated into one string. `"foo"${some.path}"bar"`
 - String values may sometimes omit quotes. If a value does not parse as a
   substitution, quoted string, number, object, array, true, false, or null,
   then that value will be parsed as a string value, created as
   follows:
    - take the string from the `=` to the first newline or comma
    - remove leading and trailing whitespace (whitespace defined
      only as ASCII whitespace, as with Java's trim() method)
    - what remains is treated as a sequence of strings, where
      each string is either the raw inline UTF-8 data, a quoted
      string, or a substitution
    - everything up to a `"` or `$` is a raw unquoted UTF-8 string;
      no unescaping is performed
    - at `"` a quoted string is parsed, with the usual escape
      sequences; after the close `"` parsing the unquoted string
      continues. The quoted string must be well-formed or it's
      an error.
    - at `$` a substitution is parsed. The substitution must be well-formed
      or it's an error.
    - to get a literal `"`, `$`, newline or comma, you would have to use
      a quoted string
    - after the initial raw string, quoted string, or substitution,
      parsing another one immediately begins and so on until the
      end of the value.
    - the resulting sequence of strings is concatenated
 - the special key `include` followed directly by a string value (with no
   `=`) means to treat that string value as a filename and merge the
   object defined in that file into the current object, overriding
   any keys defined earlier and being overridden by any keys defined later.
   The search path for the file (which may include the classpath or certain
   directories in the filesystem) will be application-defined.
 - An included filename can refer to any of a HOCON file or a JSON file or a
   Java properties file. These are distinguished by extension:
    - `.properties` for Java properties
    - `.json` for JSON
    - `.conf` or `.hocon` for HOCON
   If the included filename has no extension, then any of the above
   extensions are allowed. If the included filename has an extension
   already then it refers to precisely that filename and the format
   is not flexible.

### Java properties mapping

See the Java properties spec here: http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29

There is a mapping from Java properties to HOCON,
`foo.bar.baz=10` to `foo={ bar={ baz=10 } }`. If an HOCON key has a `.` in it
(possible by quoting the HOCON key) then there is no way to refer to it as a
Java property; it is not recommended to name HOCON keys with a `.` in them.

For an application's config, Java System properties _override_ HOCON found
in the configuration file. This supports specifying config options on the
command line.

When loading a configuration, all System properties should be merged in.

Generally an application's configuration should be under a root namespace,
to avoid merging every system property in the whole process.

For example, say your config is for "akka" then your config key "foo" would
go with `-Dakka.foo=10`. When loading your config, any system properties
starting with `akka.` would be merged into the config.

System properties always have string values, but they are parsed as a
simplified HOCON value when merged:

 - valid boolean, null, and number literals become those types
 - anything else is a string
 - no substitution, unescaping, unquoting is performed

### Substitutions

Substitutions are a way of referring to other parts of the configuration
tree.

Substitution processing is performed as the last parsing step, so a
substitution can look forward in the configuration file and even retrieve a
value from a Java System property. This also means that a substitution will
evaluate to the last-assigned value for a given option (since identical keys
later in the stream override those earlier in the stream).

Circular substitutions are an error, implementations might try to detect
them in a nicer way than stack overflow, for bonus points.

Substitutions are allowed in values, but not in keys. Substitutions are not
evaluated inside quoted strings, they must be "toplevel" values.

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
 - Parsed as a simplified value token as with system properties:
    - valid boolean, null, and number literals become those types
    - anything else is a string
    - no substitution, unescaping, unquoting is performed

## Examples

To get this JSON:

    {
        "foo" : {
            "bar" : 10,
            "baz" : 12
        }
    }

You could write any of:

    foo.bar=10
    foo.baz=12

or

    foo {
      bar=10
      baz=12
    }

or

    foo {
      bar=10, baz=12
    }

or

    foo {
      "bar"=10
      "baz"=12
    }

or

    foo {
      bar=10
    }
    foo {
      bar=12
    }
