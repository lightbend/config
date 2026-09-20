package com.typesafe.config.impl;

import com.typesafe.config.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class IncludeOptionalFallbackTest {
    private static Config resolve(String included, String main) throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("include-fallback");
        java.nio.file.Path child = dir.resolve("child.conf");
        java.nio.file.Path root = dir.resolve("main.conf");
        try {
            Files.write(child, included.getBytes(StandardCharsets.UTF_8));
            Files.write(root, main.getBytes(StandardCharsets.UTF_8));
            return ConfigFactory.parseFile(root.toFile()).resolve(ConfigResolveOptions.noSystem());
        } finally {
            Files.deleteIfExists(root);
            Files.deleteIfExists(child);
            Files.deleteIfExists(dir);
        }
    }
    private static void value(String included, String main, Object expected) throws Exception {
        Object actual = resolve(included, main).getValue("common.a").unwrapped();
        if (!expected.equals(actual)) throw new AssertionError(actual + " != " + expected);
    }
    @Test
    public void absentIncludedLeafRetriesRoot() throws Exception {
        String main = "common {include \"child.conf\"}\nx.y=0\ncopy=${common.a}\n";
        value("x.y=${?MISSING}\na=${x.y}\n", main, 0);
        value("x.y=${?PRESENT}\na=${x.y}\n", main + "PRESENT=7\n", 7);
        value("x.y=9\nx.y=${?MISSING}\na=${x.y}\n", main, 9);
        value("x.y=${?MISSING}\na=${?x.y}\n", main, 0);
        try {
            resolve("x.y=${REQUIRED}\na=${x.y}\n", main);
            throw new AssertionError("required missing succeeded");
        } catch (ConfigException.UnresolvedSubstitution expected) {
        }
        try {
            resolve("x.y=${?MISSING}\na=${x.y}\n", "common {include \"child.conf\"}\n");
            throw new AssertionError("missing root succeeded");
        } catch (ConfigException.UnresolvedSubstitution expected) {
        }
    }

    @Test
    public void optionalAliasNamesDoNotChangeFallback() throws Exception {
        String main = "common {include \"child.conf\"}\nx.y=0\ncopy=${common.a}\n";
        for (String alias : new String[] {"other", "zzz"}) {
            for (boolean present : new boolean[] {false, true}) {
                String aliases = "common.x=${obj}\nobj=${" + alias + "}\n"
                        + alias + ".y=${?MISSING}\n" + (present ? "MISSING=7\n" : "");
                value("a=${x.y}\n", main + aliases, present ? 7 : 0);
            }
        }
        try {
            resolve("a=${x.y}\n", main + "common.x=${obj}\nobj=${zzz}\nzzz.y=${REQUIRED}\n");
            throw new AssertionError("required alias leaf succeeded");
        } catch (ConfigException.UnresolvedSubstitution expected) {
        }
        try {
            resolve("a=${x.y}\n", main + "common.x=${obj}\nobj=${zzz}\nzzz=${obj}\n");
            throw new AssertionError("genuine alias cycle succeeded");
        } catch (ConfigException.UnresolvedSubstitution expected) {
        }

    }
}
