package com.github.ppotseluev.algorate.trader

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

trait LoggingSupport {

  protected def getLogger(componentName: String): Logger =
    Logger(LoggerFactory.getLogger(componentName))
}
