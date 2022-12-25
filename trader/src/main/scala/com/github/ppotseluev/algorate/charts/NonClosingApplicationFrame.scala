package com.github.ppotseluev.algorate.charts

import java.awt.event.WindowEvent

/**
 * Ignores window closing event (unlike the parent [[ApplicationFrame]] which shuts down JVM)
 */
class NonClosingApplicationFrame(title: String) extends ApplicationFrame(title) {
  override def windowClosing(event: WindowEvent): Unit = ()
}
