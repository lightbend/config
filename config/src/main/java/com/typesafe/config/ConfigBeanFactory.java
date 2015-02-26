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
 * Factory for automatically creating a Java class from a {@link Config}.
 *
 * Example usage:
 *
 * <pre>
 * Config configSource = ConfigFactory.load().getConfig("foo");
 * FooConfig config = ConfigBeanFactory.create(configSource, FooConfig.class);
 * </pre>
 *
 * The Java class should follow JavaBean conventions. Field types
 * can be any of the types you can normally get from a {@link
 * Config}, including <code>java.time.Duration</code> or {@link
 * ConfigMemorySize}. Fields may also be another JavaBean-style
 * class.
 *
 * Fields are mapped to config by converting the config key to
 * camel case.  So the key <code>foo-bar</code> becomes JavaBean
 * setter <code>setFooBar</code>.
 */
public class ConfigBeanFactory {

    /**
     * Creates an instance of a class, initializing its fields from a {@link Config}.
     * @param config source of config information
     * @param clazz class to be instantiated
     * @param <T> the type of the class to be instantiated
     * @return an instance of the class populated with data from the config
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
