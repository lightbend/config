package com.typesafe.config.impl

final class SubstitutionExpression(val path: Path, val optional: Boolean) {

    private[impl] def changePath(newPath: Path) =
        if (newPath eq path) this else new SubstitutionExpression(newPath, optional)

    override def toString: String =
        "${" + (if (optional) "?" else "") + path.render + "}"

    override def equals(other: Any): Boolean =
        if (other.isInstanceOf[SubstitutionExpression]) {
            val otherExp =
                other.asInstanceOf[SubstitutionExpression]
            otherExp.path == this.path && otherExp.optional == this.optional
        } else false

    override def hashCode: Int = {
        var h = 41 * (41 + path.hashCode)
        h = 41 * (h + (if (optional) 1 else 0))
        h
    }
}
