package com.typesafe.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows a config property to be {@code null}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Optional {

}
