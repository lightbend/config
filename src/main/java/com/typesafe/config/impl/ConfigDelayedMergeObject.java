package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigRoot;
import com.typesafe.config.ConfigValue;

// This is just like ConfigDelayedMerge except we know statically
// that it will turn out to be an object.
class ConfigDelayedMergeObject extends AbstractConfigObject implements
        Unmergeable {

    final private List<AbstractConfigValue> stack;

    ConfigDelayedMergeObject(ConfigOrigin origin,
            ConfigTransformer transformer,
            List<AbstractConfigValue> stack) {
        super(origin, transformer);
        this.stack = stack;
        if (stack.isEmpty())
            throw new ConfigException.BugOrBroken(
                    "creating empty delayed merge object");
        if (!(stack.get(0) instanceof AbstractConfigObject))
            throw new ConfigException.BugOrBroken(
                    "created a delayed merge object not guaranteed to be an object");
    }

    ConfigDelayedMergeObject(ConfigOrigin origin,
            List<AbstractConfigValue> stack) {
        this(origin, ConfigImpl.defaultConfigTransformer(), stack);
    }

    final private static class Root extends ConfigDelayedMergeObject implements
            ConfigRoot {
        Root(ConfigDelayedMergeObject original) {
            super(original.origin(), original.stack);
        }

        @Override
        protected Root asRoot() {
            return this;
        }

        @Override
        public ConfigRoot resolve() {
            return resolve(this);
        }

        @Override
        public Root withFallback(ConfigValue value) {
            return super.withFallback(value).asRoot();
        }
    }

    @Override
    protected Root asRoot() {
        return new Root(this);
    }

    @Override
    public ConfigDelayedMergeObject newCopy(ConfigTransformer newTransformer,
            ResolveStatus status) {
        if (status != resolveStatus())
            throw new ConfigException.BugOrBroken(
                    "attempt to create resolved ConfigDelayedMergeObject");
        return new ConfigDelayedMergeObject(origin(), newTransformer, stack);
    }

    @Override
    AbstractConfigObject resolveSubstitutions(SubstitutionResolver resolver,
            int depth, boolean withFallbacks) {
        AbstractConfigValue merged = ConfigDelayedMerge.resolveSubstitutions(
                stack, resolver, depth,
                withFallbacks);
        if (merged instanceof AbstractConfigObject) {
            return (AbstractConfigObject) merged;
        } else {
            throw new ConfigException.BugOrBroken(
                    "somehow brokenly merged an object and didn't get an object");
        }
    }

    @Override
    ResolveStatus resolveStatus() {
        return ResolveStatus.UNRESOLVED;
    }

    @Override
    public ConfigDelayedMergeObject withFallback(ConfigValue other) {
        if (other instanceof AbstractConfigObject
                || other instanceof Unmergeable) {
            // since we are an object, and the fallback could be,
            // then a merge may be required; delay until we resolve.
            List<AbstractConfigValue> newStack = new ArrayList<AbstractConfigValue>();
            newStack.addAll(stack);
            if (other instanceof Unmergeable)
                newStack.addAll(((Unmergeable) other).unmergedValues());
            else
                newStack.add((AbstractConfigValue) other);
            return new ConfigDelayedMergeObject(
                    AbstractConfigObject.mergeOrigins(newStack),
                    newStack);
        } else {
            // if the other is not an object, there won't be anything
            // to merge with.
            return this;
        }
    }

    @Override
    public Collection<AbstractConfigValue> unmergedValues() {
        return stack;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigDelayedMergeObject;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigDelayedMergeObject) {
            return canEqual(other)
                    && this.stack
                            .equals(((ConfigDelayedMergeObject) other).stack);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return stack.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DELAYED_MERGE_OBJECT");
        sb.append("(");
        for (Object s : stack) {
            sb.append(s.toString());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1); // chop comma
        sb.append(")");
        return sb.toString();
    }

    private static ConfigException notResolved() {
        return new ConfigException.NotResolved(
                "bug: this object has not had substitutions resolved, so can't be used");
    }

    @Override
    public Map<String, Object> unwrapped() {
        throw notResolved();
    }

    @Override
    public boolean containsKey(Object key) {
        throw notResolved();
    }

    @Override
    public boolean containsValue(Object value) {
        throw notResolved();
    }

    @Override
    public Set<java.util.Map.Entry<String, ConfigValue>> entrySet() {
        throw notResolved();
    }

    @Override
    public boolean isEmpty() {
        throw notResolved();
    }

    @Override
    public Set<String> keySet() {
        throw notResolved();
    }

    @Override
    public int size() {
        throw notResolved();
    }

    @Override
    public Collection<ConfigValue> values() {
        throw notResolved();
    }

    @Override
    protected AbstractConfigValue peek(String key) {
        throw notResolved();
    }
}
