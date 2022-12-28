package com.github.ppotseluev.algorate.strategy.indicator

import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.CachedIndicator

import LocalExtremumIndicator.Extremum

class FilteredExtremumIndicator(
    extremumIndicator: AbstractIndicator[Option[Extremum]],
    minIndexDelta: Int,
    minPercent: Double
) extends CachedIndicator[Option[Extremum]](extremumIndicator) {

  override def calculate(index: Int): Option[Extremum] =
    extremumIndicator.getValue(index).filter { extremum =>
      LazyList
        .from(index - 1, -1)
        .takeWhile(_ >= 0)
        .map(extremumIndicator.getValue)
        .collectFirst {
          case Some(extr) if extr.getClass == extremum.getClass && extr != extremum => extr
        }
        .forall { prevExtr =>
          math.abs(extremum.index - prevExtr.index) >= minIndexDelta ||
          math.abs(
            extremum.value.doubleValue - prevExtr.value.doubleValue
          ) / prevExtr.value.doubleValue >= minPercent
        }
    }
}
