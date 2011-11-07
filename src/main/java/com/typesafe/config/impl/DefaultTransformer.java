package com.typesafe.config.impl;

import com.typesafe.config.ConfigTransformer;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

class DefaultTransformer implements ConfigTransformer {

    @Override
    public ConfigValue transform(ConfigValue value, ConfigValueType requested) {
        if (value.valueType() == ConfigValueType.STRING) {
            String s = (String) value.unwrapped();
            switch (requested) {
            case NUMBER:
                try {
                    Long v = Long.parseLong(s);
                    return new ConfigLong(value.origin(), v);
                } catch (NumberFormatException e) {
                    // try Double
                }
                try {
                    Double v = Double.parseDouble(s);
                    return new ConfigDouble(value.origin(), v);
                } catch (NumberFormatException e) {
                    // oh well.
                }
                break;
            case NULL:
                if (s.equals("null"))
                    return new ConfigNull(value.origin());
                break;
            case BOOLEAN:
                if (s.equals("true") || s.equals("yes")) {
                    return new ConfigBoolean(value.origin(), true);
                } else if (s.equals("false") || s.equals("no")) {
                    return new ConfigBoolean(value.origin(), false);
                }
                break;
            }
        } else if (requested == ConfigValueType.STRING) {
            switch (value.valueType()) {
            case NUMBER: // FALL THROUGH
            case BOOLEAN:
                return new ConfigString(value.origin(), value.unwrapped()
                        .toString());
            }
        }

        return value;
    }

}
