package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.List;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigTransformer;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

abstract class AbstractConfigObject extends AbstractConfigValue implements
        ConfigObject {
    private ConfigTransformer transformer;

    protected AbstractConfigObject(ConfigOrigin origin,
            ConfigTransformer transformer) {
        super(origin);
        this.transformer = transformer;
    }

    protected abstract ConfigValue peek(String key);

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.OBJECT;
    }

    static private ConfigValue resolve(AbstractConfigObject self, String path,
            ConfigValueType expected, ConfigTransformer transformer,
            String originalPath) {
        String key = ConfigUtil.firstElement(path);
        String next = ConfigUtil.otherElements(path);
        if (next == null) {
            ConfigValue v = self.peek(key);
            if (v == null)
                throw new ConfigException.Missing(originalPath);

            if (expected != null && transformer != null)
                v = transformer.transform(v, expected);

            if (v.valueType() == ConfigValueType.NULL)
                throw new ConfigException.Null(v.origin(), originalPath,
                        expected.name());
            else if (expected != null && v.valueType() != expected)
                throw new ConfigException.WrongType(v.origin(), originalPath,
                        expected.name(), v.valueType().name());
            else
                return v;
        } else {
            AbstractConfigObject o = (AbstractConfigObject) self.getObject(key);
            assert (o != null); // missing was supposed to throw
            return resolve(o, next, expected, transformer, originalPath);
        }
    }

    private ConfigValue resolve(String path, ConfigValueType expected,
            String originalPath) {
        return resolve(this, path, expected, transformer, originalPath);
    }

    @Override
    public ConfigValue get(String path) {
        return resolve(path, null, path);
    }

    @Override
    public boolean getBoolean(String path) {
        ConfigValue v = resolve(path, ConfigValueType.BOOLEAN, path);
        return (Boolean) v.unwrapped();
    }

    @Override
    public Number getNumber(String path) {
        ConfigValue v = resolve(path, ConfigValueType.NUMBER, path);
        return (Number) v.unwrapped();
    }

    @Override
    public int getInt(String path) {
        return getNumber(path).intValue();
    }

    @Override
    public long getLong(String path) {
        return getNumber(path).longValue();
    }

    @Override
    public double getDouble(String path) {
        return getNumber(path).doubleValue();
    }

    @Override
    public String getString(String path) {
        ConfigValue v = resolve(path, ConfigValueType.STRING, path);
        return (String) v.unwrapped();
    }

    @Override
    public List<ConfigValue> getList(String path) {
        ConfigValue v = resolve(path, ConfigValueType.LIST, path);
        return ((ConfigList) v).asJavaList();
    }

    @Override
    public AbstractConfigObject getObject(String path) {
        ConfigValue v = resolve(path, ConfigValueType.OBJECT, path);
        return (AbstractConfigObject) v;
    }

    @Override
    public Object getAny(String path) {
        ConfigValue v = resolve(path, null, path);
        return v.unwrapped();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getHomogeneousUnwrappedList(String path,
            ConfigValueType expected) {
        List<T> l = new ArrayList<T>();
        List<ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() != expected)
                throw new ConfigException.WrongType(v.origin(), path,
                        expected.name(), v.valueType().name());
            l.add((T) v.unwrapped());
        }
        return l;
    }

    @Override
    public List<Boolean> getBooleanList(String path) {
        return getHomogeneousUnwrappedList(path, ConfigValueType.BOOLEAN);
    }

    @Override
    public List<Number> getNumberList(String path) {
        return getHomogeneousUnwrappedList(path, ConfigValueType.NUMBER);
    }

    @Override
    public List<Integer> getIntList(String path) {
        List<Integer> l = new ArrayList<Integer>();
        List<Number> numbers = getNumberList(path);
        for (Number n : numbers) {
            l.add(n.intValue());
        }
        return l;
    }

    @Override
    public List<Long> getLongList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<Number> numbers = getNumberList(path);
        for (Number n : numbers) {
            l.add(n.longValue());
        }
        return l;
    }

    @Override
    public List<Double> getDoubleList(String path) {
        List<Double> l = new ArrayList<Double>();
        List<Number> numbers = getNumberList(path);
        for (Number n : numbers) {
            l.add(n.doubleValue());
        }
        return l;
    }

    @Override
    public List<ConfigObject> getObjectList(String path) {
        List<ConfigObject> l = new ArrayList<ConfigObject>();
        List<ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() != ConfigValueType.OBJECT)
                throw new ConfigException.WrongType(v.origin(), path,
                        ConfigValueType.OBJECT.name(), v.valueType().name());
            l.add((ConfigObject) v);
        }
        return l;
    }

    @Override
    public List<Object> getAnyList(String path) {
        List<Object> l = new ArrayList<Object>();
        List<ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            l.add(v.unwrapped());
        }
        return l;
    }
}