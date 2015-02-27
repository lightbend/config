package com.typesafe.config.impl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigMemorySize;

public class ConfigBeanImpl {

    /**
     * This is public ONLY for use by the "config" package, DO NOT USE this ABI
     * may change.
     */
    public static <T> T createInternal(Config config, Class<T> clazz) {
        Map<String, ?> configAsMap = config.root().unwrapped();
        Map<String, Object> configProps = new HashMap<String, Object>();
        Map<String,String> originalNames = new HashMap<String, String>();
        for (Map.Entry<String, ?> configProp : configAsMap.entrySet()) {
            configProps.put(ConfigImplUtil.toCamelCase(configProp.getKey()), configProp.getValue());
            originalNames.put(ConfigImplUtil.toCamelCase(configProp.getKey()),configProp.getKey());
        }

        BeanInfo beanInfo = null;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            throw new ConfigException.BadBean("Could not get bean information for class " + clazz.getName(), e);
        }

        try {
            T bean = clazz.newInstance();
            for (PropertyDescriptor beanProp : beanInfo.getPropertyDescriptors()) {
                if (beanProp.getReadMethod() == null || beanProp.getWriteMethod() == null) {
                    continue;
                }
                Method setter = beanProp.getWriteMethod();
                Object configValue = configProps.get(beanProp.getName());
                if (configValue == null) {
                    throw new ConfigException.Missing(beanProp.getName());
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
        } catch (InstantiationException e) {
            throw new ConfigException.BadBean(clazz.getName() + " needs a public no-args constructor to be used as a bean", e);
        } catch (IllegalAccessException e) {
            throw new ConfigException.BadBean(clazz.getName() + " getters and setters are not accessible, they must be for use as a bean", e);
        } catch (InvocationTargetException e) {
            throw new ConfigException.BadBean("Calling bean method on " + clazz.getName() + " caused an exception", e);
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
            return config.getLong(configPropName);
        } else if (parameterClass == String.class) {
            return config.getString(configPropName);
        } else if (parameterClass == Duration.class) {
            return config.getDuration(configPropName);
        } else if (parameterClass == ConfigMemorySize.class) {
            return config.getMemorySize(configPropName);
        }

        return config.getAnyRef(configPropName);
    }
}
