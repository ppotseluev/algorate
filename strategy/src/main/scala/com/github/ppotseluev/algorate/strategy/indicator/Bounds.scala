package com.github.ppotseluev.algorate.strategy.indicator

import cats.Functor
import cats.Monoid
import cats.derived.semiauto

case class Bounds[T](
    lower: T,
    upper: T
) {
  def both: List[T] = lower :: upper :: Nil
}

object Bounds {
  implicit val functor: Functor[Bounds] = semiauto.functor

  implicit def monoid[T: Monoid]: Monoid[Bounds[T]] = semiauto.monoid
}
