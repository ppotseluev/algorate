package com.github.ppotseluev.algorate.ta4j

import cats.Functor
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.AbstractIndicator

import scala.util.Try

package object indicator {
  implicit val IndicatorFunctor: Functor[AbstractIndicator] = new Functor[AbstractIndicator] {
    override def map[A, B](fa: AbstractIndicator[A])(f: A => B): AbstractIndicator[B] =
      new AbstractIndicator[B](fa.getBarSeries) {
        override def getValue(index: Int): B = f(fa.getValue(index))
      }
  }

  implicit class IndicatorSyntax[T](val indicator: Indicator[T]) extends AnyVal {
    def shifted(shift: Int, defaultValue: T): AbstractIndicator[T] =
      new AbstractIndicator[T](indicator.getBarSeries) {
        override def getValue(index: Int): T =
          Try {
            indicator.getValue(index + shift)
          }.recover { case _: IndexOutOfBoundsException =>
            defaultValue
          }.get
      }

  }
}
