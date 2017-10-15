package com.typesafe.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used with {@link ConfigTypeInfo} to indicate sub-types of
 * serializable polymorphic types, and to associate logical names used within
 * config (which is more portable than using physical Java class names).
 *
 * Note that just annotating a property or base type with this annotation does
 * NOT enable polymorphic type handling: in addition, {@link ConfigTypeInfo} or
 * equivalent (such as enabling of so-called "default typing") annotation is
 * needed, and only in such case is subtype information used.
 */
@Documented
@Target({
    ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD,
    ElementType.METHOD, ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigSubTypes {

    /**
     * Subtypes of the annotated type (annotated class, or property value type
     * associated with the annotated method). These will be checked recursively
     * so that types can be defined by only including direct subtypes.
     */
    Type[] value();

    /**
     * Definition of a subtype, along with optional name. If name is missing,
     * class of the type will be checked for {@link ConfigTypeName} annotation;
     * and if that is also missing or empty, a default name will be constructed.
     * Default name is usually based on class name.
     */
    @interface Type {

        /**
         * Class of the subtype.
         */
        Class<?> value();

        /**
         * Logical type name used as the type identifier for the class.
         */
        String name() default "";
    }
}
