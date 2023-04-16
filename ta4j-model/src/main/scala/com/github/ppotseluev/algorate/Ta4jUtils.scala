package com.github.ppotseluev.algorate

import org.ta4j.core.BarSeries

object Ta4jUtils {
  implicit class BarSeriesOps(val barSeries: BarSeries) extends AnyVal {
    def getStartIndex: Int = scala.math.max(barSeries.getRemovedBarsCount, barSeries.getBeginIndex)
  }
}
