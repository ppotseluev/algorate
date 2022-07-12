package com.github.ppotseluev.algorate.ta4j

import com.github.ppotseluev.algorate.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBar
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.{Bar => Ta4jBar}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

object Utils {
  def convertBar(bar: Bar): Ta4jBar = {
    val Bar(openPrice, closePrice, lowPrice, highPrice, volume, endTime, duration) = bar
    BaseBar
      .builder[BigDecimal](d => DoubleNum.valueOf(d.toFloat), null)
      .openPrice(openPrice)
      .closePrice(closePrice)
      .lowPrice(lowPrice)
      .highPrice(highPrice)
      .volume(volume)
      .timePeriod(duration.toJava)
      .endTime(endTime.toZonedDateTime)
      .build
  }

  def buildBarSeries(
      name: String,
      bars: Seq[Bar]
  ): BarSeries = {
    new BaseBarSeriesBuilder()
      .withName(name)
      .withBars(ArraySeq.from(bars.map(convertBar)).asJava)
      .build
  }
}
