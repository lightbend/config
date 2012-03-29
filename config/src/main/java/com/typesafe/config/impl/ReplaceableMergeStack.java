package com.typesafe.config.impl;

/**
 * Implemented by a merge stack (ConfigDelayedMerge, ConfigDelayedMergeObject)
 * that replaces itself during substitution resolution in order to implement
 * "look backwards only" semantics.
 */
interface ReplaceableMergeStack {
    /**
     * Make a replacer for this object, skipping the given number of items in
     * the stack.
     */
    ResolveReplacer makeReplacer(int skipping);
}
