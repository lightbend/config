package com.typesafe.config.impl

import java.{ util => ju }

final class ConfigNodeArray private[impl] (
    children: ju.Collection[AbstractConfigNode]) extends ConfigNodeComplexValue(children) {
    override def newNode(nodes: ju.Collection[AbstractConfigNode]) =
        new ConfigNodeArray(nodes)
}
