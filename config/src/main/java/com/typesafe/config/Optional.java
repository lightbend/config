package com.typesafe.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows an config property to be {@code null}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface Optional {

}
