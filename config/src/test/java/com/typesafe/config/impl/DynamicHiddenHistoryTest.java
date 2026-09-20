package com.typesafe.config.impl;

import com.typesafe.config.*;
import org.junit.Test;

public final class DynamicHiddenHistoryTest {
    private static Config resolve(String source) {
        return ConfigFactory.parseString(source).resolve(
                ConfigResolveOptions.defaults().setUseSystemEnvironment(false));
    }

    @Test
    public void doesNotResolveHiddenDynamicHistory() {
        for (String replacement : new String[] { "42", "null", "\"text\"", "[1,2]" }) {
            Config c = resolve("wrapper { a=${MISSING} }\nreplacement=" + replacement
                    + "\nwrapper=${replacement}");
            if (!c.root().get("wrapper").equals(c.root().get("replacement")))
                throw new AssertionError(c.root().render());
        }
        Config c = resolve("wrapper { a=${MISSING} }\nreplacement=42\nwrapper=${replacement}suffix");
        if (!c.getString("wrapper").equals("42suffix")) throw new AssertionError(c.root().render());
        for (String replacement : new String[] { "{}", "${?MISSING}" }) {
            try {
                resolve("wrapper { a=${MISSING} }\nwrapper=" + replacement);
                throw new AssertionError("required old object field must still fail");
            } catch (ConfigException.UnresolvedSubstitution expected) {
            }
        }
        resolve("wrapper { a=${MISSING} }\nwrapper=42");
        Config hiddenList = resolve("wrapper=[${MISSING}]\nreplacement={}\nwrapper=${replacement}");
        if (!hiddenList.getObject("wrapper").isEmpty()) throw new AssertionError(hiddenList.root().render());
        Config merged = resolve("wrapper={a=1}\nreplacement={b=2}\nwrapper=${replacement}");
        if (merged.getInt("wrapper.a") != 1 || merged.getInt("wrapper.b") != 2)
            throw new AssertionError(merged.root().render());

    }
}
