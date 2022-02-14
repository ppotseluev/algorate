package com.github.ppotseluev.algorate.ta4j.indicator

import org.ta4j.core.Indicator
import cats.syntax.functor._
import org.ta4j.core.indicators.{AbstractIndicator, CachedIndicator}
import org.ta4j.core.num.{NaN, Num}

object LocalExtremumIndicator {
  def apply(indicator: Indicator[Num], windowSize: Int): AbstractIndicator[Option[Extremum]] =
    new Impl(indicator, windowSize).shifted(windowSize / 2, None)

  def num(indicator: Indicator[Num], windowSize: Int): AbstractIndicator[Num] =
    apply(indicator, windowSize).map {
      case Some(extr) => extr.value
      case None       => NaN.NaN
    }

  private class Impl(indicator: Indicator[Num], windowSize: Int)
      extends CachedIndicator[Option[Extremum]](indicator) {

    override def calculate(index: Int): Option[Extremum] = {
      val ind = index - windowSize / 2
      val currentValue = indicator.getValue(ind)
      val values =
        Iterator.tabulate(windowSize) { i => indicator.getValue(index - windowSize + i) }.toSeq
      if (currentValue == values.min) {
        Some(Extremum.Min(currentValue))
      } else if (currentValue == values.max) {
        Some(Extremum.Max(currentValue))
      } else {
        None
      }
    }
  }

  sealed trait Extremum {
    def value: Num
  }

  object Extremum {
    case class Min(value: Num) extends Extremum
    case class Max(value: Num) extends Extremum
  }
}
