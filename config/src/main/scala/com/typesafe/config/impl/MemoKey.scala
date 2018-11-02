package com.typesafe.config.impl

/** The key used to memoize already-traversed nodes when resolving substitutions */
final class MemoKey private[impl] (val value: AbstractConfigValue, val restrictToChildOrNull: Path) {

    override final def hashCode(): Int = {
        val h = System.identityHashCode(value)
        if (restrictToChildOrNull != null)
            h + 41 * (41 + restrictToChildOrNull.hashCode)
        else h
    }

    override final def equals(other: Any): Boolean =
        if (other.isInstanceOf[MemoKey]) {
            val o = other.asInstanceOf[MemoKey]
            if (o.value ne this.value) false
            else if (o.restrictToChildOrNull eq this.restrictToChildOrNull) true
            else if (o.restrictToChildOrNull == null || this.restrictToChildOrNull == null)
                false
            else o.restrictToChildOrNull == this.restrictToChildOrNull
        } else false

    override final def toString(): String =
        "MemoKey(" + value + "@" + System.identityHashCode(value) + "," + restrictToChildOrNull + ")"
}
