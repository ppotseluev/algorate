package com.github.ppotseluev.algorate.strategy.indicator

import com.github.ppotseluev.algorate.strategy.indicator.HasCrossRule.Direction
import org.ta4j.core.indicators.helpers.ConstantIndicator
import org.ta4j.core.num.Num
import org.ta4j.core.{Indicator, TradingRecord}
import org.ta4j.core.rules.{AbstractRule, CrossedDownIndicatorRule, CrossedUpIndicatorRule}

class HasCrossRule(direction: Direction, bars: Int)(
    first: Indicator[Num],
    second: Indicator[Num]
) extends AbstractRule {

  private val rule = direction match {
    case HasCrossRule.Up   => new CrossedUpIndicatorRule(first, second)
    case HasCrossRule.Down => new CrossedDownIndicatorRule(first, second)
  }

  override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
    Iterator.from(index, -1).take(bars).exists { i =>
      rule.isSatisfied(i, tradingRecord)
    }
}

object HasCrossRule {
  def apply(direction: Direction, bars: Int)(
      first: Indicator[Num],
      second: Double
  ) = new HasCrossRule(direction, bars)(
    first,
    new ConstantIndicator(first.getBarSeries, first.numOf(second))
  )

  sealed trait Direction
  case object Up extends Direction
  case object Down extends Direction
}
