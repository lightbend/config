package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

abstract class AbstractConfigObject extends AbstractConfigValue implements
        ConfigObject {
    protected AbstractConfigObject(ConfigOrigin origin) {
        super(origin);
    }

    /**
     * Returns a version of this object that implements the ConfigRoot
     * interface.
     *
     * @return a config root
     */
    protected ConfigRootImpl asRoot(Path rootPath) {
        return new RootConfigObject(this, rootPath);
    }

    protected static ConfigRootImpl resolve(ConfigRootImpl root) {
        return resolve(root, ConfigResolveOptions.defaults());
    }

    protected static ConfigRootImpl resolve(ConfigRootImpl root,
            ConfigResolveOptions options) {
        AbstractConfigValue resolved = SubstitutionResolver.resolve(
                (AbstractConfigValue) root, (AbstractConfigObject) root,
                options);
        return ((AbstractConfigObject) resolved).asRoot(root.rootPathObject());
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
            SubstitutionResolver resolver, int depth,
            ConfigResolveOptions options) {
        AbstractConfigValue v = peek(key);

        if (v != null && resolver != null) {
            v = resolver.resolve(v, depth, options);
        }

        return v;
    }

    /**
     * Looks up the path with no transformation, type conversion, or exceptions
     * (just returns null if path not found). Does however resolve the path, if
     * resolver != null.
     */
    protected ConfigValue peekPath(Path path, SubstitutionResolver resolver,
            int depth, ConfigResolveOptions options) {
        return peekPath(this, path, resolver, depth, options);
    }

    private static ConfigValue peekPath(AbstractConfigObject self, Path path,
            SubstitutionResolver resolver, int depth,
            ConfigResolveOptions options) {
        String key = path.first();
        Path next = path.remainder();

        if (next == null) {
            ConfigValue v = self.peek(key, resolver, depth, options);
            return v;
        } else {
            // it's important to ONLY resolve substitutions here, not
            // all values, because if you resolve arrays or objects
            // it creates unnecessary cycles as a side effect (any sibling
            // of the object we want to follow could cause a cycle, not just
            // the object we want to follow).

            ConfigValue v = self.peek(key);

            if (v instanceof ConfigSubstitution && resolver != null) {
                v = resolver.resolve((AbstractConfigValue) v, depth, options);
            }

            if (v instanceof AbstractConfigObject) {
                return peekPath((AbstractConfigObject) v, next, resolver,
                        depth, options);
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
    public boolean hasPath(String pathExpression) {
        Path path = Path.newPath(pathExpression);
        ConfigValue peeked = peekPath(path, null, 0, null);
        return peeked != null && peeked.valueType() != ConfigValueType.NULL;
    }

    protected abstract AbstractConfigObject newCopy(ResolveStatus status);

    static private AbstractConfigValue resolve(AbstractConfigObject self,
            String pathExpression, ConfigValueType expected,
            String originalPath) {
        Path path = Path.newPath(pathExpression);
        return find(self, path, expected, originalPath);
    }

    static private AbstractConfigValue findKey(AbstractConfigObject self,
            String key, ConfigValueType expected, String originalPath) {
        AbstractConfigValue v = self.peek(key);
        if (v == null)
            throw new ConfigException.Missing(originalPath);

        if (expected != null)
            v = DefaultTransformer.transform(v, expected);

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
            Path path, ConfigValueType expected,
            String originalPath) {
        String key = path.first();
        Path next = path.remainder();
        if (next == null) {
            return findKey(self, key, expected, originalPath);
        } else {
            AbstractConfigObject o = (AbstractConfigObject) findKey(self, key,
                    ConfigValueType.OBJECT, originalPath);
            assert (o != null); // missing was supposed to throw
            return find(o, next, expected, originalPath);
        }
    }

    AbstractConfigValue find(String pathExpression,
            ConfigValueType expected,
            String originalPath) {
        return resolve(this, pathExpression, expected,
                originalPath);
    }

    @Override
    public AbstractConfigObject withFallbacks(ConfigValue... others) {
        return (AbstractConfigObject) super.withFallbacks(others);
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
        final String prefix = "merge of ";
        StringBuilder sb = new StringBuilder();
        ConfigOrigin firstOrigin = null;
        int numMerged = 0;
        for (AbstractConfigValue v : stack) {
            if (firstOrigin == null)
                firstOrigin = v.origin();

            String desc = v.origin().description();
            if (desc.startsWith(prefix))
                desc = desc.substring(prefix.length());

            if (v instanceof ConfigObject && ((ConfigObject) v).isEmpty()) {
                // don't include empty files or the .empty()
                // config in the description, since they are
                // likely to be "implementation details"
            } else {
                sb.append(desc);
                sb.append(",");
                numMerged += 1;
            }
        }
        if (numMerged > 0) {
            sb.setLength(sb.length() - 1); // chop comma
            if (numMerged > 1) {
                return new SimpleConfigOrigin(prefix + sb.toString());
            } else {
                return new SimpleConfigOrigin(sb.toString());
            }
        } else {
            // the configs were all empty.
            return firstOrigin;
        }
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

    private AbstractConfigObject modify(Modifier modifier,
            ResolveStatus newResolveStatus) {
        Map<String, AbstractConfigValue> changes = null;
        for (String k : keySet()) {
            AbstractConfigValue v = peek(k);
            AbstractConfigValue modified = modifier.modifyChild(v);
            if (modified != v) {
                if (changes == null)
                    changes = new HashMap<String, AbstractConfigValue>();
                changes.put(k, modified);
            }
        }
        if (changes == null) {
            return newCopy(newResolveStatus);
        } else {
            Map<String, AbstractConfigValue> modified = new HashMap<String, AbstractConfigValue>();
            for (String k : keySet()) {
                if (changes.containsKey(k)) {
                    modified.put(k, changes.get(k));
                } else {
                    modified.put(k, peek(k));
                }
            }
            return new SimpleConfigObject(origin(), modified,
                    newResolveStatus);
        }
    }

    @Override
    AbstractConfigObject resolveSubstitutions(final SubstitutionResolver resolver,
            final int depth,
            final ConfigResolveOptions options) {
        if (resolveStatus() == ResolveStatus.RESOLVED)
            return this;

        return modify(new Modifier() {

            @Override
            public AbstractConfigValue modifyChild(AbstractConfigValue v) {
                return resolver.resolve(v, depth, options);
            }

        }, ResolveStatus.RESOLVED);
    }

    @Override
    AbstractConfigObject relativized(final Path prefix) {
        return modify(new Modifier() {

            @Override
            public AbstractConfigValue modifyChild(AbstractConfigValue v) {
                return v.relativized(prefix);
            }

        }, resolveStatus());
    }

    @Override
    public AbstractConfigValue get(Object key) {
        if (key instanceof String)
            return peek((String) key);
        else
            return null;
    }

    @Override
    public AbstractConfigValue getValue(String path) {
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
        return obj;
    }

    @Override
    public Object getAnyRef(String path) {
        ConfigValue v = find(path, null, path);
        return v.unwrapped();
    }

    @Override
    public Long getMemorySizeInBytes(String path) {
        Long size = null;
        try {
            size = getLong(path);
        } catch (ConfigException.WrongType e) {
            ConfigValue v = find(path, ConfigValueType.STRING, path);
            size = Config.parseMemorySizeInBytes((String) v.unwrapped(), v.origin(),
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
            if (expected != null) {
                v = DefaultTransformer.transform(v, expected);
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
            l.add((ConfigObject) v);
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
    public List<Long> getMemorySizeInBytesList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() == ConfigValueType.NUMBER) {
                l.add(((Number) v.unwrapped()).longValue());
            } else if (v.valueType() == ConfigValueType.STRING) {
                String s = (String) v.unwrapped();
                Long n = Config.parseMemorySizeInBytes(s, v.origin(), path);
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

    private static boolean mapEquals(Map<String, ConfigValue> a,
            Map<String, ConfigValue> b) {
        Set<String> aKeys = a.keySet();
        Set<String> bKeys = b.keySet();

        if (!aKeys.equals(bKeys))
            return false;

        for (String key : aKeys) {
            if (!a.get(key).equals(b.get(key)))
                return false;
        }
        return true;
    }

    private static int mapHash(Map<String, ConfigValue> m) {
        // the keys have to be sorted, otherwise we could be equal
        // to another map but have a different hashcode.
        List<String> keys = new ArrayList<String>();
        keys.addAll(m.keySet());
        Collections.sort(keys);

        int valuesHash = 0;
        for (String k : keys) {
            valuesHash += m.get(k).hashCode();
        }
        return 41 * (41 + keys.hashCode()) + valuesHash;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigObject;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigObject) {
            // optimization to avoid unwrapped() for two ConfigObject,
            // which is what AbstractConfigValue does.
            return canEqual(other) && mapEquals(this, ((ConfigObject) other));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return mapHash(this);
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
