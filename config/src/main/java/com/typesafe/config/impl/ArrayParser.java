/**
 *   Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.Properties;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;

final class ArrayParser {

    static AbstractConfigObject fromArray(ConfigOrigin origin, String[] args) {
        Properties props = new Properties();
        int i = 0;
        while (i < args.length) {
            String key = args[i];

            // Check parameter correctness
            if (isParameter(key)) {
                key = key.substring(1, key.length());
            } else {
                throw new ConfigException.Parse(origin, "Parameter " + key + " must start with '-'");
            }

            // Check if last parameter is a boolean parameter
            if ((i + 1) == args.length) {
                props.setProperty(key, Boolean.TRUE.toString());
                break;
            }

            String value = args[i + 1];
            // Check if boolean parameter
            if (isParameter(value)) {
                value = Boolean.TRUE.toString();
                i -= 1;
            }

            props.setProperty(key, value);
            // next parameter pair
            i += 2;
        }

        // check if last parameter was skipped
        if ((i - 1) == args.length) {
            String key = args[i - 2];

            // Check parameter correctness
            if (isParameter(key)) {
                key = key.substring(1, key.length());
            } else {
                throw new ConfigException.Parse(origin, "Parameter " + key + " must start with '-'");
            }
            props.setProperty(key, Boolean.TRUE.toString());
        }

        return PropertiesParser.fromProperties(origin, props);
    }

    static boolean isParameter(String key) {
        return key.startsWith("-");
    }

}
