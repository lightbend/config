package com.typesafe.config.impl

/**
 * This exists because we have to memoize resolved substitutions as we go
 * through the config tree; otherwise we could end up creating multiple copies
 * of values or whole trees of values as we follow chains of substitutions.
 */
final class ResolveMemos private ( // note that we can resolve things to undefined (represented as Java null,
    // rather than ConfigNull) so this map can have null values.
    val memos: BadMap[MemoKey, AbstractConfigValue]) {
    def this() = this(new BadMap[MemoKey, AbstractConfigValue])

    private[impl] def get(key: MemoKey): AbstractConfigValue = memos.get(key)

    private[impl] def put(key: MemoKey, value: AbstractConfigValue) =
        new ResolveMemos(memos.copyingPut(key, value))
}
