package com.github.ppotseluev.algorate.strategy

import cats.implicits._
import com.github.ppotseluev.algorate.ExitBounds
import com.github.ppotseluev.algorate.OperationType
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator.Channel
import org.ta4j.core.Indicator
import org.ta4j.core.Strategy
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.num.Num

import FullStrategy.{IndicatorInfo, TradeIdea}

class FullStrategy(
    longStrategy: Strategy,
    shortStrategy: Strategy,
    getPriceIndicators: () => Map[String, IndicatorInfo],
    val oscillators: Map[String, IndicatorInfo],
    stopIndicator: Indicator[(Num, Num)],
    val channelIndicator: AbstractIndicator[Option[Channel]] //TODO
) {
  lazy val priceIndicators: Map[String, IndicatorInfo] = getPriceIndicators()

  def getLongStrategy = longStrategy
  def getShortStrategy = shortStrategy

  def recommendedTrade(index: Int): Option[TradeIdea] = {
    lazy val (s1, s2) = stopIndicator.getValue(index)
    lazy val stops = List(s1, s2).map(_.doubleValue)
    if (longStrategy.shouldEnter(index)) {
      TradeIdea(
        operationType = OperationType.Buy,
        exitBounds = ExitBounds(
          takeProfit = stops.max,
          stopLoss = stops.min
        )
      ).some
    } else if (shortStrategy.shouldEnter(index)) {
      TradeIdea(
        operationType = OperationType.Sell,
        exitBounds = ExitBounds(
          takeProfit = stops.min,
          stopLoss = stops.max
        )
      ).some
    } else {
      none
    }
  }
}

object FullStrategy {
  case class TradeIdea(
      operationType: OperationType,
      exitBounds: ExitBounds
  )

  sealed trait Representation

  object Representation {
    case object Line extends Representation
    case object Points extends Representation
  }

  case class IndicatorInfo(
      indicator: Indicator[Num],
      representation: Representation = Representation.Line
  )
}
