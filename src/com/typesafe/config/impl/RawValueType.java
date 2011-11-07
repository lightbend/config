package com.typesafe.config.impl;

import com.typesafe.config.ConfigValueType;

enum RawValueType {
    OBJECT(ConfigValueType.OBJECT),

    LIST(ConfigValueType.LIST),

    NUMBER(ConfigValueType.NUMBER),

    BOOLEAN(ConfigValueType.BOOLEAN),

    NULL(ConfigValueType.NULL),

    STRING(ConfigValueType.STRING),

    SUBSTITUTION(null),

    INCLUDE(null);

    ConfigValueType cooked;

    RawValueType(ConfigValueType cooked) {
        this.cooked = cooked;
    }
}
