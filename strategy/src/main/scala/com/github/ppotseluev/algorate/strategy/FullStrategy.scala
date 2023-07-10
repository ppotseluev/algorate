package com.github.ppotseluev.algorate.strategy

import cats.implicits._
import com.github.ppotseluev.algorate.ExitBounds
import com.github.ppotseluev.algorate.OperationType
import org.ta4j.core.Indicator
import org.ta4j.core.Strategy
import org.ta4j.core.num.Num

import FullStrategy.{IndicatorInfo, TradeIdea}

class FullStrategy(
    longStrategy: Strategy,
    shortStrategy: Strategy,
    getPriceIndicators: () => Map[String, IndicatorInfo],
    val oscillators: Map[String, IndicatorInfo],
    stopIndicator: Indicator[(Num, Num)]
) {
  lazy val priceIndicators: Map[String, IndicatorInfo] = getPriceIndicators()

  def getLongStrategy = longStrategy
  def getShortStrategy = shortStrategy

  def recommendedTrade(index: Int): Option[TradeIdea] = {
    lazy val stops = stopIndicator.getValue(index).toList.map(_.doubleValue)
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
