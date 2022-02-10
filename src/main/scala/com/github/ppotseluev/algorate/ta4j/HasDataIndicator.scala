package com.github.ppotseluev.algorate.ta4j

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.AbstractIndicator

import java.time.ZonedDateTime
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class HasDataIndicator(
    period: Int,
    barSeries: BarSeries
) extends AbstractIndicator[Boolean](barSeries) {
  override def getValue(index: Int): Boolean = {
    val start = index - period
    if (start < 0) {
      false
    } else {
      val bars = barSeries.getSubSeries(start, index).getBarData.asScala
      val res = bars.sliding(2).forall { x =>
        require(x.size == 2)
        val a = x.head
        val b = x.tail.head
        (b.getBeginTime.toEpochSecond - a.getEndTime.toEpochSecond).seconds <= 1.minute
      }
      res
    }
  }
}
