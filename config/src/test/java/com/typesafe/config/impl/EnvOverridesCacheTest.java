package com.typesafe.config.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class EnvOverridesCacheTest {
    private void runProbe(String mode, boolean malformed) throws Exception {
        File output = File.createTempFile("env-overrides-cache-", ".log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    new File(System.getProperty("java.home"), "bin/java").getPath(),
                    "-cp", System.getProperty("java.class.path"), Probe.class.getName(), mode);
            // Only the child gets these variables; keep inherited overrides out of the probe.
            Iterator<String> keys = builder.environment().keySet().iterator();
            while (keys.hasNext()) {
                if (keys.next().startsWith("CONFIG_FORCE_"))
                    keys.remove();
            }
            builder.environment().put("CONFIG_FORCE_cacheProbe_value", "17");
            if (malformed)
                builder.environment().put("CONFIG_FORCE_a____b", "bad");
            builder.redirectErrorStream(true).redirectOutput(output);
            process = builder.start();
            assertTrue("probe timed out: " + mode, process.waitFor(30, TimeUnit.SECONDS));
            String diagnostics = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
            assertEquals(mode + ": " + diagnostics, 0, process.exitValue());
        } finally {
            if (process != null && process.isAlive())
                process.destroyForcibly();
            Files.deleteIfExists(output.toPath());
        }
    }

    @Test public void disabledOverridesIgnoreMalformedNameDuringInvalidation() throws Exception {
        runProbe("disabled", true);
    }

    @Test public void explicitReadFailureDoesNotPoisonCacheHolder() throws Exception {
        runProbe("explicit", true);
    }

    @Test public void enabledOverridesKeepReportingBadPath() throws Exception {
        runProbe("enabled", true);
    }

    @Test public void validOverridesAreCachedAndReloadedAfterInvalidation() throws Exception {
        runProbe("valid", false);
    }

    public static class Probe {
        private static void expectBadPath(boolean enabled) {
            try {
                if (enabled)
                    ConfigFactory.load(ConfigFactory.parseString("cacheProbe.value=3"));
                else
                    ConfigFactory.systemEnvironmentOverrides();
                fail("expected malformed override to be rejected");
            } catch (ConfigException.BadPath expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("CONFIG_FORCE_a____b"));
            }
        }

        public static void main(String[] args) {
            System.setProperty("config.override_with_env_vars", "false");
            String mode = args[0];
            if (mode.equals("disabled")) {
                for (int i = 0; i < 3; i++) {
                    ConfigFactory.invalidateCaches();
                    assertFalse(ConfigFactory.defaultOverrides().hasPath("cacheProbe.value"));
                    assertEquals(3, ConfigFactory.load(ConfigFactory.parseString("cacheProbe.value=3"))
                            .getInt("cacheProbe.value"));
                }
            } else if (mode.equals("explicit") || mode.equals("enabled")) {
                boolean enabled = mode.equals("enabled");
                if (enabled)
                    System.setProperty("config.override_with_env_vars", "true");
                // The first operation initializes the holder on the broken implementation.
                expectBadPath(enabled);
                expectBadPath(enabled);
                ConfigFactory.invalidateCaches();
                expectBadPath(enabled);
                System.setProperty("config.override_with_env_vars", "false");
                ConfigFactory.invalidateCaches();
                assertEquals(3, ConfigFactory.load(ConfigFactory.parseString("cacheProbe.value=3"))
                        .getInt("cacheProbe.value"));
            } else if (mode.equals("valid")) {
                Config first = ConfigFactory.systemEnvironmentOverrides();
                assertEquals(17, first.getInt("cacheProbe.value"));
                assertSame(first, ConfigFactory.systemEnvironmentOverrides());
                ConfigFactory.invalidateCaches();
                Config reloaded = ConfigFactory.systemEnvironmentOverrides();
                assertNotSame(first, reloaded);
                assertEquals(first, reloaded);
                System.setProperty("config.override_with_env_vars", "true");
                assertEquals(17, ConfigFactory.load(ConfigFactory.parseString("cacheProbe.value=3"))
                        .getInt("cacheProbe.value"));
            } else {
                fail("unknown probe mode: " + mode);
            }
        }
    }
}
