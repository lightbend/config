package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigRoot;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

abstract class AbstractConfigObject extends AbstractConfigValue implements
        ConfigObject {
    final protected ConfigTransformer transformer;

    protected AbstractConfigObject(ConfigOrigin origin,
            ConfigTransformer transformer) {
        super(origin);
        this.transformer = transformer;
        if (transformer == null)
            throw new ConfigException.BugOrBroken("null transformer");
    }

    protected AbstractConfigObject(ConfigOrigin origin) {
        this(origin, ConfigImpl.defaultConfigTransformer());
    }

    /**
     * Returns a version of this object that implements the ConfigRoot
     * interface.
     *
     * @return a config root
     */
    protected ConfigRoot asRoot() {
        return new RootConfigObject(this);
    }

    protected static ConfigRoot resolve(ConfigRoot root) {
        AbstractConfigValue resolved = SubstitutionResolver.resolve(
                (AbstractConfigValue) root, (AbstractConfigObject) root);
        return ((AbstractConfigObject) resolved).asRoot();
    }

    /**
     * This looks up the key with no transformation or type conversion of any
     * kind, and returns null if the key is not present.
     *
     * @param key
     * @return the unmodified raw value or null
     */
    protected abstract AbstractConfigValue peek(String key);

    protected AbstractConfigValue peek(String key,
            SubstitutionResolver resolver,
            int depth, boolean withFallbacks) {
        AbstractConfigValue v = peek(key);

        if (v != null && resolver != null) {
            v = resolver.resolve(v, depth, withFallbacks);
        }

        return v;
    }

    /**
     * Looks up the path with no transformation, type conversion, or exceptions
     * (just returns null if path not found).
     */
    protected ConfigValue peekPath(Path path) {
        return peekPath(this, path);
    }

    protected ConfigValue peekPath(Path path, SubstitutionResolver resolver,
            int depth,
            boolean withFallbacks) {
        return peekPath(this, path, resolver, depth, withFallbacks);
    }

    private static ConfigValue peekPath(AbstractConfigObject self, Path path) {
        return peekPath(self, path, null, 0, false);
    }

    private static ConfigValue peekPath(AbstractConfigObject self, Path path,
            SubstitutionResolver resolver, int depth, boolean withFallbacks) {
        String key = path.first();
        Path next = path.remainder();

        if (next == null) {
            ConfigValue v = self.peek(key, resolver, depth, withFallbacks);
            return v;
        } else {
            // it's important to ONLY resolve substitutions here, not
            // all values, because if you resolve arrays or objects
            // it creates unnecessary cycles as a side effect (any sibling
            // of the object we want to follow could cause a cycle, not just
            // the object we want to follow).

            ConfigValue v = self.peek(key);

            if (v instanceof ConfigSubstitution && resolver != null) {
                v = resolver.resolve((AbstractConfigValue) v, depth,
                        withFallbacks);
            }

            if (v instanceof AbstractConfigObject) {
                return peekPath((AbstractConfigObject) v, next, resolver,
                        depth, withFallbacks);
            } else {
                return null;
            }
        }
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.OBJECT;
    }

    @Override
    AbstractConfigObject transformed(ConfigTransformer newTransformer) {
        if (newTransformer != transformer)
            return newCopy(newTransformer, resolveStatus());
        else
            return this;
    }

    protected abstract AbstractConfigObject newCopy(
            ConfigTransformer newTransformer, ResolveStatus status);

    static private AbstractConfigValue resolve(AbstractConfigObject self,
            String pathExpression,
            ConfigValueType expected, ConfigTransformer transformer,
            String originalPath) {
        Path path = Path.newPath(pathExpression);
        return find(self, path, expected, transformer, originalPath);
    }

    static private AbstractConfigValue findKey(AbstractConfigObject self,
            String key, ConfigValueType expected,
            ConfigTransformer transformer, String originalPath) {
        AbstractConfigValue v = self.peek(key);
        if (v == null)
            throw new ConfigException.Missing(originalPath);

        if (expected != null && transformer != null)
            v = transformer.transform(v, expected);

        if (v.valueType() == ConfigValueType.NULL)
            throw new ConfigException.Null(v.origin(), originalPath,
                    expected != null ? expected.name() : null);
        else if (expected != null && v.valueType() != expected)
            throw new ConfigException.WrongType(v.origin(), originalPath,
                    expected.name(), v.valueType().name());
        else
            return v;
    }

    static private AbstractConfigValue find(AbstractConfigObject self,
            Path path, ConfigValueType expected, ConfigTransformer transformer,
            String originalPath) {
        String key = path.first();
        Path next = path.remainder();
        if (next == null) {
            return findKey(self, key, expected, transformer, originalPath);
        } else {
            AbstractConfigObject o = (AbstractConfigObject) findKey(self,
                    key, ConfigValueType.OBJECT, transformer, originalPath);
            assert (o != null); // missing was supposed to throw
            return find(o, next, expected, transformer, originalPath);
        }
    }

    AbstractConfigValue find(String pathExpression,
            ConfigValueType expected,
            String originalPath) {
        return resolve(this, pathExpression, expected, transformer,
                originalPath);
    }

    @Override
    public AbstractConfigObject withFallback(ConfigValue other) {
        if (other instanceof Unmergeable) {
            List<AbstractConfigValue> stack = new ArrayList<AbstractConfigValue>();
            stack.add(this);
            stack.addAll(((Unmergeable) other).unmergedValues());
            return new ConfigDelayedMergeObject(mergeOrigins(stack), stack);
        } else if (other instanceof AbstractConfigObject) {
            AbstractConfigObject fallback = (AbstractConfigObject) other;
            if (fallback.isEmpty()) {
                return this; // nothing to do
            } else {
                boolean allResolved = true;
                Map<String, AbstractConfigValue> merged = new HashMap<String, AbstractConfigValue>();
                Set<String> allKeys = new HashSet<String>();
                allKeys.addAll(this.keySet());
                allKeys.addAll(fallback.keySet());
                for (String key : allKeys) {
                    AbstractConfigValue first = this.peek(key);
                    AbstractConfigValue second = fallback.peek(key);
                    AbstractConfigValue kept;
                    if (first == null)
                        kept = second;
                    else if (second == null)
                        kept = first;
                    else
                        kept = first.withFallback(second);
                    merged.put(key, kept);
                    if (kept.resolveStatus() == ResolveStatus.UNRESOLVED)
                        allResolved = false;
                }
                return new SimpleConfigObject(mergeOrigins(this, fallback),
                        merged, ResolveStatus.fromBoolean(allResolved));
            }
        } else {
            // falling back to a non-object has no effect, we just override
            // primitive values.
            return this;
        }
    }

    static ConfigOrigin mergeOrigins(
            Collection<? extends AbstractConfigValue> stack) {
        if (stack.isEmpty())
            throw new ConfigException.BugOrBroken(
                    "can't merge origins on empty list");
        StringBuilder sb = new StringBuilder();
        String prefix = "merge of ";
        sb.append(prefix);
        for (AbstractConfigValue v : stack) {
            String desc = v.origin().description();
            if (desc.startsWith(prefix))
                desc = desc.substring(prefix.length());
            sb.append(desc);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1); // chop comma
        return new SimpleConfigOrigin(sb.toString());
    }

    static ConfigOrigin mergeOrigins(AbstractConfigObject... stack) {
        return mergeOrigins(Arrays.asList(stack));
    }

    /**
     * Stack should be from overrides to fallbacks (earlier items win). Objects
     * have their keys combined into a new object, while other kinds of value
     * are just first-one-wins.
     */
    static AbstractConfigObject merge(List<AbstractConfigObject> stack) {
        if (stack.isEmpty()) {
            return SimpleConfigObject.empty();
        } else if (stack.size() == 1) {
            return stack.get(0);
        } else {
            AbstractConfigObject merged = stack.get(0);
            for (int i = 1; i < stack.size(); ++i) {
                merged = merged.withFallback(stack.get(i));
            }
            return merged;
        }
    }

    @Override
    AbstractConfigObject resolveSubstitutions(SubstitutionResolver resolver,
            int depth,
            boolean withFallbacks) {
        if (resolveStatus() == ResolveStatus.RESOLVED)
            return this;

        Map<String, AbstractConfigValue> changes = null;
        for (String k : keySet()) {
            AbstractConfigValue v = peek(k);
            AbstractConfigValue resolved = resolver.resolve(v, depth,
                    withFallbacks);
            if (resolved != v) {
                if (changes == null)
                    changes = new HashMap<String, AbstractConfigValue>();
                changes.put(k, resolved);
            }
        }
        if (changes == null) {
            return newCopy(transformer, ResolveStatus.RESOLVED);
        } else {
            Map<String, AbstractConfigValue> resolved = new HashMap<String, AbstractConfigValue>();
            for (String k : keySet()) {
                if (changes.containsKey(k)) {
                    resolved.put(k, changes.get(k));
                } else {
                    resolved.put(k, peek(k));
                }
            }
            return new SimpleConfigObject(origin(), transformer, resolved,
                    ResolveStatus.RESOLVED);
        }
    }

    @Override
    public ConfigValue get(Object key) {
        if (key instanceof String)
            return peek((String) key);
        else
            return null;
    }

    @Override
    public ConfigValue getValue(String path) {
        return find(path, null, path);
    }

    @Override
    public boolean getBoolean(String path) {
        ConfigValue v = find(path, ConfigValueType.BOOLEAN, path);
        return (Boolean) v.unwrapped();
    }

    @Override
    public Number getNumber(String path) {
        ConfigValue v = find(path, ConfigValueType.NUMBER, path);
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
        ConfigValue v = find(path, ConfigValueType.STRING, path);
        return (String) v.unwrapped();
    }

    @Override
    public ConfigList getList(String path) {
        AbstractConfigValue v = find(path, ConfigValueType.LIST, path);
        return (ConfigList) v;
    }

    @Override
    public AbstractConfigObject getObject(String path) {
        AbstractConfigObject obj = (AbstractConfigObject) find(path,
                ConfigValueType.OBJECT, path);
        return obj.transformed(this.transformer);
    }

    @Override
    public Object getAnyRef(String path) {
        ConfigValue v = find(path, null, path);
        return v.unwrapped();
    }

    @Override
    public Long getMemorySize(String path) {
        Long size = null;
        try {
            size = getLong(path);
        } catch (ConfigException.WrongType e) {
            ConfigValue v = find(path, ConfigValueType.STRING, path);
            size = Config.parseMemorySize((String) v.unwrapped(), v.origin(),
                    path);
        }
        return size;
    }

    @Override
    public Long getMilliseconds(String path) {
        long ns = getNanoseconds(path);
        long ms = TimeUnit.NANOSECONDS.toMillis(ns);
        return ms;
    }

    @Override
    public Long getNanoseconds(String path) {
        Long ns = null;
        try {
            ns = TimeUnit.MILLISECONDS.toNanos(getLong(path));
        } catch (ConfigException.WrongType e) {
            ConfigValue v = find(path, ConfigValueType.STRING, path);
            ns = Config.parseDuration((String) v.unwrapped(), v.origin(), path);
        }
        return ns;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getHomogeneousUnwrappedList(String path,
            ConfigValueType expected) {
        List<T> l = new ArrayList<T>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue cv : list) {
            // variance would be nice, but stupid cast will do
            AbstractConfigValue v = (AbstractConfigValue) cv;
            if (expected != null && transformer != null) {
                v = transformer.transform(v, expected);
            }
            if (v.valueType() != expected)
                throw new ConfigException.WrongType(v.origin(), path,
                        "list of " + expected.name(), "list of "
                                + v.valueType().name());
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
    public List<String> getStringList(String path) {
        return getHomogeneousUnwrappedList(path, ConfigValueType.STRING);
    }

    @Override
    public List<ConfigObject> getObjectList(String path) {
        List<ConfigObject> l = new ArrayList<ConfigObject>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() != ConfigValueType.OBJECT)
                throw new ConfigException.WrongType(v.origin(), path,
                        ConfigValueType.OBJECT.name(), v.valueType().name());
            l.add(((AbstractConfigObject) v).transformed(this.transformer));
        }
        return l;
    }

    @Override
    public List<? extends Object> getAnyRefList(String path) {
        List<Object> l = new ArrayList<Object>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            l.add(v.unwrapped());
        }
        return l;
    }

    @Override
    public List<Long> getMemorySizeList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<? extends ConfigValue> list = getList(path);
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
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() == ConfigValueType.NUMBER) {
                l.add(TimeUnit.MILLISECONDS.toNanos(((Number) v.unwrapped())
                        .longValue()));
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(valueType().name());
        sb.append("(");
        for (String k : keySet()) {
            sb.append(k);
            sb.append("->");
            sb.append(peek(k).toString());
            sb.append(",");
        }
        if (!keySet().isEmpty())
            sb.setLength(sb.length() - 1); // chop comma
        sb.append(")");
        return sb.toString();
    }

    private static UnsupportedOperationException weAreImmutable(String method) {
        return new UnsupportedOperationException(
                "ConfigObject is immutable, you can't call Map.'" + method
                        + "'");
    }

    @Override
    public void clear() {
        throw weAreImmutable("clear");
    }

    @Override
    public ConfigValue put(String arg0, ConfigValue arg1) {
        throw weAreImmutable("put");
    }

    @Override
    public void putAll(Map<? extends String, ? extends ConfigValue> arg0) {
        throw weAreImmutable("putAll");
    }

    @Override
    public ConfigValue remove(Object arg0) {
        throw weAreImmutable("remove");
    }
}
