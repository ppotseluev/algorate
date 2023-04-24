package com.github.ppotseluev.algorate.strategy.indicator

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.Num

class PercentileIndicator(
    val baseIndicator: Indicator[Num],
    val percentile: Double,
    val barCount: Int
) extends CachedIndicator[Num](baseIndicator.getBarSeries) {
  assert(percentile >= 0.0 && percentile <= 100.0, "Percentile must be between 0 and 100")

  protected def calculate(index: Int): Num = {
    val startIndex = math.max(0, index - barCount + 1)
    val values = (startIndex to index).map(i => baseIndicator.getValue(i)).sorted

    val rank = (percentile / 100.0) * (values.length - 1)
    val lowerRank = math.floor(rank).toInt
    val upperRank = math.ceil(rank).toInt
    val weight = rank - lowerRank

    if (lowerRank == upperRank) {
      values(lowerRank)
    } else {
      val lowerValue = values(lowerRank)
      val upperValue = values(upperRank)
      lowerValue.plus(upperValue.minus(lowerValue).multipliedBy(numOf(weight)))
    }
  }
}
