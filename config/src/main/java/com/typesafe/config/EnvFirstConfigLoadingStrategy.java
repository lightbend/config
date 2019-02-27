package com.typesafe.config;

import java.util.Map;
import java.util.HashMap;

/**
 * Environment variables first config loading strategy. Able to load environment variables first and fallback to {@link DefaultConfigLoadingStrategy}
 * 
 * <p>
 * Environment variables are mangled in the following way after stripping the prefix:
 * <table border="1">
 * <tr>
 *     <th bgcolor="silver">Env Var</th>
 *     <th bgcolor="silver">Config</th>
 * </tr>
 * <tr>
 *     <td>_&nbsp;&nbsp;&nbsp;[1 underscore]</td>
 *     <td>. [dot]</td>
 * </tr>
 * <tr>
 *     <td>__&nbsp;&nbsp;[2 underscore]</td>
 *     <td>- [dash]</td>
 *  </tr>
 * <tr>
 *     <td>___&nbsp;[3 underscore]</td>
 *     <td>_ [underscore]</td>
 * </tr>
 * </table>
 * 
 * <p>
 * A variable like: {@code CONFIG_a_b__c___d}
 * is translated to a config key: {@code a.b-c_d}
 * 
 * <p>
 * The prefix may be altered by defining the VM property {@code config.env_var_prefix}
 */
public class EnvFirstConfigLoadingStrategy extends DefaultConfigLoadingStrategy {

    protected static Map<String, String> env = new HashMap(System.getenv());

    @Override
    public Config parseApplicationConfig(ConfigParseOptions parseOptions) {
        String envVarPrefix = System.getProperty("config.env_var_prefix");
        if (envVarPrefix == null) // fallback to default
            envVarPrefix = "CONFIG_";

        Map<String, String> defaultsFromEnv = new HashMap();
        for (String key : env.keySet()) {
            if (key.startsWith(envVarPrefix)) {
                StringBuilder builder = new StringBuilder();

                String strippedPrefix = key.substring(envVarPrefix.length(), key.length());

                int underscores = 0;
                for (char c : strippedPrefix.toCharArray()) {
                    if (c == '_') {
                        underscores++;
                    } else {
                        switch (underscores) {
                            case 1: builder.append('.');
                                    break;
                            case 2: builder.append('-');
                                    break;
                            case 3: builder.append('_');
                                    break;
                        }
                        underscores = 0;
                        builder.append(c);
                    }
                }

                String propertyKey = builder.toString();
                defaultsFromEnv.put(propertyKey, env.get(key));
            }
        }

        return ConfigFactory.parseMap(defaultsFromEnv).withFallback(super.parseApplicationConfig(parseOptions));
    }
}
