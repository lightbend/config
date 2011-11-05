package com.typesafe.config;

import java.util.List;
import java.util.Set;

/**
 * A Config is a read-only configuration object, which may have nested child
 * objects. Implementations of Config should be immutable (at least from the
 * perspective of anyone using this interface).
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

    List<ConfigValue> getList(String path);

    List<Boolean> getBooleanList(String path);

    List<Number> getNumberList(String path);

    List<Integer> getIntList(String path);

    List<Long> getLongList(String path);

    List<Double> getDoubleList(String path);

    List<ConfigObject> getObjectList(String path);

    List<Object> getAnyList(String path);

    boolean containsKey(String key);

    Set<String> keySet();
}
