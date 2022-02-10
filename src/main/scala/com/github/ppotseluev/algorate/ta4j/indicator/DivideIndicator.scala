package com.github.ppotseluev.algorate.ta4j.indicator

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.Num

class DivideIndicator(first: Indicator[Num], second: Indicator[Num])
    extends CachedIndicator[Num](first) {

  override def calculate(index: Int): Num =
    first.getValue(index).dividedBy(second.getValue(index))
}
