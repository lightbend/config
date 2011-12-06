# HOCON (Human-Optimized Config Object Notation)

This is an informal spec, but hopefully it's clear.

## Goals / Background

The primary goal is: keep the semantics (tree structure; set of
types; encoding/escaping) from JSON, but make it more convenient
as a human-editable config file format.

The following features are desirable, to support human usage:

 - less noisy / less pedantic syntax
 - ability to refer to another part of the configuration (set a value to
   another value)
 - import/include another configuration file into the current file
 - a mapping to a flat properties list such as Java's system properties
 - ability to get values from environment variables
 - ability to write comments

Implementation-wise, the format should have these properties:

 - a JSON superset, that is, all valid JSON should be valid and
   should result in the same in-memory data that a JSON parser
   would have produced.
 - be deterministic; the format is flexible, but it is not
   heuristic. It should be clear what's invalid and invalid files
   should generate errors.
 - require minimal look-ahead; should be able to tokenize the file
   by looking at only the next three characters. (right now, the
   only reason to look at three is to find "//" comments;
   otherwise you can parse looking at two.)

HOCON is significantly harder to specify and to parse than
JSON. Think of it as moving the work from the person maintaining
the config file to the computer program.

## Definitions

 - a _key_ is a string JSON would have to the left of `:` and a _value_ is
   anything JSON would have to the right of `:`. i.e. the two
   halves of an object _field_.

 - a _value_ is any "value" as defined in the JSON spec, plus
   unquoted strings and substitutions as defined in this spec.

 - a _simple value_ is any value excluding an object or array
   value.

 - a _field_ is a key, any separator such as ':', and a value.

 - references to a _file_ ("the file being parsed") can be
   understood to mean any byte stream being parsed, not just
   literal files in a filesystem.

## Syntax

Much of this is defined with reference to JSON; you can find the
JSON spec at http://json.org/ of course.

### Unchanged from JSON

 - files must be valid UTF-8
 - quoted strings are in the same format as JSON strings
 - values have possible types: string, number, object, array, boolean, null
 - allowed number formats matches JSON; as in JSON, some possible
   floating-point values are not represented, such as `NaN`

### Comments

Anything between `//` or `#` and the next newline is considered a comment
and ignored, unless the `//` or `#` is inside a quoted string.

### Omit root braces

JSON documents must have an array or object at the root. Empty
files are invalid documents, as are files containing only a
non-array non-object value such as a string.

In HOCON, if the file does not begin with a square bracket or
curly brace, it is parsed as if it were enclosed with `{}` curly
braces.

A HOCON file is invalid if it omits the opening `{` but still has
a closing `}`; the curly braces must be balanced.

### Key-value separator

The `=` character can be used anywhere JSON allows `:`, i.e. to
separate keys from values.

If a key is followed by `{`, the `:` or `=` may be omitted. So
`"foo" {}` means `"foo" : {}"`

### Commas

Values in arrays, and fields in objects, need not have a comma
between them as long as they have at least one ASCII newline
(`\n`, decimal value 10) between them.

The last element in an array or last field in an object may be
followed by a single comma. This extra comma is ignored.

 - `[1,2,3,]` and `[1,2,3]` are the same array.
 - `[1\n2\n3]` and `[1,2,3]` are the same array.
 - `[1,2,3,,]` is invalid because it has two trailing commas.
 - `[,1,2,3]` is invalid because it has an initial comma.
 - `[1,,2,3]` is invalid because it has two commas in a row.
 - these same comma rules apply to fields in objects.

### Whitespace

The JSON spec simply says "whitespace"; in HOCON whitespace is
defined as follows:

 - any Unicode space separator (Zs category), line separator (Zl
   category), or paragraph separator (Zp category), including
   nonbreaking spaces (such as 0x00A0, 0x2007, and 0x202F).
 - tab (`\t` 0x0009), newline ('\n' 0x000A), vertical tab ('\v'
   0x000B)`, form feed (`\f' 0x000C), carriage return ('\r'
   0x000D), file separator (0x001C), group separator (0x001D),
   record separator (0x001E), unit separator (0x001F).

In Java, the `isWhitespace()` method covers these characters with
the exception of nonbreaking spaces.

While all Unicode separators should be treated as whitespace, in
this spec "newline" refers only and specifically to ASCII newline
0x000A.

### Duplicate keys

The JSON spec does not clarify how duplicate keys in the same
object should be handled. In HOCON, duplicate keys that appear
later override those that appear earlier, unless both values are
objects. If both values are objects, then the objects are merged.

Note: this would make HOCON a non-superset of JSON if you assume
that JSON requires duplicate keys to have a behavior. The
assumption here is that duplicate keys are invalid JSON.

To merge objects:

 - add fields present in only one of the two objects to the merged
   object.
 - for non-object-valued fields present in both objects,
   the field found in the second object must be used.
 - for object-valued fields present in both objects, the
   object values should be recursively merged according to
   these same rules.

Object merge can be prevented by setting the key to another value
first. This is because merging is always done two values at a
time; if you set a key to an object, a non-object, then an object,
first the non-object falls back to the object (non-object always
wins), and then the object falls back to the non-object (no
merging, object is the new value). So the two objects never see
each other.

These two are equivalent:

    {
        "foo" : { "a" : 42 },
        "foo" : { "b" : 43 }
    }

    {
        "foo" : { "a" : 42, "b" : 43 }
    }

And these two are equivalent:

    {
        "foo" : { "a" : 42 },
        "foo" : null,
        "foo" : { "b" : 43 }
    }

    {
        "foo" : { "b" : 43 }
    }

The intermediate setting of `"foo"` to `null` prevents the object merge.

### Unquoted strings

A sequence of characters outside of a quoted string is a string
value if:

 - it does not contain "forbidden characters" '$', '"', '{', '}',
   '[', ']', ':', '=', ',', '+', '#', '`', '^', '?', '!', '@',
   '*', '&', '\' (backslash), or whitespace.
 - it does not contain the two-character string "//" (which
   starts a comment)
 - its initial characters do not parse as `true`, `false`, `null`,
   or a number.

Unquoted strings are used literally, they do not support any kind
of escaping. Quoted strings may always be used as an alternative
when you need to write a character that is not permitted in an
unquoted string.

`truefoo` parses as the boolean token `true` followed by the
unquoted string `foo`. However, `footrue` parses as the unquoted
string `footrue`. Similarly, `10.0bar` is the number `10.0` then
the unquoted string `bar` but `bar10.0` is the unquoted string
`bar10.0`.

In general, once an unquoted string begins, it continues until a
forbidden character or the two-character string "//" is
encountered. Embedded (non-initial) booleans, nulls, and numbers
are not recognized as such, they are part of the string.

An unquoted string may not _begin_ with the digits 0-9 or with a
hyphen (`-`, 0x002D) because those are valid characters to begin a
JSON number. The initial number character, plus any valid-in-JSON
number characters that follow it, must be parsed as a number
value. Again, these characters are not special _inside_ an
unquoted string; they only trigger number parsing if they appear
initially.

Note that quoted JSON strings may not contain control characters
(control characters include some whitespace characters, such as
newline). This rule is from the JSON spec. However, unquoted
strings have no restriction on control characters, other than the
ones listed as "forbidden characters" above.

Some of the "forbidden characters" are forbidden because they
already have meaning in JSON or HOCON, others are essentially
reserved keywords to allow future extensions to this spec.

### Value concatenation

The value of an object field or an array element may consist of
multiple values which are concatenated into one string.

Only simple values participate in value concatenation. Recall that
a simple value is any value other than arrays and objects.

As long as simple values are separated only by non-newline
whitespace, the _whitespace between them is preserved_ and the
values, along with the whitespace, are concatenated into a string.

Value concatenations never span a newline, or a character that is
not part of a simple value.

A value concatenation may appear in any place that a string may
appear, including object keys, object values, and array elements.

Whenever a value would appear in JSON, a HOCON parser instead
collects multiple values (including the whitespace between them)
and concatenates those values into a string.

Whitespace before the first and after the last simple value must
be discarded. Only whitespace _between_ simple values must be
preserved.

So for example ` foo bar baz ` parses as three unquoted strings,
and the three are value-concatenated into one string. The inner
whitespace is kept and the leading and trailing whitespace is
trimmed. The equivalent string, written in quoted form, would be
`"foo bar baz"`.

Value concatenation `foo bar` (two unquoted strings with
whitespace) and quoted string `"foo bar"` would result in the same
in-memory representation, seven characters.

For purposes of value concatenation, non-string values are
converted to strings as follows (strings shown as quoted strings):

 - `true` and `false` become the strings `"true"` and `"false"`.
 - `null` becomes the string `"null"`.
 - quoted and unquoted strings are themselves.
 - numbers should be kept as they were originally written in the
   file. For example, if you parse `1e5` then you might render
   it alternatively as `1E5` with capital `E`, or just `100000`.
   For purposes of value concatenation, it should be rendered
   as it was written in the file.
 - a substitution is replaced with its value which is then
   converted to a string as above.
 - it is invalid for arrays or objects to appear in a value
   concatenation.

A single value is never converted to a string. That is, it would
be wrong to value concatenate `true` by itself; that should be
parsed as a boolean-typed value. Only `true foo` (`true` with
another simple value on the same line) should be parsed as a value
concatenation and converted to a string.

### Path expressions

Path expressions are used to write out a path through the object
graph. They appear in two places; in substitutions, like
`${foo.bar}`, and as the keys in objects like `{ foo.bar : 42 }`.

Path expressions are syntactically identical to a value
concatenation, except that they may not contain
substitutions. This means that you can't nest substitutions inside
other substitutions, and you can't have substitutions in keys.

When concatenating the path expression, any `.` characters outside
quoted strings are understood as path separators, while inside
quoted strings `.` has no special meaning. So
`foo.bar."hello.world"` would be a path with three elements,
looking up key `foo`, key `bar`, then key `hello.world`.

The main tricky point is that `.` characters in numbers do count
as a path separator. When dealing with a number as part of a path
expression, it's essential to retain the _original_ string
representation of the number as it appeared in the file (rather
than converting it back to a string with a generic
number-to-string library function).

 - `10.0foo` is a number then unquoted string `foo` and should
   be the two-element path with `10` and `0foo` as the elements.
 - `foo10.0` is an unquoted string with a `.` in it, so this would
   be a two-element path with `foo10` and `0` as the elements.
 - `foo"10.0"` is an unquoted then a quoted string which are
   concatenated, so this is a single-element path.

Unlike value concatenations, path expressions are _always_
converted to a string, even if they are just a single value.

If you have an array or element value consisting of the single
value `true`, it's a value concatenation and retains its character
as a boolean value.

If you have a path expression (in a key or substitution) then it
must always be converted to a string, so `true` becomes the string
that would be quoted as `"true"`.

If a path element is an empty string, it must always be quoted.
That is, `a."".b` is a valid path with three elements, and the
middle element is an empty string. But `a..b` is invalid and
should generate an error. Following the same rule, a path that
starts or ends with a `.` is invalid and should generate an error.

### Paths as keys

If a key is a path expression with multiple elements, it is
expanded to create an object for each path element other than the
last. The last path element, combined with the value, becomes a
field in the most-nested object.

In other words:

    foo.bar : 42

is equivalent to:

    foo { bar : 42 }

and:

    foo.bar.baz : 42

is equivalent to:

    foo { bar { baz : 42 } }

and so on. These values are merged in the usual way; which implies
that:

    a.x : 42, a.y : 43

is equivalent to:

    a { x : 42, y : 43 }

Because path expressions work like value concatenations, you can
have whitespace in keys:

    a b c : 42

is equivalent to:

    "a b c" : 42

Because path expressions are always converted to strings, even
single values that would normally have another type become
strings.

   - `true : 42` is `"true" : 42`
   - `3.14 : 42` is `"3.14" : 42`

As a special rule, the unquoted string `include` may not begin a
path expression in a key, because it has a special interpretation
(see below).

### Substitutions

Substitutions are a way of referring to other parts of the
configuration tree.

The syntax is `${pathexpression}` or `${?pathexpression}` where
the `pathexpression` is a path expression as described above. This
path expression has the same syntax that you could use for an
object key.

The `?` in `${?pathexpression}` must not have whitespace before
it; the three characters `${?` must be exactly like that, grouped
together.

For substitutions which are not found in the configuration tree,
implementations may try to resolve them by looking at system
environment variables or other external sources of configuration.
(More detail on environment variables in a later section.)

Substitutions are not parsed inside quoted strings. To get a
string containing a substitution, you must use value concatenation
with the substitution in the unquoted portion:

    key : ${animal.favorite} is my favorite animal

Or you could quote the non-substitution portion:

    key : ${animal.favorite}" is my favorite animal"

Substitutions are resolved by looking up the path in the
configuration. The path begins with the root configuration object,
i.e. it is "absolute" rather than "relative."

Substitution processing is performed as the last parsing step, so
a substitution can look forward in the configuration. If a
configuration consists of multiple files, it may even end up
retrieving a value from another file. If a key has been specified
more than once, the substitution will always evaluate to its
latest-assigned value (the merged object or the last non-object
value that was set).

If a configuration sets a value to `null` then it should not be
looked up in the external source. Unfortunately there is no way to
"undo" this in a later configuration file; if you have `{ "HOME" :
null }` in a root object, then `${HOME}` will never look at the
environment variable. There is no equivalent to JavaScript's
`delete` operation in other words.

If a substitution does not match any value present in the
configuration and is not resolved by an external source, then it
is undefined. An undefined substitution with the `${foo}` syntax
is invalid and should generate an error.

If a substitution with the `${?foo}` syntax is undefined:

 - if it is the value of an object field then the field should not
   be created.
 - if it is an array element then the element should not be added.
 - if it is part of a value concatenation then it should become an
   empty string.
 - `foo : ${?bar}` would avoid creating field `foo` if `bar` is
   undefined, but `foo : ${?bar} ${?baz}` would be a value
   concatenation so if `bar` or `baz` are not defined, the result
   is an empty string.

Substitutions are only allowed in object field values and array
elements (value concatenations), they are not allowed in keys or
nested inside other substitutions (path expressions).

A substitution is replaced with any value type (number, object,
string, array, true, false, null). If the substitution is the only
part of a value, then the type is preserved. Otherwise, it is
value-concatenated to form a string.

Circular substitutions are invalid and should generate an error.

Implementations must take care, however, to allow objects to refer
to paths within themselves. For example, this must work:

    bar : { foo : 42,
            baz : ${bar.foo}
          }

Here, if an implementation resolved all substitutions in `bar` as
part of resolving the substitution `${bar.foo}`, there would be a
cycle. The implementation must only resolve the `foo` field in
`bar`, rather than recursing the entire `bar` object.

### Includes

#### Include syntax

An _include statement_ consists of the unquoted string `include`
and a single quoted string immediately following it. An include
statement can appear in place of an object field.

If the unquoted string `include` appears at the start of a path
expression where an object key would be expected, then it is not
interpreted as a path expression or a key.

Instead, the next value must be a _quoted_ string. The quoted
string is interpreted as a filename or resource name to be
included.

Together, the unquoted `include` and the quoted string substitute
for an object field syntactically, and are separated from the
following object fields or includes by the usual comma (and as
usual the comma may be omitted if there's a newline).

If an unquoted `include` at the start of a key is followed by
anything other than a single quoted string, it is invalid and an
error should be generated.

There can be any amount of whitespace, including newlines, between
the unquoted `include` and the quoted string.

Value concatenation is NOT performed on the "argument" to
`include`. The argument must be a single quoted string. No
substitutions are allowed, and the argument may not be an unquoted
string or any other kind of value.

Unquoted `include` has no special meaning if it is not the start
of a key's path expression.

It may appear later in the key:

    # this is valid
    { foo include : 42 }
    # equivalent to
    { "foo include" : 42 }

It may appear as an object or array value:

    { foo : include } # value is the string "include"
    [ include ]       # array of one string "include"

You can quote `"include"` if you want a key that starts with the
word `"include"`, only unquoted `include` is special:

    { "include" : 42 }

#### Include semantics: merging

An _including file_ contains the include statement and an
_included file_ is the one specified in the include statement.
(They need not be regular files on a filesystem, but assume they
are for the moment.)

An included file must contain an object, not an array. This is
significant because both JSON and HOCON allow arrays as root
values in a document.

If an included file contains an array as the root value, it is
invalid and an error should be generated.

The included file should be parsed, producing a root object. The
keys from the root object are conceptually substituted for the
include statement in the including file.

 - If a key in the included object occurred prior to the include
   statement in the including object, the included key's value
   overrides or merges with the earlier value, exactly as with
   duplicate keys found in a single file.
 - If the including file repeats a key from an earlier-included
   object, the including file's value would override or merge
   with the one from the included file.

#### Include semantics: substitution

Substitutions in included files are looked up at two different
paths; first, relative to the root of the included file; second,
relative to the root of the including configuration.

Recall that substitution happens as a final step, _after_
parsing. It should be done for the entire app's configuration, not
for single files in isolation.

Therefore, if an included file contains substitutions, they must
be "fixed up" to be relative to the app's configuration root.

Say for example that the root configuration is this:

    { a : { include "foo.conf" } }

And "foo.conf" might look like this:

    { x : 10, y : ${x} }

If you parsed "foo.conf" in isolation, then `${x}` would evaluate
to 10, the value at the path `x`. If you include "foo.conf" in an
object at key `a`, however, then it must be fixed up to be
`${a.x}` rather than `${x}`.

Say that the root configuration redefines `a.x`, like this:

    {
        a : { include "foo.conf" }
        a : { x : 42 }
    }

Then the `${x}` in "foo.conf", which has been fixed up to
`${a.x}`, would evaluate to `42` rather than to `10`.
Substitution happens _after_ parsing the whole configuration.

However, there are plenty of cases where the included file might
intend to refer to the application's root config. For example, to
get a value from a system property or from the reference
configuration. So it's not enough to only look up the "fixed up"
path, it's necessary to look up the original path as well.

#### Include semantics: missing files

If an included file does not exist, the include statement should
be silently ignored (as if the included file contained only an
empty object).

#### Include semantics: file formats and extensions

Implementations may support including files in other formats.
Those formats must be compatible with the JSON type system, or
have some documented mapping to JSON's type system.

If an implementation supports multiple formats, then the extension
may be omitted from the name of included files:

    include "foo"

If a filename has no extension, the implementation should treat it
as a basename and try loading the file with all known extensions.

If the file exists with multiple extensions, they should _all_ be
loaded and merged together.

Files in HOCON format should be parsed last. Files in JSON format
should be parsed next-to-last.

In short, `include "foo"` might be equivalent to:

    include "foo.properties"
    include "foo.json"
    include "foo.conf"

#### Include semantics: locating resources

Conceptually speaking, the quoted string in an include statement
identifies a file or other resource "adjacent to" the one being
parsed and of the same type as the one being parsed. The meaning
of "adjacent to", and the string itself, has to be specified
separately for each kind of resource.

Implementations may vary in the kinds of resources they support
including.

On the Java Virtual Machine, if an include statement does not
identify anything "adjacent to" the including resource,
implementations may wish to fall back to a classpath resource.
This allows configurations found in files or URLs to access
classpath resources.

For resources located on the Java classpath:

 - included resources are looked up by calling `getResource()` on
   the same class loader used to look up the including resource.
 - if the included resource name is absolute (starts with '/')
   then it should be passed to `getResource()` with the '/'
   removed.
 - if the included resource name does not start with '/' then it
   should have the "directory" of the including resource.
   prepended to it, before passing it to `getResource()`.  If the
   including resource is not absolute (no '/') and has no "parent
   directory" (is just a single path element), then the included
   relative resource name should be left as-is.
 - it would be wrong to use `getResource()` to get a URL and then
   locate the included name relative to that URL, because a class
   loader is not required to have a one-to-one mapping between
   paths in its URLs and the paths it handles in `getResource()`.
   In other words, the "adjacent to" computation should be done
   on the resource name not on the resource's URL.

For plain files on the filesystem:

 - if the included file is an absolute path then it should be kept
   absolute and loaded as such.
 - if the included file is a relative path, then it should be
   located relative to the directory containing the including
   file.  The current working directory of the process parsing a
   file must NOT be used when interpreting included paths.
 - if the file is not found, fall back to the classpath resource.
   The classpath resource should not have any package name added
   in front, it should be relative to the "root"; which means any
   leading "/" should just be removed (absolute is the same as
   relative since it's root-relative). The "/" is handled for
   consistency with including resources from inside other
   classpath resources, where the resource name may not be
   root-relative and "/" allows specifying relative to root.

URLs:

 - for both filesystem files and Java resources, if the
   included name is a URL (begins with a protocol), it would
   be reasonable behavior to try to load the URL rather than
   treating the name as a filename or resource name.
 - for files loaded from a URL, "adjacent to" should be based
   on parsing the URL's path component, replacing the last
   path element with the included name.
 - file: URLs should behave in exactly the same way as a plain
   filename

Implementations need not support files, Java resources, or URLs;
and they need not support particular URL protocols. However, if
they do support them they should do so as described above.

## API Recommendations

Implementations of HOCON ideally follow certain conventions and
work in a predictable way.

### Automatic type conversions

If an application asks for a value with a particular type, the
implementation should attempt to convert types as follows:

 - number to string: convert the number into a string
   representation that would be a valid number in JSON.
 - boolean to string: should become the string "true" or "false"
 - string to number: parse the number with the JSON rules
 - string to boolean: the strings "true", "yes", "on", "false",
   "no", "off" should be converted to boolean values. It's
   tempting to support a long list of other ways to write a
   boolean, but for interoperability and keeping it simple, it's
   recommended to stick to these six.
 - string to null: the string `"null"` should be converted to a
   null value if the application specifically asks for a null
   value, though there's probably no reason an app would do this.

The following type conversions should NOT be performed:

 - null to anything: If the application asks for a specific type
   and finds null instead, that should usually result in an error.
 - object to anything
 - array to anything
 - anything to object
 - anything to array

Converting objects and arrays to and from strings is tempting, but
in practical situations raises thorny issues of quoting and
double-escaping.

### Units format

Implementations may wish to support interpreting a value with some
family of units, such as time units or memory size units: `10ms`
or `512K`. HOCON does not have an extensible type system and there
is no way to add a "duration" type. However, for example, if an
application asks for milliseconds, the implementation can try to
interpret a value as a milliseconds value.

If an API supports this, for each family of units it should define
a default unit in the family. For example, the family of duration
units might default to milliseconds (see below for details on
durations). The implementation should then interpret values as
follows:

 - if the value is a number, it is taken to be a number in
   the default unit.
 - if the value is a string, it is taken to be this sequence:

     - optional whitespace
     - a number
     - optional whitespace
     - an optional unit name consisting only of letters (letters
       are the Unicode `L*` categories, Java `isLetter()`)
     - optional whitespace

   If a string value has no unit name, then it should be
   interpreted with the default unit, as if it were a number. If a
   string value has a unit name, that name of course specifies the
   value's interpretation.

### Duration format

Implementations may wish to support a `getMilliseconds()` (and
similar for other time units).

This can use the general "units format" described above; bare
numbers are taken to be in milliseconds already, while strings are
parsed as a number plus an optional unit string.

The supported unit strings for duration are case sensitive and
must be lowercase. Exactly these strings are supported:

 - `ns`, `nanosecond`, `nanoseconds`
 - `us`, `microsecond`, `microseconds`
 - `ms`, `millisecond`, `milliseconds`
 - `s`, `second`, `seconds`
 - `m`, `minute`, `minutes`
 - `h`, `hour`, `hours`
 - `d`, `day`, `days`

### Size in bytes format

Implementations may wish to support a `getBytes()` returning a
size in bytes.

This can use the general "units format" described above; bare
numbers are taken to be in bytes already, while strings are
parsed as a number plus an optional unit string.

The one-letter unit strings may be uppercase (note: duration units
are always lowercase, so this convention is specific to size
units).

There is an unfortunate nightmare with size-in-bytes units, that
they may be in powers or two or powers of ten. The approach
defined by standards bodies appears to differ from common usage,
such that following the standard leads to people being confused.
Worse, common usage varies based on whether people are talking
about RAM or disk sizes, and various existing operating systems
and apps do all kinds of different things.  See
http://en.wikipedia.org/wiki/Binary_prefix#Deviation_between_powers_of_1024_and_powers_of_1000
for examples. It appears impossible to sort this out without
causing confusion for someone sometime.

For single bytes, exactly these strings are supported:

 - `B`, `b`, `byte`, `bytes`

For powers of ten, exactly these strings are supported:

 - `kB`, `kilobyte`, `kilobytes`
 - `MB`, `megabyte`, `megabytes`
 - `GB`, `gigabyte`, `gigabytes`
 - `TB`, `terabyte`, `terabytes`
 - `PB`, `petabyte`, `petabytes`
 - `EB`, `exabyte`, `exabytes`
 - `ZB`, `zettabyte`, `zettabytes`
 - `YB`, `yottabyte`, `yottabytes`

For powers of two, exactly these strings are supported:

 - `K`, `k`, `Ki`, `KiB`, `kibibyte`, `kibibytes`
 - `M`, `m`, `Mi`, `MiB`, `mebibyte`, `mebibytes`
 - `G`, `g`, `Gi`, `GiB`, `gibibyte`, `gibibytes`
 - `T`, `t`, `Ti`, `TiB`, `tebibyte`, `tebibytes`
 - `P`, `p`, `Pi`, `PiB`, `pebibyte`, `pebibytes`
 - `E`, `e`, `Ei`, `EiB`, `exbibyte`, `exbibytes`
 - `Z`, `z`, `Zi`, `ZiB`, `zebibyte`, `zebibytes`
 - `Y`, `y`, `Yi`, `YiB`, `yobibyte`, `yobibytes`

It's very unclear which units the single-character abbreviations
("128K") should go with; some precedents such as `java -Xmx 2G`
and the GNU tools such as `ls` map these to powers of two, so this
spec copies that. You can certainly find examples of mapping these
to powers of ten, though. If you don't like ambiguity, don't use
the single-letter abbreviations.

### Config object merging and file merging

It may be useful to offer a method to merge two objects. If such a
method is provided, it should work as if the two objects were
duplicate values for the same key in the same file. (See the
section earlier on duplicate key handling.)

As with duplicate keys, an intermediate non-object value "hides"
earlier object values. So say you merge three objects in this
order:

 - `{ a : { x : 1 } }`  (first priority)
 - `{ a : 42 }` (fallback)
 - `{ a : { y : 2 } }` (another fallback)

The result would be `{ a : { x : 1 } }`. The two objects are not
merged because they are not "adjacent"; the merging is done in
pairs, and when `42` is paired with `{ y : 2 }`, `42` simply wins
and loses all information about what it overrode.

But if you re-ordered like this:

 - `{ a : { x : 1 } }`  (first priority)
 - `{ a : { y : 2 } }` (fallback)
 - `{ a : 42 }` (another fallback)

Now the result would be `{ a : { x : 1, y : 2 } }` because the two
objects are adjacent.

This rule for merging objects loaded from different files is
_exactly_ the same behavior as for merging duplicate fields in the
same file. All merging works the same way.

Needless to say, normally it's well-defined whether a config
setting is supposed to be a number or an object. This kind of
weird pathology where the two are mixed should not be happening.

The one place where it matters, though, is that it allows you to
"clear" an object and start over by setting it to null and then
setting it back to a new object. So this behavior gives people a
way to get rid of default fallback values they don't want.

### Java properties mapping

It may be useful to merge Java properties data with data loaded
from JSON or HOCON. See the Java properties spec here:
http://download.oracle.com/javase/7/docs/api/java/util/Properties.html#load%28java.io.Reader%29

Java properties parse as a one-level map from string keys to
string values.

To convert to HOCON, first split each key on the `.` character,
keeping any empty strings (including leading and trailing empty
strings). Note that this is _very different_ from parsing a path
expression.

The key split on `.` is a series of path elements. So the
properties key with just `.` is a path with two elements, both of
them an empty string. `a.` is a path with two elements, `a` and
empty string.  (Java's `String.split()` does NOT do what you want
for this.)

It is impossible to represent a key with a `.` in it in a
properties file.  If a JSON/HOCON key has a `.` in it, which is
possible if the key is quoted, then there is no way to refer to it
as a Java property. It is not recommended to name HOCON keys with
a `.` in them, since it would be confusing at best in any case.

Once you have a path for each value, construct a tree of
JSON-style objects with the string value of each property located
at that value's path.

Values from properties files are _always_ strings, even if they
could be parsed as some other type. Implementations should do type
conversion if an app asks for an integer, as described in an
earlier section.

When Java loads a properties file, unfortunately it does not
preserve the order of the file. As a result, there is an
intractable case where a single key needs to refer to both a
parent object and a string value. For example, say the Java
properties file has:

    a=hello
    a.b=world

In this case, `a` needs to be both an object and a string value.
The _object_ must always win in this case... the "object wins"
rule throws out at most one value (the string) while "string wins"
would throw out all values in the object. Unfortunately, when
properties files are mapped to the JSON structure, there is no way
to access these strings that conflict with objects.

The usual rule in HOCON would be that the later assignment in the
file wins, rather than "object wins"; but implementing that for
Java properties would require implementing a custom Java
properties parser, which is surely not worth it and wouldn't help
with system properties anyway.

### Conventional configuration files for JVM apps

By convention, JVM apps have two parts to their configuration:

 - the _reference_ config is made up of all resources named
   `reference.conf` on the classpath, merged in the order they
   are returned by `ClassLoader.getResources()`; also, system
   property overrides are applied.
 - the _application_ config can be loaded from anywhere an
   application likes, but by default if the application doesn't
   provide a config it would be loaded from files
   `application.{conf,json,properties}` on the classpath and
   then system property overrides are applied.
 - a single JVM may have multiple application configs.

The reference config should be merged and resolved first, and
shared among all application configs. Substitutions in the
reference config are not affected by any application configs,
because the reference config should be resolved by itself.

The application config should then be loaded, have the reference
config added as a fallback, and have substitutions resolved. This
means the application config can refer to the reference config in
its substitutions.

### Conventional override by system properties

For an application's config, Java system properties _override_
settings found in the configuration file. This supports specifying
config options on the command line.

### Substitution fallback to environment variables

Recall that if a substitution is not present (not even set to
`null`) within a configuration tree, implementations may search
for it from external sources. One such source could be environment
variables.

It's recommended that HOCON keys always use lowercase, because
environment variables generally are capitalized. This avoids
naming collisions between environment variables and configuration
properties. (While on Windows getenv() is generally not
case-sensitive, the lookup will be case sensitive all the way
until the env variable fallback lookup is reached.)

An application can explicitly block looking up a substitution in
the environment by setting a value in the configuration, with the
same name as the environment variable. You could set `HOME : null`
in your root object to avoid expanding `${HOME}` from the
environment, for example.

Environment variables are interpreted as follows:

 - env variables set to the empty string are kept as such (set to
   empty string, rather than undefined)
 - System.getenv throws SecurityException: treated as not present
 - encoding is handled by Java (System.getenv already returns
   a Unicode string)
 - environment variables always become a string value, though
   if an app asks for another type automatic type conversion
   would kick in

## Note on Java properties similarity

You can write a HOCON file that looks much like a Java properties
file, and many valid Java properties files will also parse as
HOCON.

However, HOCON is not a Java properties superset and the corner
cases work like JSON, not like properties.

Differences include but are probably not limited to:

 - certain characters that can be unquoted in properties files
   have to be placed in JSON-style double-quoted strings in HOCON
 - unquoted strings in HOCON do not support escape sequences
 - unquoted strings in HOCON do not preserve trailing whitespace
 - multi-line unquoted strings using backslash to continue the
   line are not allowed in HOCON
 - in properties files you can omit the value for a key and it's
   interpreted as an empty string, in HOCON you cannot omit the
   value
 - properties files support '!' as a comment character
 - HOCON allows comments on the same line as a key or value, while
   properties files only recognize comment characters if they
   occur as the first character on the line
 - HOCON interprets `${}` as a substitution
