package com.github.ppotseluev.algorate.strategy.indicator

import com.github.ppotseluev.algorate.strategy.indicator.HasCrossRule.Direction
import org.ta4j.core.Indicator
import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.helpers.ConstantIndicator
import org.ta4j.core.num.Num
import org.ta4j.core.rules.AbstractRule
import org.ta4j.core.rules.CrossedDownIndicatorRule
import org.ta4j.core.rules.CrossedUpIndicatorRule

class HasCrossRule(
    direction: Direction,
    bars: Int,
    numberOfCross: Int = 1,
    offset: Int = 0
)(
    first: Indicator[Num],
    second: Indicator[Num]
) extends AbstractRule {

  private val rule = direction match {
    case HasCrossRule.Up   => new CrossedUpIndicatorRule(first, second)
    case HasCrossRule.Down => new CrossedDownIndicatorRule(first, second)
    case HasCrossRule.AnyDirection =>
      new CrossedUpIndicatorRule(first, second) or new CrossedDownIndicatorRule(first, second)
  }

  override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
    Iterator.from(index - offset, -1).take(bars).count { i =>
      rule.isSatisfied(i, tradingRecord)
    } >= numberOfCross
}

object HasCrossRule {
  def apply(direction: Direction, bars: Int)(
      first: Indicator[Num],
      second: Double,
      numberOfCross: Int = 1,
      offset: Int = 0
  ) = new HasCrossRule(direction, bars, numberOfCross = numberOfCross, offset = offset)(
    first,
    new ConstantIndicator(first.getBarSeries, first.numOf(second))
  )

  sealed trait Direction
  case object Up extends Direction
  case object Down extends Direction
  case object AnyDirection extends Direction
}
