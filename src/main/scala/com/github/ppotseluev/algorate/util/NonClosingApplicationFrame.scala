package com.github.ppotseluev.algorate.util

import java.awt.event.WindowEvent
import org.jfree.ui.ApplicationFrame

/**
 * Ignores window closing event (unlike the parent [[ApplicationFrame]] which shuts down JVM)
 */
class NonClosingApplicationFrame(title: String) extends ApplicationFrame(title) {
  override def windowClosing(event: WindowEvent): Unit = ()
}
