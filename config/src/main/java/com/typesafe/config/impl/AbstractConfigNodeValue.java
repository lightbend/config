package com.typesafe.config.impl;

// This is gross. We currently have a class that doesn't do anything, but is needed
// to distinguish certain types of nodes from other types. This is required if we want
// to be referencing the AbstractConfigNode class in implementation rather than the
// ConfigNode interface, as we can't cast an AbstractConfigNode to an interface
abstract class AbstractConfigNodeValue extends AbstractConfigNode {

}
