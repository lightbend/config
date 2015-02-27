package com.typesafe.config.impl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

public class ConfigBeanImpl {

    /**
     * This is public ONLY for use by the "config" package, DO NOT USE this ABI
     * may change.
     */
    public static <T> T createInternal(Config config, Class<T> clazz) {
        if (((SimpleConfig)config).root().resolveStatus() != ResolveStatus.RESOLVED)
            throw new ConfigException.NotResolved(
                    "need to Config#resolve() a config before using it to initialize a bean, see the API docs for Config#resolve()");

        Map<String, AbstractConfigValue> configProps = new HashMap<String, AbstractConfigValue>();
        Map<String, String> originalNames = new HashMap<String, String>();
        for (Map.Entry<String, ConfigValue> configProp : config.root().entrySet()) {
            String originalName = configProp.getKey();
            String camelName = ConfigImplUtil.toCamelCase(originalName);
            // if a setting is in there both as some hyphen name and the camel name,
            // the camel one wins
            if (originalNames.containsKey(camelName) && originalName != camelName) {
                // if we aren't a camel name to start with, we lose.
                // if we are or we are the first matching key, we win.
            } else {
                configProps.put(camelName, (AbstractConfigValue) configProp.getValue());
                originalNames.put(camelName, originalName);
            }
        }

        BeanInfo beanInfo = null;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            throw new ConfigException.BadBean("Could not get bean information for class " + clazz.getName(), e);
        }

        try {
            List<PropertyDescriptor> beanProps = new ArrayList<PropertyDescriptor>();
            for (PropertyDescriptor beanProp : beanInfo.getPropertyDescriptors()) {
                if (beanProp.getReadMethod() == null || beanProp.getWriteMethod() == null) {
                    continue;
                }
                beanProps.add(beanProp);
            }

            // Try to throw all validation issues at once (this does not comprehensively
            // find every issue, but it should find common ones).
            List<ConfigException.ValidationProblem> problems = new ArrayList<ConfigException.ValidationProblem>();
            for (PropertyDescriptor beanProp : beanProps) {
                Method setter = beanProp.getWriteMethod();
                Class parameterClass = setter.getParameterTypes()[0];
                ConfigValueType expectedType = getValueTypeOrNull(parameterClass);
                if (expectedType != null) {
                    String name = originalNames.get(beanProp.getName());
                    if (name == null)
                        name = beanProp.getName();
                    Path path = Path.newKey(name);
                    AbstractConfigValue configValue = configProps.get(beanProp.getName());
                    if (configValue != null) {
                        SimpleConfig.checkValid(path, expectedType, configValue, problems);
                    } else {
                        SimpleConfig.addMissing(problems, expectedType, path, config.origin());
                    }
                }
            }

            if (!problems.isEmpty()) {
                throw new ConfigException.ValidationFailed(problems);
            }

            // Fill in the bean instance
            T bean = clazz.newInstance();
            for (PropertyDescriptor beanProp : beanProps) {
                Method setter = beanProp.getWriteMethod();
                ConfigValue configValue = configProps.get(beanProp.getName());
                Object unwrapped;
                if (configValue == null) {
                    throw new ConfigException.Missing(beanProp.getName());
                }
                if (configValue instanceof SimpleConfigObject) {
                    unwrapped = createInternal(config.getConfig(originalNames.get(beanProp.getDisplayName())), beanProp.getPropertyType());
                } else {
                    Class parameterClass = setter.getParameterTypes()[0];
                    unwrapped = getValueWithAutoConversion(parameterClass, config, originalNames.get(beanProp.getDisplayName()));
                }
                setter.invoke(bean, unwrapped);
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

    // null if we can't easily say
    private static ConfigValueType getValueTypeOrNull(Class<?> parameterClass) {
        if (parameterClass == Boolean.class || parameterClass == boolean.class) {
            return ConfigValueType.BOOLEAN;
        } else if (parameterClass == Integer.class || parameterClass == int.class) {
            return ConfigValueType.NUMBER;
        } else if (parameterClass == Double.class || parameterClass == double.class) {
            return ConfigValueType.NUMBER;
        } else if (parameterClass == Long.class || parameterClass == long.class) {
            return ConfigValueType.NUMBER;
        } else if (parameterClass == String.class) {
            return ConfigValueType.STRING;
        } else if (parameterClass == Duration.class) {
            return null;
        } else if (parameterClass == ConfigMemorySize.class) {
            return null;
        } else if (parameterClass.isAssignableFrom(List.class)) {
            return ConfigValueType.LIST;
        } else if (parameterClass.isAssignableFrom(Map.class)) {
            return ConfigValueType.OBJECT;
        } else {
            return null;
        }
    }
}
