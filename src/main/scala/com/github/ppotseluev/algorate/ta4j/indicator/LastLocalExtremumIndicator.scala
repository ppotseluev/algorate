package com.github.ppotseluev.algorate.ta4j.indicator

import org.ta4j.core.Indicator
import cats.syntax.functor._
import org.ta4j.core.indicators.{AbstractIndicator, CachedIndicator}
import org.ta4j.core.num.{NaN, Num}

object LastLocalExtremumIndicator { //TODO refactor. It's not last extremum now. ~ remove Option
  def apply(indicator: Indicator[Num], windowSize: Int): AbstractIndicator[Option[Extremum]] =
    new Impl(indicator, windowSize)

  def num(indicator: Indicator[Num], windowSize: Int): AbstractIndicator[Num] =
    apply(indicator, windowSize).map {
      case Some(extr) => extr.value
      case None       => NaN.NaN
    }

  private class Impl(indicator: Indicator[Num], windowSize: Int)
      extends CachedIndicator[Option[Extremum]](indicator) {

    override protected def calculate(index: Int): Option[Extremum] = {
      val ind = index - windowSize / 2
      val currentValue = indicator.getValue(ind)
      val values =
        Iterator
          .tabulate(windowSize) { i => indicator.getValue(index - windowSize + i + 1) }
          .toSeq
      if (currentValue == values.min) {
        Some(Extremum.Min(currentValue, ind))
      } else if (currentValue == values.max) {
        Some(Extremum.Max(currentValue, ind))
      } else {
        None
      }
    }
  }

  sealed trait Extremum {
    def value: Num

    def index: Int
  }

  object Extremum {
    case class Min(value: Num, index: Int) extends Extremum
    case class Max(value: Num, index: Int) extends Extremum
  }
}
