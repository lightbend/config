package com.typesafe.config.impl;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * A ConfigTransformer converts values in the config to other values, most often
 * it's used to parse strings and treat them as some other kind of value.
 * 
 * This was originally in the public API but I'm now thinking it is not useful
 * to customize, so it's not public for now. It's still used internally, but
 * probably the code could be cleaned up by just hard-coding the equivalent of
 * the DefaultTransformer.
 */
interface ConfigTransformer {
    ConfigValue transform(ConfigValue value, ConfigValueType requested);
}
