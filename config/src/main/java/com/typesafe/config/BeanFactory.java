package com.typesafe.config;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * Sent as pull request to config project.
 * https://github.com/typesafehub/config/pull/107
 */
public class BeanFactory {

  public static <T> T create(Config config, Class<T> clazz) {
    return create(config.root().unwrapped(), clazz);
  }

  private static <T> T create(Map<String,?> config, Class<T> clazz) {    
    Map<String, Object> configProps = new HashMap<String, Object>();
    for (Map.Entry<String, ?> configProp : config.entrySet()) {
      configProps.put(toCamelCase(configProp.getKey()), configProp.getValue());
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
          @SuppressWarnings("unchecked")
          Map<String,?> child = ((Map<String,?>) configValue);
          configValue = create(child, beanProp.getPropertyType());
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

  /**
   * Converts from hyphenated name to camel case.
   */
  protected static String toCamelCase(String originalName) {
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

}
