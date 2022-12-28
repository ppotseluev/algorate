package com.github.ppotseluev.algorate.strategy.indicator

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator

import ChannelIndicator.Channel
import ChannelIndicator.Section

class VisualChannelIndicator(channelIndicator: Indicator[Option[Channel]])
    extends CachedIndicator[Option[Channel]](channelIndicator) {

  private val channels: Seq[(Int, Channel)] =
    (0 to getBarSeries.getEndIndex).flatMap { i =>
      channelIndicator.getValue(i).map(i -> _)
    }

  private val approximations = channels.map(_._2.lowerBoundApproximation).toSet
  log.debug(s"${approximations.size} channels found")

  override protected def calculate(index: Int): Option[Channel] = {
    channels
      .collectFirst { case (ind, channel) if ind >= index => channel }
      .filter { c =>
        c.upperBoundApproximation.points.head.x <= index ||
        c.lowerBoundApproximation.points.head.x <= index
      }
      .map { c =>
        c.copy(
          section = Section(
            lowerBound = numOf(c.lowerBoundApproximation.func.value(index)),
            upperBound = numOf(c.upperBoundApproximation.func.value(index))
          )
        )
      }
  }
}
