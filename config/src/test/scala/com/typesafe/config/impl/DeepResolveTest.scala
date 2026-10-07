package com.typesafe.config.impl

import org.junit._
import org.junit.Assert._

import com.typesafe.config.{ConfigException, ConfigFactory}

// Many += lines on one key overflow the stack during resolve and
// escape as an Error; JVM-only because the overflow point depends on the
// thread stack size
class DeepResolveTest {
  private def plusEqualsLines(n: Int): String =
    (1 to n).map(i => s"modules += m$i").mkString("\n")

  @Test def manyPlusEqualsLinesFailToResolveWithConfigException(): Unit = {
    // The JVM raises a request this small to its minimum stack size, which
    // overflows from 40 to 100 lines on JDK 8, 17 and 25, cold or warmed up
    // (a 256 KiB stack needed over 1000 lines once the JIT had compiled the
    // resolver). 500 keeps a margin; resolving += lines is quadratic, so a
    // larger count makes the test as slow as the runner, not as deep.
    val conf = ConfigFactory.parseString(plusEqualsLines(500))
    val failure = new java.util.concurrent.atomic.AtomicReference[Throwable]()
    val thread = new Thread(
      null,
      new Runnable {
        override def run(): Unit =
          try {
            conf.resolve()
          } catch {
            case t: Throwable => failure.set(t)
          }
      },
      "deep-config-resolve",
      64 * 1024L
    )
    thread.setDaemon(true)
    thread.start()
    thread.join(30000)
    assertFalse("resolve did not finish", thread.isAlive)
    val e = failure.get()
    assertTrue(
      "expected ConfigException.Parse, got " + e,
      e.isInstanceOf[ConfigException.Parse]
    )
    assertTrue(e.getMessage.contains("stack overflow"))
    assertTrue(e.getCause.isInstanceOf[StackOverflowError])
  }

  @Test def fewPlusEqualsLinesStillResolve(): Unit = {
    val conf = ConfigFactory.parseString(plusEqualsLines(20)).resolve()
    assertEquals(20, conf.getList("modules").size())
  }
}
