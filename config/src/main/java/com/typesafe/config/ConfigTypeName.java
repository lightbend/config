package com.typesafe.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used for binding logical name that the annotated class has.
 */
@Documented
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigTypeName {

    /**
     * Logical type name for annotated type. If missing (or defined as Empty
     * String), defaults to using non-qualified class name as the type.
     */
    String value() default "";
}
