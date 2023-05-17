package com.github.ppotseluev.algorate.strategy.indicator

import cats.implicits._
import com.github.ppotseluev.algorate.strategy.indicator.ChannelBreakIndicator.Break
import com.github.ppotseluev.algorate.strategy.indicator.ChannelBreakIndicator.BreakType
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator.Channel
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.num.Num

class ChannelBreakIndicator(
    priceIndicator: AbstractIndicator[Num],
    channelIndicator: AbstractIndicator[Option[Channel]]
) extends AbstractIndicator[Option[Break]](channelIndicator.getBarSeries) {
  override def getValue(index: Int): Option[Break] = {
    channelIndicator.getValue(index - 1).flatMap { channel =>
      Option.when(channelIndicator.getValue(index).isEmpty) {
        val (l, u) = channel.bounds.map(_.func.value(index)).tuple
        val p = priceIndicator.getValue(index).doubleValue
        val breakType =
          if (p > u) BreakType.Up
          else if (p < l) BreakType.Down
          else BreakType.Inner
        Break(channel, breakType)
      }
    }
  }
}

object ChannelBreakIndicator {
  case class Break(
      channel: Channel,
      breakType: BreakType
  )
  sealed trait BreakType
  object BreakType {
    case object Up extends BreakType
    case object Down extends BreakType
    case object Inner extends BreakType
  }
}
