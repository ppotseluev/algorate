package com.github.ppotseluev.algorate.strategy.indicator

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.num.Num

class LessThanIndicator(first: Indicator[Num], second: Indicator[Num], forLastBars: Int)
    extends AbstractIndicator[Boolean](first.getBarSeries) {

  override def getValue(index: Int): Boolean = {
    Range(index, index - forLastBars, step = -1).forall { i =>
      first.getValue(i).isLessThan(second.getValue(i))
    }
  }
}

class GreaterThanIndicator(first: Indicator[Num], second: Indicator[Num], forLastBars: Int)
    extends AbstractIndicator[Boolean](first.getBarSeries) {

  override def getValue(index: Int): Boolean = {
    Range(index, index - forLastBars, step = -1).forall { i =>
      first.getValue(i).isGreaterThan(second.getValue(i))
    }
  }
}
