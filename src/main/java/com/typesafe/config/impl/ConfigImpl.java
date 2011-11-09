package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.typesafe.config.ConfigConfig;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;

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

        // this is a conceptual placeholder for a customizable
        // object that the app might be able to pass in.
        IncludeHandler includer = defaultIncluder();

        stack.add(includer.include(configConfig.rootPath()));

        ConfigTransformer transformer = withExtraTransformer(null);

        AbstractConfigObject merged = AbstractConfigObject
                .merge(new SimpleConfigOrigin("config for "
                        + configConfig.rootPath()), stack, transformer);

        AbstractConfigValue resolved = SubstitutionResolver.resolve(merged,
                merged);

        return (AbstractConfigObject) resolved;
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

    synchronized static ConfigTransformer defaultConfigTransformer() {
        if (defaultTransformer == null) {
            defaultTransformer = new DefaultTransformer();
        }
        return defaultTransformer;
    }

    private static IncludeHandler defaultIncluder = null;

    synchronized static IncludeHandler defaultIncluder() {
        if (defaultIncluder == null) {
            defaultIncluder = new IncludeHandler() {

                @Override
                public AbstractConfigObject include(String name) {
                    return Loader.load(name, this);
                }
            };
        }
        return defaultIncluder;
    }

    private static AbstractConfigObject systemProperties = null;

    synchronized static AbstractConfigObject systemPropertiesConfig() {
        if (systemProperties == null) {
            systemProperties = loadSystemProperties();
        }
        return systemProperties;
    }

    private static AbstractConfigObject loadSystemProperties() {
        return Loader.fromProperties("system property", System.getProperties());
    }

    // this is a hack to let us set system props in the test suite
    synchronized static void dropSystemPropertiesConfig() {
        systemProperties = null;
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
