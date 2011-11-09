package com.typesafe.config.impl;


final class Substitution {
    private SubstitutionStyle style;
    private String reference;

    Substitution(String reference, SubstitutionStyle style) {
        this.style = style;
        this.reference = reference;
    }

    SubstitutionStyle style() {
        return style;
    }

    String reference() {
        return reference;
    }

    boolean isPath() {
        return style == SubstitutionStyle.PATH;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Substitution) {
            Substitution that = (Substitution) other;
            return this.reference.equals(that.reference)
                    && this.style == that.style;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 41 * (41 + reference.hashCode()) + style.hashCode();
    }

    @Override
    public String toString() {
        return "Substitution(" + reference + "," + style.name() + ")";
    }
}
