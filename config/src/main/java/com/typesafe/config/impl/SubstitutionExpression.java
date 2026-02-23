package com.typesafe.config.impl;

final class SubstitutionExpression {

    final private Path path;
    final private boolean optional;
    final private boolean listExpansion;

    SubstitutionExpression(Path path, boolean optional) {
        this(path, optional, false);
    }

    SubstitutionExpression(Path path, boolean optional, boolean listExpansion) {
        this.path = path;
        this.optional = optional;
        this.listExpansion = listExpansion;
    }

    Path path() {
        return path;
    }

    boolean optional() {
        return optional;
    }

    boolean listExpansion() {
        return listExpansion;
    }

    SubstitutionExpression changePath(Path newPath) {
        if (newPath == path)
            return this;
        else
            return new SubstitutionExpression(newPath, optional, listExpansion);
    }

    SubstitutionExpression changeListExpansion(boolean newListExpansion) {
        if (newListExpansion == listExpansion)
            return this;
        else
            return new SubstitutionExpression(path, optional, newListExpansion);
    }

    @Override
    public String toString() {
        return "${" + (optional ? "?" : "") + path.render() + (listExpansion ? "[]" : "") + "}";
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SubstitutionExpression) {
            SubstitutionExpression otherExp = (SubstitutionExpression) other;
            return otherExp.path.equals(this.path) && otherExp.optional == this.optional
                    && otherExp.listExpansion == this.listExpansion;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int h = 41 * (41 + path.hashCode());
        h = 41 * (h + (optional ? 1 : 0));
        h = 41 * (h + (listExpansion ? 1 : 0));
        return h;
    }
}
