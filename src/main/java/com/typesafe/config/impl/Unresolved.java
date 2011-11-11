package com.typesafe.config.impl;

import java.util.Collection;

/**
 * Interface that tags a ConfigValue that is inherently not resolved; i.e. a
 * subclass of ConfigValue that will not appear in a resolved tree of values.
 * Types such as ConfigObject may not be resolved _yet_, but they are not
 * inherently unresolved.
 */
interface Unresolved {
    Collection<? extends AbstractConfigValue> unmergedValues();
}
