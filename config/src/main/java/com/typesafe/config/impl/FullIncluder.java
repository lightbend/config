/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

import com.twitter_typesafe.config.ConfigIncluder;
import com.twitter_typesafe.config.ConfigIncluderClasspath;
import com.twitter_typesafe.config.ConfigIncluderFile;
import com.twitter_typesafe.config.ConfigIncluderURL;

interface FullIncluder extends ConfigIncluder, ConfigIncluderFile, ConfigIncluderURL,
            ConfigIncluderClasspath {

}
