/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigInteger;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

final class ConfigBigInteger extends ConfigNumber implements Serializable {

    private static final long serialVersionUID = 2L;

    final private BigInteger value;

    ConfigBigInteger(ConfigOrigin origin, BigInteger value, String originalText) {
        super(origin, originalText);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.NUMBER;
    }

    @Override
    public BigInteger unwrapped() {
        return value;
    }

    @Override
    String transformToString() {
        String s = super.transformToString();
        if (s == null)
            return value.toString();
        else
            return s;
    }

    @Override
    protected long longValue() {
        return value.longValue();
    }

    @Override
    protected double doubleValue() {
        return value.doubleValue();
    }

    @Override
    protected ConfigBigInteger newCopy(ConfigOrigin origin) {
        return new ConfigBigInteger(origin, value, originalText);
    }

    // serialization all goes through SerializedConfigValue
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }
}
