package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.typesafe.config.ConfigConfig;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigTransformer;
import com.typesafe.config.ConfigValue;

/** This is public but is only supposed to be used by the "config" package */
public class ConfigImpl {
    public static ConfigObject loadConfig(ConfigConfig configConfig) {
        AbstractConfigObject system = null;
        try {
            system = systemPropertiesConfig()
                    .getObject(configConfig.rootPath());
        } catch (ConfigException e) {
            // no system props in the requested root path
        }
        List<AbstractConfigObject> stack = new ArrayList<AbstractConfigObject>();

        // higher-priority configs are first
        if (system != null)
            stack.add(system);

        ConfigTransformer transformer = withExtraTransformer(configConfig
                .extraTransformer());

        AbstractConfigObject merged = AbstractConfigObject
                .merge(new SimpleConfigOrigin("config for "
                        + configConfig.rootPath()), stack, transformer);

        return merged;
    }

    public static ConfigObject getEnvironmentAsConfig(
            ConfigTransformer extraTransformer) {
        // This should not need to create a new config object
        // as long as the transformer is just the default transformer.
        return AbstractConfigObject.transformed(envVariablesConfig(),
                withExtraTransformer(extraTransformer));
    }

    public static ConfigObject getSystemPropertiesAsConfig(
            ConfigTransformer extraTransformer) {
        // This should not need to create a new config object
        // as long as the transformer is just the default transformer.
        return AbstractConfigObject.transformed(systemPropertiesConfig(),
                withExtraTransformer(extraTransformer));
    }

    private static ConfigTransformer withExtraTransformer(
            ConfigTransformer extraTransformer) {
        // idea is to avoid creating a new, unique transformer if there's no
        // extraTransformer
        if (extraTransformer != null) {
            List<ConfigTransformer> transformerStack = new ArrayList<ConfigTransformer>();
            transformerStack.add(defaultConfigTransformer());
            transformerStack.add(extraTransformer);
            return new StackTransformer(transformerStack);
        } else {
            return defaultConfigTransformer();
        }
    }

    private static ConfigTransformer defaultTransformer = null;

    private synchronized static ConfigTransformer defaultConfigTransformer() {
        if (defaultTransformer == null) {
            defaultTransformer = new DefaultTransformer();
        }
        return defaultTransformer;
    }

    private static AbstractConfigObject systemProperties = null;

    synchronized static AbstractConfigObject systemPropertiesConfig() {
        if (systemProperties == null) {
            systemProperties = loadSystemProperties();
        }
        return systemProperties;
    }

    private static AbstractConfigObject loadSystemProperties() {
        return fromProperties("system property", System.getProperties());

    }

    // this is a hack to let us set system props in the test suite
    synchronized static void dropSystemPropertiesConfig() {
        systemProperties = null;
    }

    private static AbstractConfigObject fromProperties(String originPrefix,
            Properties props) {
        Map<String, Map<String, ConfigValue>> scopes = new HashMap<String, Map<String, ConfigValue>>();
        Enumeration<?> i = props.propertyNames();
        while (i.hasMoreElements()) {
            Object o = i.nextElement();
            if (o instanceof String) {
                try {
                    String path = (String) o;
                    String last = ConfigUtil.lastElement(path);
                    String exceptLast = ConfigUtil.exceptLastElement(path);
                    if (exceptLast == null)
                        exceptLast = "";
                    Map<String, ConfigValue> scope = scopes.get(exceptLast);
                    if (scope == null) {
                        scope = new HashMap<String, ConfigValue>();
                        scopes.put(exceptLast, scope);
                    }
                    String value = props.getProperty(path);
                    scope.put(last, new ConfigString(new SimpleConfigOrigin(
                            originPrefix + " " + path), value));
                } catch (ConfigException.BadPath e) {
                    // just skip this one (log it?)
                }
            }
        }

        // pull out the list of objects that go inside other objects
        List<String> childPaths = new ArrayList<String>();
        for (String path : scopes.keySet()) {
            if (path != "")
                childPaths.add(path);
        }

        // put everything in its parent, ensuring all parents exist
        for (String path : childPaths) {
            String parentPath = ConfigUtil.exceptLastElement(path);
            if (parentPath == null)
                parentPath = "";

            Map<String, ConfigValue> parent = scopes.get(parentPath);
            if (parent == null) {
                parent = new HashMap<String, ConfigValue>();
                scopes.put(parentPath, parent);
            }
            // NOTE: we are evil and cheating, we mutate the map we
            // provide to SimpleConfigObject, which is not allowed by
            // its contract, but since we know nobody has a ref to this
            // SimpleConfigObject yet we can get away with it.
            ConfigObject o = new SimpleConfigObject(new SimpleConfigOrigin(
                    originPrefix + " " + path), null, scopes.get(path));
            String basename = ConfigUtil.lastElement(path);
            parent.put(basename, o);
        }

        Map<String, ConfigValue> root = scopes.get("");
        if (root == null) {
            // this would happen only if you had no properties at all
            // in "props"
            root = Collections.<String, ConfigValue> emptyMap();
        }

        // return root config object
        return new SimpleConfigObject(new SimpleConfigOrigin(originPrefix),
                null, root);
    }

    private static AbstractConfigObject envVariables = null;

    synchronized static AbstractConfigObject envVariablesConfig() {
        if (envVariables == null) {
            envVariables = loadEnvVariables();
        }
        return envVariables;
    }

    private static AbstractConfigObject loadEnvVariables() {
        Map<String, String> env = System.getenv();
        Map<String, ConfigValue> m = new HashMap<String, ConfigValue>();
        for (String key : env.keySet()) {
            m.put(key, new ConfigString(
                    new SimpleConfigOrigin("env var " + key), env.get(key)));
        }
        return new SimpleConfigObject(new SimpleConfigOrigin("env variables"),
                defaultConfigTransformer(), m);
    }
}
