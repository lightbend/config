/**
 *   Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config

import org.junit.Assert._
import org.junit._

class BeanFactoryTest {

    @Test
    def toCamelCase() {
        assertEquals("configProp", BeanFactory.toCamelCase("config-prop"))
        assertEquals("fooBar", BeanFactory.toCamelCase("foo-----bar"))
        assertEquals("foo", BeanFactory.toCamelCase("-foo"))
        assertEquals("bar", BeanFactory.toCamelCase("bar-"))
    }

}
