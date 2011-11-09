package com.typesafe.config;

import java.util.List;
import java.util.Set;

/**
 * A Config is a read-only configuration object, which may have nested child
 * objects. Implementations of Config should be immutable (at least from the
 * perspective of anyone using this interface).
 *
 * The getters all have the same semantics; they throw ConfigException.Missing
 * if the value is entirely unset, and ConfigException.WrongType if you ask for
 * a type that the value can't be converted to. ConfigException.Null is a
 * subclass of ConfigException.WrongType thrown if the value is null.
 *
 * TODO add OrNull variants of all these getters?
 */
public interface ConfigObject extends ConfigValue {

    boolean getBoolean(String path);

    Number getNumber(String path);

    int getInt(String path);

    long getLong(String path);

    double getDouble(String path);

    String getString(String path);

    ConfigObject getObject(String path);

    Object getAny(String path);

    ConfigValue get(String path);

    /** Get value as a size in bytes (parses special strings like "128M") */
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

    List<? extends ConfigValue> getList(String path);

    List<Boolean> getBooleanList(String path);

    List<Number> getNumberList(String path);

    List<Integer> getIntList(String path);

    List<Long> getLongList(String path);

    List<Double> getDoubleList(String path);

    List<? extends ConfigObject> getObjectList(String path);

    List<? extends Object> getAnyList(String path);

    List<Long> getMemorySizeList(String path);

    List<Long> getMillisecondsList(String path);

    List<Long> getNanosecondsList(String path);

    boolean containsKey(String key);

    Set<String> keySet();
}
