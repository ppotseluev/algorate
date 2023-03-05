package com.github.ppotseluev.algorate.cats

import cats.effect.{Sync, Temporal}
import cats.kernel.Monoid
import fs2.Stream

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class Provider[F[_]: Sync: Temporal, T](
    pull: F[T],
    initialValue: Option[T] = None,
    updateEvery: FiniteDuration = 30.seconds
) {
  @volatile private var value: Option[T] = initialValue

  val run: F[Unit] = Stream
    .eval(pull)
    .evalMap { value => Sync[F].delay { this.value = Some(value) } }
    .repeat
    .meteredStartImmediately(updateEvery)
    .compile
    .drain

  def get: Option[T] = value
  def getOrElse(default: => T): T = value.getOrElse(default)
  def getM(implicit m: Monoid[T]): T = getOrElse(m.empty)
}
