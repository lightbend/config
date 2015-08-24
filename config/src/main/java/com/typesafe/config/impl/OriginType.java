package com.twitter_typesafe.config.impl;

// caution: ordinals used in serialization
enum OriginType {
    GENERIC,
    FILE,
    URL,
    RESOURCE
}
