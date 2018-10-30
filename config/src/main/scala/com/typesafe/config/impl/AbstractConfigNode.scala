/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.parser.ConfigNode
import java.{ util => ju }

abstract class AbstractConfigNode extends ConfigNode {

    protected[impl] def tokens(): ju.Collection[Token]

    override final def render: String = {
        val origText = new StringBuilder
        import scala.collection.JavaConverters._
        for (t <- tokens().asScala) {
            origText.append(t.tokenText)
        }
        origText.toString
    }

    override final def equals(other: Any): Boolean =
        other.isInstanceOf[AbstractConfigNode] && render == other
            .asInstanceOf[AbstractConfigNode]
            .render

    override final def hashCode: Int = render.hashCode
}
