package com.github.ppotseluev.algorate.strategy

import cats.FlatMap
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.num.Num
import org.ta4j.core.rules.{AbstractRule, BooleanIndicatorRule}

import scala.util.Try

package object indicator {
  implicit val FlatMapIndicator: FlatMap[AbstractIndicator] = new FlatMap[AbstractIndicator] {
    override def flatMap[A, B](
        fa: AbstractIndicator[A]
    )(f: A => AbstractIndicator[B]): AbstractIndicator[B] =
      new AbstractIndicator[B](fa.getBarSeries) {
        override def getValue(index: Int): B =
          f(fa.getValue(index)).getValue(index)
      }

    override def tailRecM[A, B](a: A)(
        f: A => AbstractIndicator[Either[A, B]]
    ): AbstractIndicator[B] = ??? //TODO

    override def map[A, B](fa: AbstractIndicator[A])(f: A => B): AbstractIndicator[B] =
      new AbstractIndicator[B](fa.getBarSeries) {
        override def getValue(index: Int): B = f(fa.getValue(index))
      }
  }

  implicit class IndicatorSyntax[T](val indicator: AbstractIndicator[T]) extends AnyVal {
    def zipWithIndex: AbstractIndicator[(Int, T)] =
      new AbstractIndicator[(Int, T)](indicator.getBarSeries) {
        override def getValue(index: Int): (Int, T) =
          index -> indicator.getValue(index)
      }

    def shifted(shift: Int, defaultValue: T): AbstractIndicator[T] =
      new AbstractIndicator[T](indicator.getBarSeries) {
        override def getValue(index: Int): T =
          Try {
            indicator.getValue(index + shift)
          }.recover { case _: IndexOutOfBoundsException =>
            defaultValue
          }.get
      }

    def \+\(other: AbstractIndicator[Num])(implicit ev: T <:< Num): AbstractIndicator[Num] =
      for {
        x <- indicator.map(ev.apply)
        y <- other
      } yield y.plus(x)

    def \-\(other: AbstractIndicator[Num])(implicit ev: T <:< Num): AbstractIndicator[Num] =
      for {
        x <- indicator.map(ev.apply)
        y <- other
      } yield x.minus(y)

    def filter[A](predicate: A => Boolean)(implicit
        ev: T <:< Option[A]
    ): AbstractIndicator[Option[A]] =
      indicator.map(_.filter(predicate))

    def exists[A](predicate: A => Boolean)(implicit
        ev: T <:< Option[A]
    ): AbstractIndicator[Boolean] =
      indicator.map(_.exists(predicate))

    def asRule(implicit ev: T <:< Boolean): AbstractRule =
      new BooleanIndicatorRule(indicator.map(ev).map(boolean2Boolean))
  }
}
