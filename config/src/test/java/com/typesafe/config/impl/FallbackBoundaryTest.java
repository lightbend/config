package com.typesafe.config.impl;

import com.typesafe.config.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

public class FallbackBoundaryTest {
    private static Config included(String source) {
        AbstractConfigObject object = (AbstractConfigObject) ConfigFactory.parseString(source).root();
        return object.relativized(new Path("common")).toConfig().atKey("common");
    }

    @Test
    public void retryPreservesResolverCallsAndApplicationPrecedence() {
        final List<String> calls = new ArrayList<String>();
        ConfigResolver resolver = new ConfigResolver() {
            @Override
            public ConfigValue lookup(String path) {
                calls.add(path);
                return path.equals("common.x.y") ? ConfigValueFactory.fromAnyRef(7) : null;
            }

            @Override
            public ConfigResolver withFallback(ConfigResolver fallback) {
                return this;
            }
        };
        ConfigResolveOptions options = ConfigResolveOptions.noSystem().appendResolver(resolver);
        included("a=${?MISSING}").resolve(options);
        assertEquals("initially absent lookup calls the resolver only once",
                Arrays.asList("common.MISSING"), calls);

        calls.clear();
        Config source = included("x.y=${?MISSING}\na=${x.y}");
        Config resolved = source.resolve(options);
        assertEquals(7, resolved.getInt("common.a"));
        assertFalse("retry retains the original prefixed resolver path", calls.contains("x.y"));

        calls.clear();
        resolved = source.withFallback(ConfigFactory.parseString("x.y=5")).resolve(options);
        assertEquals(5, resolved.getInt("common.a"));
        assertFalse("application root precedes the resolver", calls.contains("common.x.y"));
    }

    @Test
    public void retryPreservesEnvironmentAndApplicationPrecedence() {
        String path = System.getenv("PATH");
        assumeNotNull(path);
        Config source = included("PATH=${?ABSENT_ENV_BOUNDARY_CONTROL}\na=${PATH}");
        assertEquals(path, source.resolve().getString("common.a"));
        Config resolved = source.withFallback(ConfigFactory.parseString("PATH=application")).resolve();
        assertEquals("application", resolved.getString("common.a"));
    }
}
