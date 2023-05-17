package com.github.ppotseluev.algorate.strategy.indicator

import cats.Functor
import cats.Monoid
import cats.derived.semiauto

case class Bounds[T](
    lower: T,
    upper: T
) {
  def tuple = (lower, upper)
  def both: List[T] = lower :: upper :: Nil
  def foldU(f: (T, T) => T): T = f(upper, lower)
}

object Bounds {
  implicit val functor: Functor[Bounds] = semiauto.functor

  implicit def monoid[T: Monoid]: Monoid[Bounds[T]] = semiauto.monoid
}
