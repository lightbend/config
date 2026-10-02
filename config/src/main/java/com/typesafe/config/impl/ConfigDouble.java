/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigDecimal;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;

final class ConfigDouble extends ConfigNumber implements Serializable {

    private static final long serialVersionUID = 2L;

    final private double value;

    ConfigDouble(ConfigOrigin origin, double value, String originalText) {
        super(origin, originalText);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.NUMBER;
    }

    @Override
    public Double unwrapped() {
        return value;
    }

    @Override
    String transformToString() {
        String s = super.transformToString();
        if (s == null)
            return Double.toString(value);
        else
            return s;
    }

    @Override
    protected long longValue() {
        return (long) value;
    }

    // A Java double-to-long cast saturates on overflow and turns NaN into zero.
    @Override
    long longValueRangeChecked(String path) {
        if (Double.isNaN(value) || value >= 0x1.0p63 || value < -0x1.0p63
                || belowMinimumBeforeRounding()) {
            throw new ConfigException.WrongType(origin(), path, "64-bit integer",
                    "out-of-range value " + value);
        }
        return (long) value;
    }

    private boolean belowMinimumBeforeRounding() {
        if (value == -0x1.0p63 && originalText != null) {
            try {
                return new BigDecimal(originalText).compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0;
            } catch (NumberFormatException e) {
                // Double.parseDouble also accepts non-decimal spellings.
                return false;
            }
        }
        return false;
    }

    @Override
    protected double doubleValue() {
        return value;
    }

    @Override
    protected ConfigDouble newCopy(ConfigOrigin origin) {
        return new ConfigDouble(origin, value, originalText);
    }

    // serialization all goes through SerializedConfigValue
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }
}
