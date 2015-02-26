package com.typesafe.config;

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
import com.typesafe.config.impl.ConfigImplUtil;

/**
 * Factory for automatic creation of config classes populated with values from config.
 *
 * Example usage:
 *
 * Config configSource = ConfigFactory.parseReader(new InputStreamReader("converters.conf"));
 * ConverterConfig config = ConfigBeanFactory.create(configSource,ConverterConfig.class);
 *
 * Supports nested configs.
 * Supports automatic types conversion
 * (https://github.com/typesafehub/config/blob/master/HOCON.md#automatic-type-conversions).
 */
public class ConfigBeanFactory {

    /**
     * Creates instance of class containing configuration info from config source
     * @param config - source of config information
     * @param clazz - class to be created
     * @param <T>
     * @return - instance of config class populated with data from config source
     */
    public static <T> T create(Config config, Class<T> clazz) {
        return createInternal(config, clazz);
    }

    private static <T> T createInternal(Config config, Class<T> clazz) {
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
        } catch(IntrospectionException e) {
            throw new ConfigException.Generic("Could not get bean information for class " + clazz.getName(), e);
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
                    throw new ConfigException.Generic(
                            "Could not find property '" + beanProp.getName() + "' from class '" + clazz.getName() + "' in config.");
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
