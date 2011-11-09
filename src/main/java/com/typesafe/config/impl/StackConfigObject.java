package com.typesafe.config.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;

/**
 * This is unused for now, decided that it was too annoying to "lazy merge" and
 * better to do the full merge up-front.
 */
final class StackConfigObject extends AbstractConfigObject {

    private List<AbstractConfigObject> stack;

    /**
     * The stack is ordered from highest to lowest priority. So the start of the
     * list is the top of the search path.
     */
    StackConfigObject(ConfigOrigin origin, ConfigTransformer transformer,
            List<AbstractConfigObject> stack) {
        super(origin, transformer);
        this.stack = stack;
    }

    @Override
    public boolean containsKey(String key) {
        for (AbstractConfigObject o : stack) {
            if (o.containsKey(key))
                return true;
        }
        return false;
    }

    @Override
    public Set<String> keySet() {
        if (stack.isEmpty()) {
            return Collections.emptySet();
        } else if (stack.size() == 1) {
            return stack.get(0).keySet();
        } else {
            Set<String> combined = new HashSet<String>();
            for (AbstractConfigObject o : stack) {
                combined.addAll(o.keySet());
            }
            return combined;
        }
    }

    @Override
    public Object unwrapped() {
        Map<String, Object> m = new HashMap<String, Object>();
        for (AbstractConfigObject o : stack) {
            for (String k : o.keySet()) {
                m.put(k, o.peek(k).unwrapped());
            }
        }
        return m;
    }

    @Override
    protected ConfigValue peek(String key) {
        for (AbstractConfigObject o : stack) {
            // Important: A ConfigNull value would override
            // and keep us from returning a later non-null value.
            ConfigValue v = o.peek(key);
            if (v != null)
                return v;
        }
        return null;
    }
}
