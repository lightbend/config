package com.typesafe.config;

import java.util.List;
import java.util.Map;

/**
 * A ConfigObject is a read-only configuration object, which may have nested
 * child objects. Implementations of ConfigObject should be immutable (at least
 * from the perspective of anyone using this interface).
 * 
 * Throughout the API, there is a distinction between "keys" and "paths". A key
 * is a key in a JSON object; it's just a string that's the key in a map. A
 * "path" is a parseable expression with a syntax and it refers to a series of
 * keys. A path is used to traverse nested ConfigObject by looking up each key
 * in the path. Path expressions are described in the spec for "HOCON", which
 * can be found at https://github.com/havocp/config/blob/master/HOCON.md; in
 * brief, a path is period-separated so "a.b.c" looks for key c in object b in
 * object a in the root object. Sometimes double quotes are needed around
 * special characters in path expressions.
 *
 * ConfigObject implements java.util.Map<String,ConfigValue>. For all methods
 * implementing the Map interface, the keys are just plain keys; not a parseable
 * path expression. In methods implementing Map, a ConfigValue with
 * ConfigValue.valueType() of ConfigValueType.NULL will be distinct from a
 * missing value. java.util.Map.containsKey() returns true if the map contains a
 * value of type ConfigValueType.NULL at that key.
 *
 * ConfigObject has another set of "getters", such as getValue() and getAnyRef()
 * and getInt(), with more convenient semantics than java.util.Map.get(). These
 * "getters" throw ConfigException.Missing if the value is entirely unset, and
 * ConfigException.WrongType if you ask for a type that the value can't be
 * converted to. ConfigException.Null is a subclass of ConfigException.WrongType
 * thrown if the value is null. These getters also use path expressions, rather
 * than keys, as described above.
 *
 * While ConfigObject implements the standard Java Map interface, the mutator
 * methods all throw UnsupportedOperationException. This Map is immutable.
 *
 * The Map may contain null values, which will have ConfigValue.valueType() ==
 * ConfigValueType.NULL. When using methods from the Map interface, such as
 * get() or containsKey(), these null ConfigValue will be visible. But hasPath()
 * returns false for null values, and getInt() etc. throw ConfigException.Null
 * for null values.
 */
public interface ConfigObject extends ConfigValue, Map<String, ConfigValue> {

    /**
     * Recursively unwraps the object, returning a map from String to whatever
     * plain Java values are unwrapped from the object's values.
     */
    @Override
    Map<String, Object> unwrapped();

    @Override
    ConfigObject withFallback(ConfigValue other);

    @Override
    ConfigObject withFallbacks(ConfigValue... others);

    /**
     * Checks whether a value is present and non-null at the given path. This
     * differs in two ways from containsKey(): it looks for a path expression,
     * not a key; and it returns false for null values, while containsKey()
     * returns true indicating that the object contains a null value for the
     * key.
     *
     * If a path exists according to hasPath(), then getValue() will never throw
     * an exception. However, the typed getters, such as getInt(), will still
     * throw if the value is not convertible to the requested type.
     *
     * @param path
     *            the path expression
     * @return true if a non-null value is present at the path
     * @throws ConfigException.BadPath
     *             if the path expression is invalid
     */
    boolean hasPath(String path);

    boolean getBoolean(String path);

    Number getNumber(String path);

    int getInt(String path);

    long getLong(String path);

    double getDouble(String path);

    String getString(String path);

    ConfigObject getObject(String path);

    /**
     * Gets the value at the path as an unwrapped Java boxed value (Boolean,
     * Integer, Long, etc.)
     */
    Object getAnyRef(String path);

    /**
     * Gets the value at the given path, unless the value is a null value or
     * missing, in which case it throws just like the other getters. Use get()
     * from the Map interface if you want an unprocessed value.
     *
     * @param path
     * @return
     */
    ConfigValue getValue(String path);

    /**
     * Get value as a size in bytes (parses special strings like "128M"). The
     * size units are interpreted as for memory, not as for disk space, so they
     * are in powers of two.
     */
    Long getMemorySizeInBytes(String path);

    /**
     * Get value as a duration in milliseconds. If the value is already a
     * number, then it's left alone; if it's a string, it's parsed understanding
     * units suffixes like "10m" or "5ns"
     */
    Long getMilliseconds(String path);

    /**
     * Get value as a duration in nanoseconds. If the value is already a number
     * it's taken as milliseconds and converted to nanoseconds. If it's a
     * string, it's parsed understanding unit suffixes.
     */
    Long getNanoseconds(String path);

    /**
     * Gets a list value (with any element type) as a ConfigList, which
     * implements java.util.List<ConfigValue>. Throws if the path is unset or
     * null.
     *
     * @param path
     *            the path to the list value.
     * @return the ConfigList at the path
     */
    ConfigList getList(String path);

    List<Boolean> getBooleanList(String path);

    List<Number> getNumberList(String path);

    List<Integer> getIntList(String path);

    List<Long> getLongList(String path);

    List<Double> getDoubleList(String path);

    List<String> getStringList(String path);

    List<? extends ConfigObject> getObjectList(String path);

    List<? extends Object> getAnyRefList(String path);

    List<Long> getMemorySizeInBytesList(String path);

    List<Long> getMillisecondsList(String path);

    List<Long> getNanosecondsList(String path);

    /**
     * Gets a ConfigValue at the given key, or returns null if there is no
     * value. The returned ConfigValue may have ConfigValueType.NULL or any
     * other type, and the passed-in key must be a key in this object, rather
     * than a path expression.
     */
    @Override
    ConfigValue get(Object key);
}
