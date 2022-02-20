package com.github.ppotseluev.algorate.ta4j.indicator

import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.{Channel, Section}
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator

import scala.util.Try

class VisualChannelIndicator(channelIndicator: Indicator[Option[Channel]])
    extends CachedIndicator[Option[Channel]](channelIndicator) {

  override def calculate(index: Int): Option[Channel] = {
    val channel = Try {
      LazyList
        .from(index)
        .takeWhile(_ < getBarSeries.getBarCount)
        .flatMap(channelIndicator.getValue)
        .headOption
    }.recover { case _: IndexOutOfBoundsException =>
      None
    }.toOption
      .flatten
    channel
      .filter { c =>
        c.uppperBoundApproximation.points.head.getX <= index ||
        c.lowerBoundApproximation.points.head.getX <= index
      }
      .map { c =>
        c.copy(
          section = Section(
            lowerBound = numOf(c.lowerBoundApproximation.func.value(index)),
            upperBound = numOf(c.uppperBoundApproximation.func.value(index))
          )
        )
      }
  }
}
