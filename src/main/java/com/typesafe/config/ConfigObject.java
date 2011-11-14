package com.typesafe.config;

import java.util.List;
import java.util.Map;

/**
 * A ConfigObject is a read-only configuration object, which may have nested
 * child objects. Implementations of ConfigObject should be immutable (at least
 * from the perspective of anyone using this interface).
 *
 * The getters all have the same semantics; they throw ConfigException.Missing
 * if the value is entirely unset, and ConfigException.WrongType if you ask for
 * a type that the value can't be converted to. ConfigException.Null is a
 * subclass of ConfigException.WrongType thrown if the value is null. The "path"
 * parameters for all the getters have periods between the key names, so the
 * path "a.b.c" looks for key c in object b in object a in the root object. (The
 * syntax for paths is the same as in ${} substitution expressions in config
 * files, sometimes double quotes are needed around special characters.)
 *
 * ConfigObject implements the standard Java Map interface, but the mutator
 * methods all throw UnsupportedOperationException.
 *
 * TODO add OrNull variants of all these getters? Or better to avoid convenience
 * API for that?
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

    /** Get value as a size in bytes (parses special strings like "128M") */
    // rename getSizeInBytes ? clearer. allows a megabyte version
    // or just getBytes is consistent with getMilliseconds
    Long getMemorySize(String path);

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

    List<Long> getMemorySizeList(String path);

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
