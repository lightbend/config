package com.typesafe.config.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
import com.typesafe.config.ConfigOrigin;

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

        // now try to load a resource for each extension
        addResource(configConfig.rootPath() + ".conf", stack);
        addResource(configConfig.rootPath() + ".json", stack);
        addResource(configConfig.rootPath() + ".properties", stack);

        ConfigTransformer transformer = withExtraTransformer(null);

        AbstractConfigObject merged = AbstractConfigObject
                .merge(new SimpleConfigOrigin("config for "
                        + configConfig.rootPath()), stack, transformer);

        AbstractConfigValue resolved = SubstitutionResolver.resolve(merged,
                merged);

        return (AbstractConfigObject) resolved;
    }

    private static void addResource(String name,
            List<AbstractConfigObject> stack) {
        URL url = ConfigImpl.class.getResource("/" + name);
        if (url != null) {
            stack.add(loadURL(url));
        }
    }

    static ConfigObject getEnvironmentAsConfig() {
        // This should not need to create a new config object
        // as long as the transformer is just the default transformer.
        return AbstractConfigObject.transformed(envVariablesConfig(),
                withExtraTransformer(null));
    }

    static ConfigObject getSystemPropertiesAsConfig() {
        // This should not need to create a new config object
        // as long as the transformer is just the default transformer.
        return AbstractConfigObject.transformed(systemPropertiesConfig(),
                withExtraTransformer(null));
    }

    static AbstractConfigObject loadURL(URL url) {
        if (url.getPath().endsWith(".properties")) {
            ConfigOrigin origin = new SimpleConfigOrigin(url.toExternalForm());
            Properties props = new Properties();
            InputStream stream = null;
            try {
                stream = url.openStream();
                props.load(stream);
            } catch (IOException e) {
                throw new ConfigException.IO(origin, "failed to open url", e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
            }
            return fromProperties(url.toExternalForm(), props);
        } else {
            return forceParsedToObject(Parser.parse(url));
        }
    }

    static AbstractConfigObject forceParsedToObject(AbstractConfigValue value) {
        if (value instanceof AbstractConfigObject) {
            return (AbstractConfigObject) value;
        } else {
            throw new ConfigException.WrongType(value.origin(), "",
                    "object at file root", value.valueType().name());
        }
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
        Map<String, Map<String, AbstractConfigValue>> scopes = new HashMap<String, Map<String, AbstractConfigValue>>();
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
                    Map<String, AbstractConfigValue> scope = scopes
                            .get(exceptLast);
                    if (scope == null) {
                        scope = new HashMap<String, AbstractConfigValue>();
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

            Map<String, AbstractConfigValue> parent = scopes.get(parentPath);
            if (parent == null) {
                parent = new HashMap<String, AbstractConfigValue>();
                scopes.put(parentPath, parent);
            }
            // NOTE: we are evil and cheating, we mutate the map we
            // provide to SimpleConfigObject, which is not allowed by
            // its contract, but since we know nobody has a ref to this
            // SimpleConfigObject yet we can get away with it.
            AbstractConfigObject o = new SimpleConfigObject(
                    new SimpleConfigOrigin(
                    originPrefix + " " + path), null, scopes.get(path));
            String basename = ConfigUtil.lastElement(path);
            parent.put(basename, o);
        }

        Map<String, AbstractConfigValue> root = scopes.get("");
        if (root == null) {
            // this would happen only if you had no properties at all
            // in "props"
            root = Collections.<String, AbstractConfigValue> emptyMap();
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
        Map<String, AbstractConfigValue> m = new HashMap<String, AbstractConfigValue>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            m.put(key, new ConfigString(
                    new SimpleConfigOrigin("env var " + key), entry.getValue()));
        }
        return new SimpleConfigObject(new SimpleConfigOrigin("env variables"),
                defaultConfigTransformer(), m);
    }
}
