package com.typesafe.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used for configuring details of if and how type information is
 * used with conversion to Java class, to preserve information about actual
 * class of Object instances. This is necessarily for polymorphic types, and may
 * also be needed to link abstract declared types and matching concrete
 * implementation.
 */
@Documented
@Target({
    ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD,
    ElementType.METHOD, ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigTypeInfo {

    /**
     * Property name used when for type inclusion method.
     *
     * If POJO itself has a property with same name, value of property will be
     * set with type id metadata: if no such property exists, type id is only
     * used for determining actual type.
     */
    String property() default "type";

    /**
     * Optional property that can be used to specify default implementation
     * class to use for deserialization if type identifier is either not
     * present, or can not be mapped to a registered type (which can occur for
     * ids, but not when specifying explicit class to use). Property is only
     * used in deciding what to do for otherwise unmappable cases.
     *
     * Note that while this property allows specification of the default
     * implementation to use, it does not help with structural issues that may
     * arise if type information is missing. This means that most often this is
     * used with type-name -based resolution, to cover cases where new sub-types
     * are added, but base type is not changed to reference new sub-types.
     *
     * There are certain special values that indicate alternate behavior:
     * <ul>
     *     <li>
     *         {@link java.lang.Void} means that objects with unmappable (or
     *         missing) type are to be mapped to null references.
     *     </li>
     *     <li>
     *         Placeholder value of {@link ConfigTypeInfo} (that is, this
     *         annotation type itself} means "there is no default implementation"
     *         (in which case an error results from unmappable type).
     *     </li>
     * </ul>
     */
    Class<?> defaultImpl() default ConfigTypeInfo.class;

    /**
     * Property that defines whether type identifier value will be passed as
     * part of config to deserializer (true), or handled and removed by {@link
     * ConfigBeanFactory} (false).
     *
     * Default value is false, meaning that Config handles and removes the type
     * identifier from config that is passed to {@link ConfigBeanFactory}.
     */
    boolean visible() default false;
}
