package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

final class SimpleConfigList extends AbstractConfigValue implements ConfigList {

    final private List<AbstractConfigValue> value;

    SimpleConfigList(ConfigOrigin origin, List<AbstractConfigValue> value) {
        super(origin);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.LIST;
    }

    @Override
    public List<Object> unwrapped() {
        List<Object> list = new ArrayList<Object>();
        for (AbstractConfigValue v : value) {
            list.add(v.unwrapped());
        }
        return list;
    }

    @Override
    SimpleConfigList resolveSubstitutions(SubstitutionResolver resolver, int depth,
            boolean withFallbacks) {
        // lazy-create for optimization
        List<AbstractConfigValue> changed = null;
        int i = 0;
        for (AbstractConfigValue v : value) {
            AbstractConfigValue resolved = resolver.resolve(v, depth,
                    withFallbacks);

            // lazy-create the new list if required
            if (changed == null && resolved != v) {
                changed = new ArrayList<AbstractConfigValue>();
                for (int j = 0; j < i; ++j) {
                    changed.add(value.get(j));
                }
            }

            // once the new list is created, all elements
            // have to go in it.
            if (changed != null) {
                changed.add(resolved);
            }

            i += 1;
        }

        if (changed != null) {
            if (changed.size() != value.size())
                throw new ConfigException.BugOrBroken(
                        "substituted list's size doesn't match");
            return new SimpleConfigList(origin(), changed);
        } else {
            return this;
        }
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof SimpleConfigList;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof SimpleConfigList) {
            // optimization to avoid unwrapped() for two ConfigList
            return canEqual(other) && value.equals(((SimpleConfigList) other).value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return value.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(valueType().name());
        sb.append("(");
        for (ConfigValue e : value) {
            sb.append(e.toString());
            sb.append(",");
        }
        if (!value.isEmpty())
            sb.setLength(sb.length() - 1); // chop comma
        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean contains(Object o) {
        return value.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return value.containsAll(c);
    }

    @Override
    public ConfigValue get(int index) {
        return value.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return value.indexOf(o);
    }

    @Override
    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public Iterator<ConfigValue> iterator() {
        final Iterator<AbstractConfigValue> i = value.iterator();

        return new Iterator<ConfigValue>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public ConfigValue next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw weAreImmutable("iterator().remove");
            }
        };
    }

    @Override
    public int lastIndexOf(Object o) {
        return value.lastIndexOf(o);
    }

    private static ListIterator<ConfigValue> wrapListIterator(
            final ListIterator<AbstractConfigValue> i) {
        return new ListIterator<ConfigValue>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public ConfigValue next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw weAreImmutable("listIterator().remove");
            }

            @Override
            public void add(ConfigValue arg0) {
                throw weAreImmutable("listIterator().add");
            }

            @Override
            public boolean hasPrevious() {
                return i.hasPrevious();
            }

            @Override
            public int nextIndex() {
                return i.nextIndex();
            }

            @Override
            public ConfigValue previous() {
                return i.previous();
            }

            @Override
            public int previousIndex() {
                return i.previousIndex();
            }

            @Override
            public void set(ConfigValue arg0) {
                throw weAreImmutable("listIterator().set");
            }
        };
    }

    @Override
    public ListIterator<ConfigValue> listIterator() {
        return wrapListIterator(value.listIterator());
    }

    @Override
    public ListIterator<ConfigValue> listIterator(int index) {
        return wrapListIterator(value.listIterator(index));
    }

    @Override
    public int size() {
        return value.size();
    }

    @Override
    public List<ConfigValue> subList(int fromIndex, int toIndex) {
        List<ConfigValue> list = new ArrayList<ConfigValue>();
        // yay bloat caused by lack of type variance
        for (AbstractConfigValue v : value.subList(fromIndex, toIndex)) {
            list.add(v);
        }
        return list;
    }

    @Override
    public Object[] toArray() {
        return value.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return value.toArray(a);
    }

    private static UnsupportedOperationException weAreImmutable(String method) {
        return new UnsupportedOperationException(
                "ConfigList is immutable, you can't call List.'" + method + "'");
    }

    @Override
    public boolean add(ConfigValue e) {
        throw weAreImmutable("add");
    }

    @Override
    public void add(int index, ConfigValue element) {
        throw weAreImmutable("add");
    }

    @Override
    public boolean addAll(Collection<? extends ConfigValue> c) {
        throw weAreImmutable("addAll");
    }

    @Override
    public boolean addAll(int index, Collection<? extends ConfigValue> c) {
        throw weAreImmutable("addAll");
    }

    @Override
    public void clear() {
        throw weAreImmutable("clear");
    }

    @Override
    public boolean remove(Object o) {
        throw weAreImmutable("remove");
    }

    @Override
    public ConfigValue remove(int index) {
        throw weAreImmutable("remove");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw weAreImmutable("removeAll");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw weAreImmutable("retainAll");
    }

    @Override
    public ConfigValue set(int index, ConfigValue element) {
        throw weAreImmutable("set");
    }
}
