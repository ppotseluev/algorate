package com.github.ppotseluev.algorate.ta4j.indicator

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.Num

class AbsIndicator(indicator: Indicator[Num]) extends CachedIndicator[Num](indicator) {

  override protected def calculate(index: Int): Num = {
    indicator.getValue(index).abs()
  }
}
