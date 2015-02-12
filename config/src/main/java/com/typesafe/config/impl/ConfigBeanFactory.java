package com.typesafe.config.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sent as pull request to config project.
 * https://github.com/typesafehub/config/pull/107 (original)
 * https://github.com/typesafehub/config/pull/249 (automatic conversion support)
 */
public class ConfigBeanFactory {

    public static <T> T create(Config config, Class<T> clazz) {
        return createInternal(config, clazz);
    }

    private static <T> T createInternal(Config config, Class<T> clazz) {
        Map<String, ?> configAsMap = config.root().unwrapped();
        Map<String, Object> configProps = new HashMap<String, Object>();
        Map<String,String> originalNames = new HashMap<String, String>();
        for (Map.Entry<String, ?> configProp : configAsMap.entrySet()) {
            configProps.put(toCamelCase(configProp.getKey()), configProp.getValue());
            originalNames.put(toCamelCase(configProp.getKey()),configProp.getKey());
        }

        try {
            T bean = clazz.newInstance();
            for (PropertyDescriptor beanProp : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
                if (beanProp.getReadMethod() == null || beanProp.getWriteMethod() == null) {
                    continue;
                }
                Method setter = beanProp.getWriteMethod();
                Object configValue = configProps.get(beanProp.getName());
                if (configValue == null) {
                    throw new ConfigException.Generic(
                            "Could not find " + beanProp.getName() + " from " + clazz.getName() + " in config.");
                }
                if (configValue instanceof Map) {
                    configValue = createInternal(config.getConfig(originalNames.get(beanProp.getDisplayName())), beanProp.getPropertyType());
                } else {
                    Class parameterClass = setter.getParameterTypes()[0];
                    configValue = getValueWithAutoConversion(parameterClass, config, originalNames.get(beanProp.getDisplayName()));
                }
                setter.invoke(bean, configValue);

            }
            return bean;
        } catch (IntrospectionException e) {
            throw new ConfigException.Generic("Could not resolve a string method name.", e);
        } catch (InstantiationException e) {
            throw new ConfigException.Generic(clazz + " needs a public no args constructor", e);
        } catch (IllegalAccessException e) {
            throw new ConfigException.Generic(clazz + " getters and setters are not accessible", e);
        } catch (InvocationTargetException e) {
            throw new ConfigException.Generic("Calling bean method caused exception", e);
        }
    }

    private static Object getValueWithAutoConversion(Class parameterClass, Config config, String configPropName) {
        if (parameterClass == Boolean.class || parameterClass == boolean.class) {
            return config.getBoolean(configPropName);
        } else if (parameterClass == Byte.class || parameterClass == byte.class) {
            return Integer.valueOf(config.getInt(configPropName)).byteValue();
        } else if (parameterClass == Short.class || parameterClass == short.class) {
            return Integer.valueOf(config.getInt(configPropName)).shortValue();
        } else if (parameterClass == Integer.class || parameterClass == int.class) {
            return config.getInt(configPropName);
        } else if (parameterClass == Double.class || parameterClass == double.class) {
            return config.getDouble(configPropName);
        } else if (parameterClass == Long.class || parameterClass == long.class) {
            String rawVal = config.getString(configPropName);
            if (DurationUnit.containsDurationToken(rawVal)) {
                return config.getDuration(configPropName, TimeUnit.NANOSECONDS);
            } else if (isByteValue(rawVal)) {
                return config.getBytes(configPropName);
            }
            return config.getLong(configPropName);
        } else if (parameterClass == String.class) {
            return config.getString(configPropName);
        }

        return config.getAnyRef(configPropName);
    }

    /**
     * Converts from hyphenated name to camel case.
     */
    public static String toCamelCase(String originalName) {
        String[] words = originalName.split("-+");
        StringBuilder nameBuilder = new StringBuilder(originalName.length());
        for (int i = 0; i < words.length; i++) {
            if (nameBuilder.length() == 0) {
                nameBuilder.append(words[i]);
            } else {
                nameBuilder.append(words[i].substring(0, 1).toUpperCase());
                nameBuilder.append(words[i].substring(1));
            }
        }
        return nameBuilder.toString();
    }

    public static boolean isByteValue(String rawVal) {
        for (String memoryUnitName : SimpleConfig.getMemoryUnitNameVariations()) {
            if(rawVal.contains(memoryUnitName)) {
                return true;
            }
        }
        return false;
    }
}
