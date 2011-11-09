package com.typesafe.config.impl;

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
    /**
     * Because this interface is currently private, it uses AbstractConfigValue;
     * if public it would have to use plain ConfigValue.
     * 
     * @param value
     *            the value to potentially transform
     * @param requested
     *            the target type to transform to
     * @return a new value or the original value
     */
    AbstractConfigValue transform(AbstractConfigValue value,
            ConfigValueType requested);
}
