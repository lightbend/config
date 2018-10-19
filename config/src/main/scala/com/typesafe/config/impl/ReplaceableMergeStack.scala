package com.typesafe.config.impl

/**
 * Implemented by a merge stack (ConfigDelayedMerge, ConfigDelayedMergeObject)
 * that replaces itself during substitution resolution in order to implement
 * "look backwards only" semantics.
 */
trait ReplaceableMergeStack extends Container {

    /**
     * Make a replacement for this object skipping the given number of elements
     * which are lower in merge priority.
     */
    def makeReplacement(
        context: ResolveContext,
        skipping: Int): AbstractConfigValue
}
