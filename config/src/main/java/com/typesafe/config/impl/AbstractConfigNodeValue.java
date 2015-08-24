/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.twitter_typesafe.config.impl;

// This is required if we want
// to be referencing the AbstractConfigNode class in implementation rather than the
// ConfigNode interface, as we can't cast an AbstractConfigNode to an interface
abstract class AbstractConfigNodeValue extends AbstractConfigNode {

}
