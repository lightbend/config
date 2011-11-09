package com.typesafe.config.impl;

/**
 * This is sort of a placeholder so that something per-config-load is passed in
 * to the parser to handle includes. The eventual idea is to let apps customize
 * how an included name gets searched for, which would involve some nicer
 * interface in the public API.
 */
interface IncludeHandler {
    AbstractConfigObject include(String name);
}
