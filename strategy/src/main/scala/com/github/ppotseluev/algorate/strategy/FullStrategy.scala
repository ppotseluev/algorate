package com.github.ppotseluev.algorate.strategy

import org.ta4j.core.Indicator
import org.ta4j.core.Strategy
import org.ta4j.core.num.Num

import FullStrategy.IndicatorInfo

case class FullStrategy(
    longStrategy: Strategy,
    shortStrategy: Strategy,
    getPriceIndicators: () => Map[String, IndicatorInfo],
    oscillators: Map[String, IndicatorInfo]
) {
  lazy val priceIndicators: Map[String, IndicatorInfo] = getPriceIndicators()
}

object FullStrategy {
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
