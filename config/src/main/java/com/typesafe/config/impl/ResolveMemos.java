package com.typesafe.config.impl;

/**
 * This exists because we have to memoize resolved substitutions as we go
 * through the config tree; otherwise we could end up creating multiple copies
 * of values or whole trees of values as we follow chains of substitutions.
 */
final class ResolveMemos {
    // note that we can resolve things to undefined (represented as Java null,
    // rather than ConfigNull) so this map can have null values.
    final private BadMap<MemoKey, AbstractConfigValue> memos;

    private ResolveMemos(BadMap<MemoKey, AbstractConfigValue> memos) {
        this.memos = memos;
    }

    ResolveMemos() {
        this(new BadMap<>());
    }

    AbstractConfigValue get(MemoKey key) {
        return memos.get(key);
    }

    ResolveMemos put(MemoKey key, AbstractConfigValue value) {
        return new ResolveMemos(memos.copyingPut(key, value));
    }
}
