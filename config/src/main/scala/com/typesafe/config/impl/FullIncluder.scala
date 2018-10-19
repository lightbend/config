/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigIncluder
import com.typesafe.config.ConfigIncluderClasspath
import com.typesafe.config.ConfigIncluderFile
import com.typesafe.config.ConfigIncluderURL

trait FullIncluder extends ConfigIncluder
    with ConfigIncluderFile
    with ConfigIncluderURL
    with ConfigIncluderClasspath {}
