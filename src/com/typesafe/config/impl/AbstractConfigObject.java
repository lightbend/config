package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
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

    /**
     * This looks up the key with no transformation or type conversion of any
     * kind, and returns null if the key is not present.
     *
     * @param key
     * @return the unmodified raw value or null
     */
    protected abstract ConfigValue peek(String key);

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.OBJECT;
    }

    static AbstractConfigObject transformed(AbstractConfigObject obj,
            ConfigTransformer transformer) {
        if (obj.transformer != transformer)
            return new TransformedConfigObject(transformer, obj);
        else
            return obj;
    }

    private AbstractConfigObject transformed(AbstractConfigObject obj) {
        return transformed(obj, transformer);
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

    /**
     * Stack should be from overrides to fallbacks (earlier items win). Test
     * suite should check: merging of objects with a non-object in the middle.
     * Override of object with non-object, override of non-object with object.
     * Merging 0, 1, N objects.
     */
    static AbstractConfigObject merge(ConfigOrigin origin,
            List<AbstractConfigObject> stack,
            ConfigTransformer transformer) {
        if (stack.isEmpty()) {
            return new SimpleConfigObject(origin, transformer,
                    Collections.<String, ConfigValue> emptyMap());
        } else if (stack.size() == 1) {
            return transformed(stack.get(0), transformer);
        } else {
            // for non-objects, we just take the first value; but for objects we
            // have to do work to combine them.
            Map<String, ConfigValue> merged = new HashMap<String, ConfigValue>();
            Map<String, List<AbstractConfigObject>> objects = new HashMap<String, List<AbstractConfigObject>>();
            for (AbstractConfigObject obj : stack) {
                for (String key : obj.keySet()) {
                    ConfigValue v = obj.peek(key);
                    if (!merged.containsKey(key)) {
                        if (v.valueType() == ConfigValueType.OBJECT) {
                            // requires recursive merge and transformer fixup
                            List<AbstractConfigObject> stackForKey = null;
                            if (objects.containsKey(key)) {
                                stackForKey = objects.get(key);
                            } else {
                                stackForKey = new ArrayList<AbstractConfigObject>();
                            }
                            stackForKey.add(transformed(
                                    (AbstractConfigObject) v,
                                    transformer));
                        } else {
                            if (!objects.containsKey(key)) {
                                merged.put(key, v);
                            }
                        }
                    }
                }
            }
            for (String key : objects.keySet()) {
                List<AbstractConfigObject> stackForKey = objects.get(key);
                AbstractConfigObject obj = merge(origin, stackForKey, transformer);
                merged.put(key, obj);
            }

            return new SimpleConfigObject(origin, transformer, merged);
        }
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
        AbstractConfigObject obj = (AbstractConfigObject) resolve(path,
                ConfigValueType.OBJECT, path);
        return transformed(obj);
    }

    @Override
    public Object getAny(String path) {
        ConfigValue v = resolve(path, null, path);
        return v.unwrapped();
    }

    @Override
    public Long getMemorySize(String path) {
        Long size = null;
        try {
            size = getLong(path);
        } catch (ConfigException.WrongType e) {
            ConfigValue v = resolve(path, ConfigValueType.STRING, path);
            size = Config.parseMemorySize((String) v.unwrapped(), v.origin(),
                    path);
        }
        return size;
    }

    @Override
    public Long getMilliseconds(String path) {
        return TimeUnit.NANOSECONDS.toMillis(getNanoseconds(path));
    }

    @Override
    public Long getNanoseconds(String path) {
        Long ns = null;
        try {
            ns = getLong(path);
        } catch (ConfigException.WrongType e) {
            ConfigValue v = resolve(path, ConfigValueType.STRING, path);
            ns = Config.parseDuration((String) v.unwrapped(), v.origin(), path);
        }
        return ns;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getHomogeneousUnwrappedList(String path,
            ConfigValueType expected) {
        List<T> l = new ArrayList<T>();
        List<ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (expected != null && transformer != null) {
                v = transformer.transform(v, expected);
            }
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
            l.add(transformed((AbstractConfigObject) v));
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

    @Override
    public List<Long> getMemorySizeList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() == ConfigValueType.NUMBER) {
                l.add(((Number) v.unwrapped()).longValue());
            } else if (v.valueType() == ConfigValueType.STRING) {
                String s = (String) v.unwrapped();
                Long n = Config.parseMemorySize(s, v.origin(), path);
                l.add(n);
            } else {
                throw new ConfigException.WrongType(v.origin(), path,
                        "memory size string or number of bytes", v.valueType()
                                .name());
            }
        }
        return l;
    }

    @Override
    public List<Long> getMillisecondsList(String path) {
        List<Long> nanos = getNanosecondsList(path);
        List<Long> l = new ArrayList<Long>();
        for (Long n : nanos) {
            l.add(TimeUnit.NANOSECONDS.toMillis(n));
        }
        return l;
    }

    @Override
    public List<Long> getNanosecondsList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() == ConfigValueType.NUMBER) {
                l.add(((Number) v.unwrapped()).longValue());
            } else if (v.valueType() == ConfigValueType.STRING) {
                String s = (String) v.unwrapped();
                Long n = Config.parseDuration(s, v.origin(), path);
                l.add(n);
            } else {
                throw new ConfigException.WrongType(v.origin(), path,
                        "duration string or number of nanoseconds", v
                                .valueType().name());
            }
        }
        return l;
    }

}