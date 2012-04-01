/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

/**
 * ConfigSubstitution is now a shim for back-compat with serialization. i.e. an
 * old version may unserialize one of these. We now split into ConfigReference
 * and ConfigConcatenation.
 */
@Deprecated
final class ConfigSubstitution extends AbstractConfigValue implements
        Unmergeable {

    private static final long serialVersionUID = 1L;

    // this is a list of String and SubstitutionExpression where the
    // SubstitutionExpression has to be resolved to values, then if there's more
    // than one piece everything is stringified and concatenated
    final private List<Object> pieces;

    // this is just here to avoid breaking serialization
    @SuppressWarnings("unused")
    @Deprecated
    final private int prefixLength = 0;

    // this is just here to avoid breaking serialization
    @SuppressWarnings("unused")
    @Deprecated
    final private boolean ignoresFallbacks = false;

    // we chain the ConfigSubstitution back-compat stub to a new value
    private transient AbstractConfigValue delegate = null;

    private void createDelegate() {
        if (delegate != null)
            throw new ConfigException.BugOrBroken("creating delegate twice: " + this);

        List<AbstractConfigValue> values = ConfigConcatenation.valuesFromPieces(origin(), pieces);

        if (values.size() == 1) {
            delegate = values.get(0);
        } else {
            delegate = new ConfigConcatenation(origin(), values);
        }

        if (!(delegate instanceof Unmergeable))
            throw new ConfigException.BugOrBroken("delegate must be Unmergeable: " + this
                    + " delegate was: " + delegate);
    }

    AbstractConfigValue delegate() {
        if (delegate == null)
            throw new NullPointerException("failed to create delegate " + this);
        return delegate;
    }

    ConfigSubstitution(ConfigOrigin origin, List<Object> pieces) {
        super(origin);
        this.pieces = pieces;

        createDelegate();
    }

    @Override
    public ConfigValueType valueType() {
        return delegate().valueType();
    }

    @Override
    public Object unwrapped() {
        return delegate().unwrapped();
    }

    @Override
    protected AbstractConfigValue newCopy(boolean ignoresFallbacks, ConfigOrigin newOrigin) {
        return delegate().newCopy(ignoresFallbacks, newOrigin);
    }

    @Override
    protected boolean ignoresFallbacks() {
        return delegate().ignoresFallbacks();
    }

    @Override
    protected AbstractConfigValue mergedWithTheUnmergeable(Unmergeable fallback) {
        return delegate().mergedWithTheUnmergeable(fallback);
    }

    @Override
    protected AbstractConfigValue mergedWithObject(AbstractConfigObject fallback) {
        return delegate().mergedWithObject(fallback);
    }

    @Override
    protected AbstractConfigValue mergedWithNonObject(AbstractConfigValue fallback) {
        return delegate().mergedWithNonObject(fallback);
    }

    @Override
    public Collection<? extends AbstractConfigValue> unmergedValues() {
        return ((Unmergeable) delegate()).unmergedValues();
    }

    @Override
    AbstractConfigValue resolveSubstitutions(ResolveContext context) throws NotPossibleToResolve {
        return context.resolve(delegate());
    }

    @Override
    ResolveStatus resolveStatus() {
        return delegate().resolveStatus();
    }

    @Override
    AbstractConfigValue relativized(Path prefix) {
        return delegate().relativized(prefix);
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigSubstitution || other instanceof ConfigReference
                || other instanceof ConfigConcatenation;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigSubstitution) {
            return canEqual(other)
                    && this.pieces.equals(((ConfigSubstitution) other).pieces);
        } else {
            return delegate().equals(other);
        }
    }

    @Override
    public int hashCode() {
        return delegate().hashCode();
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean formatted) {
        delegate().render(sb, indent, formatted);
    }

    // This ridiculous hack is because some JDK versions apparently can't
    // serialize an array, which is used to implement ArrayList and EmptyList.
    // maybe
    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6446627
    private Object writeReplace() throws ObjectStreamException {
        // switch to LinkedList
        return new ConfigSubstitution(origin(), new java.util.LinkedList<Object>(pieces));
    }

    // generate the delegate when we deserialize to avoid thread safety
    // issues later
    private void readObject(java.io.ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
        createDelegate();
    }

    // this is a little cleaner but just causes compat issues probably
    // private Object readResolve() throws ObjectStreamException {
        // replace ourselves on deserialize
    // return delegate();
    // }
}
